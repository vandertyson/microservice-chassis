kubectl apply -f deploy/logging/fluent-bit/service-account.yaml
kubectl apply -f deploy/logging/fluent-bit/config-map.yaml
kubectl apply -f deploy/logging/fluent-bit/fluent-bit.yaml