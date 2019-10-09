#!/bin/sh

sudo apt-get update -y

sudo apt-get dist-upgrade -y

sudo apt install docker.io -y

sudo systemctl start docker -y

sudo systemctl enable docker -y

apt  install docker-compose -y

# cAdvisor
sudo docker run \
  --volume=/:/rootfs:ro \
  --volume=/var/run:/var/run:ro \
  --volume=/sys:/sys:ro \
  --volume=/var/lib/docker/:/var/lib/docker:ro \
  --volume=/dev/disk/:/dev/disk:ro \
  --publish=8080:8080 \
  --detach=true \
  --name=cadvisor \
  google/cadvisor:latest
