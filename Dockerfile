FROM openjdk:11-jdk
MAINTAINER Netgrif <devops@netgrif.com>

RUN mkdir -p /src/main/

ARG JAR_FILE=target/app-exec.jar
ARG RESOURCE=src/main/resources

COPY ${RESOURCE} src/main/resources
COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java","-jar","/app.jar"]
