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
/bin/cp -rf $CONTAINER_PATH/ocs-like/etc/* $CONTAINER_PATH/deploy/

sudo docker build -t docker-registry:4000/tiennn18/microchassis-microservice:4.2 -f $CONTAINER_PATH/Dockerfile $TEST_PATH
sudo docker push docker-registry:4000/tiennn18/microchassis-microservice:4.2
kubectl config use-context $KUBE_CTX
cd $CONTAINER_PATH/ocs-like

deploy_layer(){
  local dp=$1
  local dpf=$2
  kubectl delete -f $dpf -n $KUBE_NS
  kubectl apply -f $dpf -n $KUBE_NS
  while [[ $(kubectl get deploy $dp -o=jsonpath={.status.readyReplicas} -n $KUBE_NS) != $(kubectl get deploy $dp -o=jsonpath={.status.updatedReplicas} -n $KUBE_NS) ]]; do sleep 1; done
}
kubectl delete -f back.yml -n $KUBE_NS
kubectl delete -f front.yml -n $KUBE_NS
kubectl delete -f inner1.yml -n $KUBE_NS
kubectl delete -f inner2.yml -n $KUBE_NS
kubectl delete -f inner3.yml -n $KUBE_NS
sleep 10
deploy_layer microchassis-db back.yml
deploy_layer microchassis-abm inner1.yml
deploy_layer microchassis-chp inner2.yml
sleep 5
deploy_layer microchassis-cgw inner3.yml
sleep 15
deploy_layer microchassis-lb front.yml


# a="$(netstat -nap | grep "$(ip -o -4 addr list eth0 | awk '{print $4}' | cut -d"/" -f1):13007")"; echo "$(echo "$a" | wc -l)"; echo "$a"
# check 1000log: for pod in $(kubectl get po -n tiennn18-mesh | grep microchassis-lb | cut -d" " -f1); do kubectl exec $pod  -c main -n tiennn18-mesh  -- cat /mesh/logs/all.log | grep SendCy | wc -l ; done