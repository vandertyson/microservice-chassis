#!/bin/bash

mkdir -p /mesh
nohup java -Xmx1G -jar -DNUMBER_OF_CPU=1 -DintervalStatistic=1000 -Dlog4j.configurationFile=/mesh/log4j2.xml \
 -DVDU_NAME=echo \
 -DconnectionPath=/mesh/connection-echo.yml \
 /mesh/microchassis-mesh-jar-with-dependencies.jar > /mesh/logs/out.log & # 2>&1 | tee /mesh/logs/out.log &