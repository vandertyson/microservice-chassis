fileName=pod-info.txt
TOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)
CURL_CA_BUNDLE=/var/run/secrets/kubernetes.io/serviceaccount/ca.crt
POD_NAME=$HOSTNAME
NAMESPACE=$(cat /var/run/secrets/kubernetes.io/serviceaccount/namespace)

# check status
unset tmp
while [ -z "$tmp" ]
do
	sleep 1s
	curl --cacert $CURL_CA_BUNDLE -H "Authorization: Bearer $TOKEN" https://kubernetes.default.svc:443/api/v1/namespaces/$NAMESPACE/pods/$POD_NAME > $fileName
	# curl --cacert $CURL_CA_BUNDLE -H "Authorization: Bearer $TOKEN" https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT/api/v1/namespaces/$NAMESPACE/pods/$POD_NAME > $fileName
	tmp=`grep -A5 "containerStatuses" $fileName | grep "running"`
done

# get CONTAINER_NAME
tmp=`grep -A5 "containerStatuses" $fileName | grep "name" | sed 's/"//g;s/,//g'`
# name: jaeger-operator
IFS=' '
read -a strarr <<< $tmp
CONTAINER_NAME=${strarr[1]}
# echo $CONTAINER_NAME

# get CONTAINER_ID
tmp=`grep "containerID" $fileName | sed 's/"//g;s/,//g;s/\///g;s/:/ /g'`
# containerID  docker e37f7e808cd7802a56d0b3f7f6b939c5e37a749cd5f391dee08c92845bd6e63c
IFS=' '
read -a strarr <<< $tmp
CONTAINER_ID=${strarr[2]}
# echo $CONTAINER_ID

# export to ENV
echo export POD_NAME=$POD_NAME >> $HOME/.bashrc
echo export NAMESPACE=$NAMESPACE >> $HOME/.bashrc
echo export CONTAINER_NAME=$CONTAINER_NAME >> $HOME/.bashrc
echo export CONTAINER_ID=$CONTAINER_ID >> $HOME/.bashrc

source $HOME/.bashrc
env | grep 'NAMESPACE\|POD_NAME\|CONTAINER_ID\|CONTAINER_NAME'
printf "_______________________ init-env: DONE _______________________ \n\n"