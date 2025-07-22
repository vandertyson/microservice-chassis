#!/bin/bash
docker build -t microchassis:v1 .

docker tag microchassis:v1 docker-registry:4000/microchassis:v1
docker push docker-registry:4000/microchassis:v1