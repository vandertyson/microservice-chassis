#!/bin/bash

MCS=/home/vht/Projecs/javawork/microservice-chassis
TEST_PATH=$MCS/microchassis-demo
TARGET_PATH=$TEST_PATH/target
BENCHMARK_PATH=$TEST_PATH/src/com/viettel/vocs/microchassis/tiennn18
CONTAINER_PATH=$BENCHMARK_PATH/container
KUBE_NS=tiennn18-mesh
KUBE_CTX=pat01

mkdir -p $CONTAINER_PATH/deploy/logs
/bin/cp -rf $TARGET_PATH/microchassis-mesh-jar-with-dependencies.jar $CONTAINER_PATH/deploy/
/bin/cp -rf $BENCHMARK_PATH/mesh-log4j2.xml $CONTAINER_PATH/deploy/
/bin/cp -rf $BENCHMARK_PATH/etc/* $CONTAINER_PATH/deploy/



sudo docker build -t docker-registry:4000/tiennn18/microchassis-microservice:4.2 -f $CONTAINER_PATH/Dockerfile $TEST_PATH
#sudo docker build -t docker-registry:4000/tiennn18-microchassis:latest
sudo docker push docker-registry:4000/tiennn18/microchassis-microservice:4.2
kubectl config use-context $KUBE_CTX
cd $CONTAINER_PATH/

deploy_layer(){
  local dp=$1
  local dpf=$2
  kubectl delete -f $dpf -n $KUBE_NS
  kubectl apply -f $dpf -n $KUBE_NS
  while [[ $(kubectl get deploy $dp -o=jsonpath={.status.readyReplicas} -n $KUBE_NS) != $(kubectl get deploy $dp -o=jsonpath={.status.updatedReplicas} -n $KUBE_NS) ]]; do sleep 1; done
}
kubectl delete -f back.yml -n $KUBE_NS
kubectl delete -f inner1.yml -n $KUBE_NS
kubectl delete -f inner2.yml -n $KUBE_NS
kubectl delete -f inner3.yml -n $KUBE_NS
kubectl delete -f front.yml -n $KUBE_NS
deploy_layer microchassis-db back.yml
sleep 5
deploy_layer microchassis-abm inner1.yml # abm
#sleep 5
#deploy_layer microchassis-chp inner2.yml # chp
##sleep 5
#deploy_layer microchassis-chp inner3.yml # cgw
sleep 15
deploy_layer microchassis-lb front.yml # ext


# check tcp_handler
#   for((;;)); do echo "$(date): $(pidstat -t -p 1| grep tcp_handler | wc -l) handler" ; sleep 10; done