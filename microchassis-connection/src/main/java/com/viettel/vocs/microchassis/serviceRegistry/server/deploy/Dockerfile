FROM docker-registry:4000/centos-nonroot:1.0.0

COPY --chown=ocs:ocs ./target/dependencies/* /u01/app/lib/
COPY --chown=ocs:ocs ./target/*.jar /u01/app/lib/

COPY --chown=ocs:ocs ./src/main/java/com/viettel/vocs/microchassis/serviceRegistry/start.sh /u01/app/bin/start.sh
RUN chmod +x /u01/app/bin/start.sh

#COPY VERSION.md /u01/app/VERSION.md

WORKDIR /u01/app/bin

ENTRYPOINT ./start.sh