#!/bin/bash
mkdir -p /home/vht/Projecs/javawork/microservice-chassis/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/container/deploy/logs
/bin/cp -rf /home/vht/Projecs/javawork/microservice-chassis/microchassis-demo/target/microchassis-mesh-jar-with-dependencies.jar /home/vht/Projecs/javawork/microservice-chassis/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/container/deploy/
/bin/cp -rf /home/vht/Projecs/javawork/microservice-chassis/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/mesh-log4j2.xml /home/vht/Projecs/javawork/microservice-chassis/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/container/deploy/


cd /home/vht/Projecs/javawork/microservice-chassis/microchassis-demo/
sudo docker build -t docker-registry:4000/tiennn18-microchassis -f src/com/viettel/vocs/microchassis/tiennn18/container/Dockerfile .
sudo docker push docker-registry:4000/tiennn18-microchassis

ns=tiennn18
pat=01
cd /home/vht/Projecs/javawork/microservice-chassis/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/container/
dp=microchassis-back
dpf=back.yml
kubectl scale --replicas=0 deployment $dp -n $ns --context pat${pat}
kubectl apply -f $dpf -n $ns --context pat$pat
while [[ $(kubectl get deploy $dp -o=jsonpath={.status.readyReplicas} -n $ns) != $(kubectl get deploy $dp -o=jsonpath={.status.updatedReplicas} -n $ns) ]]; do sleep 1; done
sleep 1

dp=microchassis-front
dpf=front.yml
kubectl scale --replicas=0 deployment $dp -n $ns --context pat${pat}
kubectl apply -f $dpf -n $ns --context pat$pat
