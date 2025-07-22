FROM docker-registry:4000/vocs-base:v1
COPY bin /usr/app/bin
COPY src/main/resources /usr/app/src/main/resources
COPY target /usr/app/target
COPY microchassis-logging/target /usr/app/microchassis-logging/target
COPY microchassis-tracing/target /usr/app/microchassis-tracing/target
COPY microchassis-exception/target /usr/app/microchassis-exception/target
WORKDIR /usr/app