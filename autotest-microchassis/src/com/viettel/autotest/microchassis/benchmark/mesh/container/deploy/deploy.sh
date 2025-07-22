#!/bin/bash
echo "LOADING ENV"
source ./env.sh

mkdir -p $MINION_PATH/deploy/logs
/bin/cp -rf $TARGET_PATH/microchassis-mesh-jar-with-dependencies.jar $MINION_PATH/deploy/
/bin/cp -rf $MINION_PATH/log4j2.xml $MINION_PATH/deploy/
/bin/cp -rf $MINION_PATH/start*.sh $MINION_PATH/deploy/
/bin/cp -rf $MINION_PATH/*.crt $MINION_PATH/deploy/
/bin/cp -rf $MINION_PATH/*.p12 $MINION_PATH/deploy/
/bin/cp -rf $MINION_PATH/*.jks $MINION_PATH/deploy/
/bin/cp -rf $MINION_PATH/*.key $MINION_PATH/deploy/
/bin/cp -rf $MINION_PATH/connection-*.yml $MINION_PATH/deploy/

sudo docker build -t docker-registry:4000/tiennn18/microchassis-microservice:4.0.goaway -f $MINION_PATH/Dockerfile $MESH_PATH
sudo docker push docker-registry:4000/tiennn18/microchassis-microservice:4.0.goaway
kubectl config use-context $KUBE_CTX
cd $MINION_PATH/

deploy_layer(){
  local dp=$1
  local dpf=$2
  kubectl delete -f $dpf -n $KUBE_NS
  kubectl apply -f $dpf -n $KUBE_NS
  while [[ $(kubectl get deploy $dp -o=jsonpath={.status.readyReplicas} -n $KUBE_NS) != $(kubectl get deploy $dp -o=jsonpath={.status.updatedReplicas} -n $KUBE_NS) ]]; do sleep 1; done
}
kubectl delete -f echo.yml -n $KUBE_NS
kubectl delete -f fw1.yml -n $KUBE_NS
deploy_layer microchassis-echo echo.yml
sleep 5
deploy_layer microchassis-fw1 fw1.yml # abm
#sleep 5
#deploy_layer microchassis-chp inner2.yml # chp
##sleep 5
#deploy_layer microchassis-chp inner3.yml # cgw


# check tcp_handler
#   for((;;)); do echo "$(date): $(pidstat -t -p 1| grep tcp_handler | wc -l) handler" ; sleep 10; done