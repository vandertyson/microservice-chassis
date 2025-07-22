FROM docker-registry:4000/centos-nonroot:1.0.0

COPY --chown=ocs:ocs ./target/*.jar /u01/app/lib/
COPY --chown=ocs:ocs ./target/lib/* /u01/app/lib/
COPY --chown=ocs:ocs ./target/lib-common /u01/app/lib-common

WORKDIR /u01/app/bin