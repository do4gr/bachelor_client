#!/bin/sh

cd ~/bachelor_client/neo4j/setup
sudo docker build -t neo4j_graphql:latest  -f Dockerfile .

sudo docker run -d -h localhost -p 7474:7474  -p 7687:7687  --name neo4j-gqlbench  --env NEO4J_AUTH=none neo4j_graphql:latest