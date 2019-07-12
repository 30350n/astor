#!/bin/bash
. /vol/tmp/Astor/_create_env.sh
date "+%s%N astorStart" | cut -b1-13,20-30 > recording.txt
java -cp $PWD/target/astor-0.0.2-SNAPSHOT-jar-with-dependencies.jar fr.inria.main.evolution.AstorMain -validation fr.inria.astor.approaches.extensions.processbasedsorted.ProcessValidatorSorted -mode jkali -operatorspace relational-Logical-op -srcjavafolder /src -srctestfolder /src -binjavafolder /src -bintestfolder /src -location examples/sleep
date "+%s%N astorEnd" | cut -b1-13,20-28 >> recording.txt
