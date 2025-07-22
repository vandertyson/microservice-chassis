/u01/jdk-11.0.8/bin/java -Xmx1700m -cp :../lib/* \
  -DclientMode=sync  \
  -DpayloadSize=4096  \
  -DfakeTPS=5000\
  -DcPort=13001 \
  -DcHost=microchassis-inner1 \
  -DcPort=13000 \
  -DcHost=microchassis-back\
  -DmaxAllowTPS=50000  \
  -DmaxTPS=30000  \
  -DmaxConcurrent=0  \
  -DmaxRequest=0 \
  -DserviceType=front  \
  -DintervalStatistic=1000\
  -Dlog4j.configurationFile=../etc/mesh-log4j2.xml \
   com.viettel.vocs.microchassis.tiennn18.FakeService