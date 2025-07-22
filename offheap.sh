java -Xmx512M -Xms512M \
	-Dcount=100000 \
	-Ddirect=false \
	-Drelease=true \
	-Dsize=2048 \
	-cp ./microchassis-connection/target/*.jar com.viettel.vocs.microchassis.util.Utils

