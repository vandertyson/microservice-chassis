ROOT_LOG=/u01/data/log-storage
PREFIX=service-d-v1-64bf88bc5-s65d9_microservices-chassis_service-d_cafaea0ece337b4cd42e585f48f352e913a22d280511064ed901ceb65ab21e3c

#touch pids.txt
#xargs kill < pids.txt
rm -rf pids.txt
rm -rf $ROOT_LOG/*
touch pids.txt
for ((i = 0; i < $1-1; i++));
do
    echo $i
    ./bin/flb-tail-writer -d /home/vinhcx2/logs/concat.log -o $ROOT_LOG/$PREFIX\_$i.log -r $2 -s $3&
    PID=$!
    echo $PID
    echo $PID >> pids.txt
done;
#./bin/flb-tail-writer -d /home/vinhcx2/logs/concat.log -o /home/vinhcx2/logs/scp-out/$i.log -r $2 -s $3 -p `pgrep -f "/fluent"`
./bin/flb-tail-writer -d /home/vinhcx2/logs/concat.log -o $ROOT_LOG/$PREFIX\_$i.log -r $2 -s $3 -p `pgrep -f "/fluent"`
#./bin/flb-tail-writer -d /home/vinhcx2/logs/concat.log -o /home/vinhcx2/logs/scp-out/$i.log -r $2 -s $3 -p `pgrep -f "filebeat"`
#./bin/flb-tail-writer -d /home/vinhcx2/logs/concat.log -o /home/vinhcx2/logs/scp-out/$i.log -r $2 -s $3 -p `pgrep -f "/opt/td-agent-bit/bin/td-agent-bit"`