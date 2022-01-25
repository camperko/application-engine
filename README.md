# Netgrif Application Engine

[![GitHub](https://img.shields.io/github/license/netgrif/application-engine)](https://netgrif.com/engine/license)
[![Java](https://img.shields.io/badge/Java-11-red)](https://openjdk.java.net/projects/jdk/11/)
[![Petriflow 1.0.1](https://img.shields.io/badge/Petriflow-1.0.1-0aa8ff)](https://petriflow.com)
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/netgrif/application-engine?sort=semver&display_name=tag)](https://github.com/netgrif/application-engine/releases)
[![build](https://github.com/netgrif/application-engine/actions/workflows/master-build.yml/badge.svg)](https://github.com/netgrif/application-engine/actions/workflows/release-build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=netgrif_application-engine&metric=alert_status)](https://sonarcloud.io/dashboard?id=netgrif_application-engine)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=netgrif_application-engine&metric=coverage)](https://sonarcloud.io/dashboard?id=netgrif_application-engine)
[![Known Vulnerabilities](https://snyk.io/test/github/netgrif/application-engine/badge.svg)](https://snyk.io/test/github/netgrif/application-engine)

> Next-generation end-to-end low code platform.

Application Engine is a workflow management system fully supporting low-code language Petriflow. Application Engine (NAE for short)
is based on Spring framework with fully complaint Petriflow language interpreter. NAE runs inside the Java Virtual Machine.
It can be embedded into Java 11 project or used as a standalone process server. On top of the process server, NAE provides
additional components to make integration to your project/environment seamless.

* Petriflow low-code language: [http://petriflow.com](https://petriflow.com)
* Documentation: [https://engine.netgrif.com](https://engine.netgrif.com)
* Getting Started: [https://engine.netgrif.com/get_started](https://engine.netgrif.com/get_started)
* Issue Tracker: [Github issues](https://github.com/netgrif/application-engine/issues)
* Java docs: [https://engine.netgrif.com/javadoc](https://engine.netgrif.com/javadoc)
* Roadmap: [https://engine.netgrif.com/roadmap](https://engine.netgrif.com/roadmap)
* License: [NETGRIF Community License](https://github.com/netgrif/application-engine/blob/master/LICENSE)

## Components

// TODO linky do dokumentácie
Netgrif Application Engine (or NAE for short) consists of several key components:
 * **Workflow engine**
   * **Process executions** - Process instance and task management
   * **Actions and Events processing** - Compiling and running action's code, handling events in processes
   * **Roles management and permissions resolution** - Permissions and restrictions resolving for processes
   * **Search and filters** - Indexing, querying and filter management.
 * **Authentication and authorization** - User management and application-wide permissions
   * **LDAP** - Integration to authentication solution via LDAP protocol.
   * **Organization structures** - Managing organization structure for application users
 * **Business rules engine** - Rules execution across whole application based on [Drools](https://drools.org/)
 * **Logging and auditing** - Logging to text file and Event/Audit log generation to the main database 
 * **Mail service** - Mail client for sending and receiving emails
 * **Extension services**
   * **PDF generator** - Generate PDF from process form / task
   * **QR code generator** - Generate QR code from process data

## Requirements

The Application engine has some requirements for runtime environment. The following table is summary of requirements 
to run and use the engine:

| Name                                                   | Version | Description                                                     | Recommendation                                                         |
|--------------------------------------------------------|---------|-----------------------------------------------------------------|------------------------------------------------------------------------|
| [Java](https://openjdk.java.net/)                      | 11+     | Java Development Kit                                            | [OpenJDK 11](https://openjdk.java.net/install/)                        |
| [Redis](https://redis.io/)                             | 5+      | Key-value in-memory database used for user sessions and caching | [Redis 6.2.6](https://redis.io/download)                               |
| [MongoDB](https://www.mongodb.com/)                    | 4.4+    | Main document store database                                    | [MongoDB 4.4.11](https://docs.mongodb.com/v4.4/installation/)          |
| [Elasticsearch](https://www.elastic.co/elasticsearch/) | 7.10+   | Index database used for better application search               | [Elasticsearch 7.10.2](https://www.elastic.co/downloads/elasticsearch) |

If you are planning on developing docker container based solution you can use our [docker-compose](docker-compose.yml) configuration to run all
necessary databases to develop with NAE.

If you are going to deploy your application on Kubernetes cluster please check out documentation for [Kubernetes deployment](https://engine.netgrif.com/devops/kubernetes).

## Installation

### Running as standalone

// TODO stručný popis ako rozbehať rovno z jarka engine a otestovať si deployment. odkázať sa na podrobnejší popis v docs

### Embedding

// TODO stručný popis ako to zahrnúť v spring projekte cez maven. odkázať sa na podrobnejší popis v docs

For more information please follow instructions in [Get Started](https://engine.netgrif.com/get_started)

## Other projects

### Frontend library

For complete Netgrif Application Engine experience check out our [Angular library](https://github.com/netgrif/components) 
for building frontend applications in Application Engine platform powered by Petriflow processes.

### Application Builder

For creating processes in Petriflow language try our free Application Builder on [https://builder.netgrif.com](https://builder.netgrif.com).
You can start from scratch or import existing process in BPMN 2.0 and builder automatically converts it into Petriflow.

### NCLI (Coming soon)

If you need help with setting up project or looking for tool to automate your developer work with NAE based applications,
take a look on [NCLI (Netgrif Command Line Interface)](https://github.com/netgrif/ncli).

## Reporting issues

If you find a bug, let us know at [Issue page](https://github.com/netgrif/application-engine/issues). First, please read our [Contribution guide](https://github.com/netgrif/application-engine/blob/master/CONTRIBUTING.md)

## License

The software is licensed under NETGRIF Community license. You may be found this license at [the LICENSE file](https://github.com/netgrif/application-engine/blob/master/LICENSE) in the repository. 