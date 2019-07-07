. /vol/tmp/Astor/_create_env.sh
java  -cp $PWD/target/astor-0.0.2-SNAPSHOT-jar-with-dependencies.jar fr.inria.main.evolution.AstorMain -validation fr.inria.astor.approaches.extensions.processbasedsorted.ProcessValidatorSorted -testbystep true -location examples/Math-issue-280 -srcjavafolder /src/java/ -srctestfolder /src/test/  -binjavafolder /target/classes/ -bintestfolder  /target/test-classes/
