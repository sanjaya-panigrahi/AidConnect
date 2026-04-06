# Discovery Service

## Overview

The **Discovery Service** is a Netflix Eureka server that acts as the service registry
for all Chat Assist microservices. Each service registers itself on startup and
de-registers on shutdown, enabling the gateway to perform client-side load balancing
without hard-coded service URLs.

## Port

| Environment | Port |
|---|---|
| Default / Docker | `8761` |

## Key Responsibilities

- Maintain a registry of all running service instances (`user-service`, `chat-service`, `bot-service`, `aid-service`, `gateway-service`)
- Provide a web UI for inspecting registered services and their health
- Support client-side load balancing via Spring Cloud LoadBalancer

## Package Structure

```
com.chatassist.discovery
└── DiscoveryServiceApplication.java   # @EnableEurekaServer entry point
```

## Technology Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.x + Spring Cloud Netflix Eureka Server |
| Language | Java 21 |

## Local Development

```bash
mvn clean package -pl discovery-service -am -DskipTests
java -jar discovery-service/target/discovery-service-1.0.0.jar
```

## Eureka Dashboard

Available at `http://localhost:8761` when running locally.
All registered services and their lease health are visible here.
