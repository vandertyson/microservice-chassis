#!/bin/bash
MCS=/home/vht/Projecs/javawork/microservice-chassis
LBR_PATH=$MCS/microchassis-connection/src/main/java/com/viettel/vocs/microchassis/serviceRegistry
CON_PATH=$MCS/microchassis-connection
CHP_PATH=/home/vht/Projecs/javawork/vocs/chp-gy
ABM_PATH=/home/vht/Projecs/javawork/vocs/abm
CGW_PATH=/home/vht/Projecs/javawork/vocs/diameter-soap-mml-cgw

mvn clean install -f $MCS
mvn clean install -f $ABM_PATH &
mvn clean install -f $CHP_PATH &
mvn clean install -f $CGW_PATH &
wait
sudo docker build -t docker-registry:4000/tiennn18/lbr:4.2 -f $LBR_PATH/Dockerfile $CON_PATH && sudo docker push docker-registry:4000/tiennn18/lbr:4.2 &
sudo docker build -t docker-registry:4000/tiennn18/abm:4.2 -f $ABM_PATH/Dockerfile $ABM_PATH && sudo docker push docker-registry:4000/tiennn18/abm:4.2 &
sudo docker build -t docker-registry:4000/tiennn18/chp-gy:4.2 -f $CHP_PATH/Dockerfile $CHP_PATH && sudo docker push docker-registry:4000/tiennn18/chp-gy:4.2 &
sudo docker build -t docker-registry:4000/tiennn18/cgw:4.2 -f $CGW_PATH/Dockerfile $CGW_PATH && sudo docker push docker-registry:4000/tiennn18/cgw:4.2 &
wait

~/Projecs/helmwork/autobench/controller/deploy.sh /home/vht/Projecs/helmwork/autobench/data4g/wcgw/chassis/pat01


# count connection abm-chp
# for((;;)); do date;  for pod in $(kubectl get po -n tiennn18-mesh | grep abm | cut -d " " -f1); do echo "$pod $(kubectl exec $pod -c abm1 -n tiennn18-mesh -- netstat -nap | grep 9001 | grep EST | wc -l)" ; done; sleep 10; done
# count connection chp
# for((;;)); do date;  for pod in $(kubectl get po -n tiennn18-mesh | grep chp | cut -d " " -f1); do echo "$pod $(kubectl exec $pod -c chp1 -n tiennn18-mesh -- netstat -nap | grep 9001 | grep EST | wc -l)" ; done; sleep 10; done
# coutn connection cgw-chp
# for((;;)); do date;  for pod in $(kubectl get po -n tiennn18-mesh | grep chp | cut -d " " -f1); do echo "$pod $(kubectl exec $pod -c chp1 -n tiennn18-mesh -- netstat -nap | grep 9003 | grep EST | wc -l)" ; done; sleep 10; done
# coutn connection Cgw
# for((;;)); do date;  for pod in $(kubectl get po -n tiennn18-mesh | grep cgw | cut -d " " -f1); do cgw_idx=$(echo $pod | cut -d "-" -f2); echo "$pod $(kubectl exec $pod -c cgw-$cgw_idx -n tiennn18-mesh -- netstat -nap | grep 9003 | grep EST | wc -l)" ; done; sleep 10; done