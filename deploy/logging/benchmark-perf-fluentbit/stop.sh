ROOT_LOG=/u01/data/log-storage
xargs kill < pids.txt
rm -rf $ROOT_LOG/*
rm -rf /var/log/flb_kube.db