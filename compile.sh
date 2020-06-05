mkdir classes
javac -cp "$(clj -Spath)" --source-path src/main/java src/main/java/functions/Main.java -d classes
clj -Aaot
clj -Adepstar
cp target/service.jar .