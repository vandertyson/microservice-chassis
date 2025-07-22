#!/bin/bash

MCS=/home/vht/Projecs/javawork/microservice-chassis
TEST_PATH=$MCS/microchassis-demo
TARGET_PATH=$TEST_PATH/target
BENCHMARK_PATH=$TEST_PATH/src/com/viettel/vocs/microchassis/tiennn18
CONTAINER_PATH=$BENCHMARK_PATH/container
KUBE_NS=tiennn18-mesh
KUBE_CTX=pat01
# build
mvn clean install -f $MCS
mvn clean install -f $TEST_PATH
cd $TEST_PATH
#/usr/lib/jvm/java-11-openjdk-amd64/bin/java -Dmaven.multiModuleProjectDirectory=/home/vht/Projecs/javawork/microservice-chassis/microchassis-demo -Dmaven.home=/home/vht/Apps/ideaC/plugins/maven/lib/maven3 -Dclassworlds.conf=/home/vht/Apps/ideaC/plugins/maven/lib/maven3/bin/m2.conf -Dmaven.ext.class.path=/home/vht/Apps/ideaC/plugins/maven/lib/maven-event-listener.jar -javaagent:/home/vht/Apps/ideaC/lib/idea_rt.jar=41443:/home/vht/Apps/ideaC/bin -Dfile.encoding=UTF-8 -classpath /home/vht/Apps/ideaC/plugins/maven/lib/maven3/boot/plexus-classworlds-2.6.0.jar:/home/vht/Apps/ideaC/plugins/maven/lib/maven3/boot/plexus-classworlds.license org.codehaus.classworlds.Launcher -Didea.version=2022.2.1 install -P vocs


/home/vht/Projecs/javawork/microservice-chassis/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/container/deploy.sh
