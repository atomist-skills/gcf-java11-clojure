# GCF: java11 runtime for clojure

My primary goal here was to create an aot-compiled uberjar to experiment with startup times for the new java11 runtime.
A secondary goal was to see whether I could `(genclass ...)` the `com.google.cloud.functions.HttpFunction` needed by
the [functions framework][functions-framework-api].

The creation of an aot-compiled uberjar worked well.  I was hoping that this project wouldn't contain any `.java` files
but as everyone has probably anticipated, there were some class loader problems ...

We did not need a `pom.xml`, or gradle file or anything like that.

## Creating the Uberjar

Here's a script to create `target/service.jar`.  It's basically:

* compile one `.java` file
* aot all of the `.clj files
* create an uberjar with [depstar][depstar] (I think it might be possible to get depstar to do the aot step actually)

```shell script
mkdir classes
javac -cp "$(clj -Spath)" --source-path src/main/java src/main/java/functions/Main.java -d classes
clj -Aaot
clj -Adepstar
```

## Testing the Uberjar

Google has a local harness you can use at `{com.google.cloud.functions.invoker/java-function-invoker {:mvn/version "1.0.0-alpha-2-rc5"}}`.
Running this new uberjar in a repl, will give you a function invoker on port `8080`:

```
16:58 $ clj -A:java-function-invoker
Clojure 1.10.1
user=> (import '[com.google.cloud.functions.invoker.runner Invoker])
com.google.cloud.functions.invoker.runner.Invoker
user=> (Invoker/main (into-array ["--classpath" "target/service.jar" "--target" "functions.Main"]))
2020-06-01 17:17:08.280:INFO:oejs.Server:main: jetty-9.4.26.v20200117; built: 2020-01-17T12:35:33.676Z; git: 7b38981d25d14afb4a12ff1f2596756144edf695; jvm 11.0.7+8-LTS
2020-06-01 17:17:08.308:INFO:oejsh.ContextHandler:main: Started o.e.j.s.ServletContextHandler@108d55c4{/,null,AVAILABLE}
2020-06-01 17:17:08.356:INFO:oejs.AbstractConnector:main: Started ServerConnector@35fb22a9{HTTP/1.1,[http/1.1]}{0.0.0.0:8080}
2020-06-01 17:17:08.357:INFO:oejs.Server:main: Started @916377ms
Jun 01, 2020 5:17:08 PM com.google.cloud.functions.invoker.runner.Invoker logServerInfo
INFO: Serving function...
Jun 01, 2020 5:17:08 PM com.google.cloud.functions.invoker.runner.Invoker logServerInfo
INFO: Function: functions.Main
Jun 01, 2020 5:17:08 PM com.google.cloud.functions.invoker.runner.Invoker logServerInfo
INFO: URL: http://localhost:8080/
```

Now that this is running, you can test this locally with `curl htp://localhost:8080`.

## Deploying the Uberjar

Deploy this to google using `glcoud`.  For a function named `clojure-function`, 
the command line should be:

```shell script
gcloud functions deploy \ 
  clojure-function \
  --entry-point functions.Main \
  --runtime java11 \
  --trigger-http \
  --source ./target \
  --memory 512MB
```

The most surprising thing here is the `--source ./target` parameter.  This directory has a single 
jar (`./target/service.jar`), which gets uploaded as part of the function archive.  
Somehow, the google function runtime knows 
how to construct a classpath with this jar.  I didn't see any documentation on the actual contract here (most of 
the documentation focuses on setting up a `pom.xml` and deploying .java src files directly).  Incidentally, it's also 
straight forward to leave the `.clj` files uncompiled in the archive.  That certainly works and is 
a lot easier.  However, the purpose of this was to experiment with the `aot` route.  I don't want any `clj` compilation
in the timings.

You should see output like:

```shell script
Deploying function (may take a while - up to 2 minutes)...done.
availableMemoryMb: 256
entryPoint: functions.Main
httpsTrigger:
  url: https://us-central1-xxxxxxxxx.cloudfunctions.net/clojure-function
...
```

You can curl that url and use stack driver to see the logs.

## `(:gen-class ...)`??

Right?  Seems straight forward.  The functions framework should be able to reference a `Class` 
that is generated from `.clj` source.  I ran 
into a ClassLoader issue when trying this.  I'm wondering whether the context ClassLoader 
that loads `com.google.cloud.functions.HttpFunction`, is not what I was expected.  I tried to
create an entrypoint using this `clj` code:

```clojure
(ns atomist.Main
  (:require [atomist.skill :as skill])
  (:import [com.google.cloud.functions HttpFunction])
  (:gen-class
   :implements [com.google.cloud.functions.HttpFunction]
   :prefix "gcf-"
   :main false))

(defn gcf-service [_ request response]
  (println request)
  (skill/-main []))
```

When I try to run the function, I see the `Exception` below.  Apparently, the context 
ClassLoader being used by `internPrivate` can no longer see `clojure.core`?

```
Exception in thread "main" java.lang.ExceptionInInitializerError
        at clojure.lang.Namespace.<init>(Namespace.java:34)
        at clojure.lang.Namespace.findOrCreate(Namespace.java:176)
        at clojure.lang.Var.internPrivate(Var.java:156)
        at atomist.Main.<clinit>(Unknown Source)
        at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
        at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)
        at java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
        at java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:490)
        at com.google.cloud.functions.invoker.NewHttpFunctionExecutor.forClass(NewHttpFunctionExecutor.java:51)
        at com.google.cloud.functions.invoker.runner.Invoker.servletForDeducedSignatureType(Invoker.java:358)
        at com.google.cloud.functions.invoker.runner.Invoker.startServer(Invoker.java:290)
        at com.google.cloud.functions.invoker.runner.Invoker.main(Invoker.java:140)
Caused by: java.io.FileNotFoundException: Could not locate clojure/core__init.class, clojure/core.clj or clojure/core.cljc on classpath.
        at clojure.lang.RT.load(RT.java:462)
        at clojure.lang.RT.load(RT.java:424)
        at clojure.lang.RT.<clinit>(RT.java:338)
        ... 12 more

```

It would be cool to have no `.java` files in this repo.  My first look at [the code for the invoker][github-functions-framework-java] also
made me think that the generated class should have worked so I need to look a little deeper.

[pack.alpha]: https://github.com/juxt/pack.alpha
[first.java]: https://cloud.google.com/functions/docs/first-java
[depstar]: https://github.com/seancorfield/depstar
[functions-framework-api]: https://javadoc.io/doc/com.google.cloud.functions/functions-framework-api/latest/index.html
[api]: https://mvnrepository.com/artifact/com.google.cloud.functions/functions-framework-api/1.0.1
[java-11-for-cloud-functions]: https://developers.googleblog.com/2020/05/java-11-for-cloud-functions.html
[github-functions-framework-java]: https://github.com/GoogleCloudPlatform/functions-framework-java