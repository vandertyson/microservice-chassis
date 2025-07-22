openssl pkcs12 -export -out keystore.p12 -inkey key_nodes.pem -in cert_nodes.pem -name "certificate"
keytool -importkeystore -destkeystore keystore.jks -srcstoretype PKCS12 -srckeystore keystore.p12
