#!/bin/bash
echo "LOADING ENV"
source ./env.sh

mvn clean install -f $MCS
mvn clean install -f $MESH_PATH

#sudo docker build -t docker-registry:4000/tiennn18/abm:4.2 -f $ABM_PATH/Dockerfile $ABM_PATH && sudo docker push docker-registry:4000/tiennn18/abm:4.2 &

$MINION_PATH/deploy.sh

#/usr/lib/jvm/java-11-openjdk-amd64/bin/java -Dmaven.multiModuleProjectDirectory=/home/vht/Projecs/javawork/microservice-chassis/microchassis-demo -Dmaven.home=/home/vht/Apps/ideaC/plugins/maven/lib/maven3 -Dclassworlds.conf=/home/vht/Apps/ideaC/plugins/maven/lib/maven3/bin/m2.conf -Dmaven.ext.class.path=/home/vht/Apps/ideaC/plugins/maven/lib/maven-event-listener.jar -javaagent:/home/vht/Apps/ideaC/lib/idea_rt.jar=41443:/home/vht/Apps/ideaC/bin -Dfile.encoding=UTF-8 -classpath /home/vht/Apps/ideaC/plugins/maven/lib/maven3/boot/plexus-classworlds-2.6.0.jar:/home/vht/Apps/ideaC/plugins/maven/lib/maven3/boot/plexus-classworlds.license org.codehaus.classworlds.Launcher -Didea.version=2022.2.1 install -P vocs

# count connection abm-chp
# for((;;)); do date;  for pod in $(kubectl get po -n tiennn18-mesh | grep abm | cut -d " " -f1); do echo "$pod $(kubectl exec $pod -c abm1 -n tiennn18-mesh -- netstat -nap | grep 9001 | grep EST | wc -l)" ; done; sleep 10; done
# count connection chp
# for((;;)); do date;  for pod in $(kubectl get po -n tiennn18-mesh | grep chp | cut -d " " -f1); do echo "$pod $(kubectl exec $pod -c chp1 -n tiennn18-mesh -- netstat -nap | grep 9001 | grep EST | wc -l)" ; done; sleep 10; done
# coutn connection cgw-chp
# for((;;)); do date;  for pod in $(kubectl get po -n tiennn18-mesh | grep chp | cut -d " " -f1); do echo "$pod $(kubectl exec $pod -c chp1 -n tiennn18-mesh -- netstat -nap | grep 9003 | grep EST | wc -l)" ; done; sleep 10; done
# coutn connection Cgw
# for((;;)); do date;  for pod in $(kubectl get po -n tiennn18-mesh | grep cgw | cut -d " " -f1); do cgw_idx=$(echo $pod | cut -d "-" -f2); echo "$pod $(kubectl exec $pod -c cgw-$cgw_idx -n tiennn18-mesh -- netstat -nap | grep 9003 | grep EST | wc -l)" ; done; sleep 10; done