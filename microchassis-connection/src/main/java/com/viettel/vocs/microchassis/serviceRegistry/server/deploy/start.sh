#!/bin/bash

mkdir -p logs

if [[ -z "${IS_LOAD_FILE_CONFIG}" ]]
then
	echo "Load from database"
elif [[ "${IS_LOAD_FILE_CONFIG}" == "true" ]]; then
	yum install -y unzip
	s3cmd -c /u01/.s3cfg sync -r s3://${CONFIG_BUCKET}/${NAMESPACE}/abm/ /u01/app/${CONFIG_DIR}/
	for dir in $(ls -d /u01/app/${CONFIG_DIR}/*/) ; do
		echo "$dir"
		for zip in $(ls $dir* | grep .zip); do
			echo "$zip"
			unzip $zip -d $dir
		done
	done
fi

#yum -y install iproute
#tc qdisc add dev eth0 root netem delay 40ms

cpu=`cat /sys/fs/cgroup/cpuset/cpuset.cpus`

java \
 -Xmx$HEAP \
 -cp "*:lib/*:lib-common/*"\
 --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
 -Dio.netty.tryReflectionSetAccessible=true \
 -Dio.netty.native.workdir="lib-common" \
 -Dcpus=$cpu \
 -Dchassis.common.maxPoolSize=100 \
 -Dmesh.server..type=fullmesh \
 -Dmano.enable=$MANO_ENABLE \
 -DmaxContentLength=150000000 \
 -DisLoadFileConfig=$IS_LOAD_FILE_CONFIG \
 -Dcache.file.url=$CONFIG_DIR/cache \
 -Dlog.input.enable=true \
 com.viettel.vocs.microchassis.serviceRegistry.LBR 2>&1 | tee logs/out.log

# -Dio.netty.leakdetection.level=paranoid \

