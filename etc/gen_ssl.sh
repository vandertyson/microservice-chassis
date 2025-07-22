openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -days 365
openssl pkcs8 -topk8 -in key.pem -inform pem -out key.pem -outform pem
