#KEY
PASSPRASE=your_passphrase
openssl genrsa -out echo-server.key 2048
openssl req -new -key echo-server.key -out echo-server.csr
openssl x509 -req -days 365 -in echo-server.csr -signkey echo-server.key -out echo-server.crt
# do not encrypt passout of pkcs8
openssl pkcs8 -topk8 -inform PEM -outform PEM -in echo-server.key -out echo-pkcs8.key -nocrypt

#KEY
PASSPRASE=your_passphrase
openssl genrsa -out fw1-server.key 2048
openssl req -new -key fw1-server.key -out fw1-server.csr
openssl x509 -req -days 365 -in fw1-server.csr -signkey fw1-server.key -out fw1-server.crt
# do not encrypt passout of pkcs8
openssl pkcs8 -topk8 -inform PEM -outform PEM -in fw1-server.key -out fw1-pkcs8.key -nocrypt


#JKS
PASSPRASE=your_passphrase
openssl genrsa -out echo-server.key 2048
openssl pkcs12 -export -in echo-server.crt -inkey echo-server.key -out echo-server.p12 -name echo-pkcs12 -password pass:$PASSPRASE
keytool -importkeystore -deststorepass $PASSPRASE -destkeypass $PASSPRASE -destkeystore echo-server.jks -srckeystore echo-server.p12 -srcstoretype PKCS12 -srcstorepass $PASSPRASE -alias echo-pkcs12

#JKS
PASSPRASE=your_passphrase
openssl genrsa -out fw1-server.key 2048
openssl pkcs12 -export -in fw1-server.crt -inkey fw1-server.key -out fw1-server.p12 -name fw1-pkcs12 -password pass:$PASSPRASE
keytool -importkeystore -deststorepass $PASSPRASE -destkeypass $PASSPRASE -destkeystore fw1-server.jks \
  -srckeystore fw1-server.p12 -srcstoretype PKCS12 -srcstorepass $PASSPRASE -alias fw1-pkcs12