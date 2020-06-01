package functions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import clojure.java.api.Clojure;
import clojure.lang.IFn;

public class Main implements HttpFunction {
    // Simple function to return "Hello World"
    @Override
    public void service(HttpRequest request, HttpResponse response)
            throws IOException {
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("atomist.skill"));
        IFn main = Clojure.var("atomist.skill","-main");
        BufferedWriter writer = response.getWriter();
        writer.write((String)main.invoke(main, Clojure.read("[]")));
    }
}