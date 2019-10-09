# https://docs.docker.com/engine/reference/builder/

FROM neo4j:latest

COPY neo4j-graphql-3.5.0.3.jar /var/lib/neo4j/plugins

RUN echo 'dbms.unmanaged_extension_classes=org.neo4j.graphql=/graphql' | tee -a /var/lib/neo4j/conf/neo4j.conf

CMD ["neo4j"]