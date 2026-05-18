# patra-gateway-boot

## 概述

**patra-gateway-boot** 是 Patra 医学出版物数据平台的 API 网关服务,基于 Spring Cloud Gateway 构建。作为系统的统一入口,负责接收所有外部请求并将其路由到相应的后端微服务。

本网关提供服务发现、负载均衡、请求转发等核心功能,确保客户端可以通过单一入口访问整个 Patra 微服务生态系统。

## 核心职责

- **请求路由**: 根据请求路径将流量分发到正确的微服务(patra-registry、patra-ingest 等)
- **服务发现**: 通过 Nacos 自动发现和注册后端服务实例
- **负载均衡**: 使用 Spring Cloud LoadBalancer 在多个服务实例间分配请求
- **统一入口**: 为所有 Patra API 提供单一访问点,简化客户端配置
- **请求日志**: 集成分布式追踪,记录请求和响应以便问题诊断

## 模块结构

```
patra-gateway-boot/
├── src/main/java/com/patra/gateway/
│   └── PatraGatewayApplication.java    # Spring Boot 启动类
├── src/main/resources/
│   ├── application.yml                 # 主配置文件(路由、Nacos)
│   ├── application-dev.yml             # 开发环境配置(DEBUG 日志)
│   └── application-prod.yml            # 生产环境配置
└── build.gradle.kts                    # Gradle 依赖定义
```

## 主要组件

### PatraGatewayApplication
Spring Boot 应用启动类,使用 `@SpringBootApplication` 注解启动 Spring Cloud Gateway 服务。

### 路由配置
在 `application.yml` 中定义所有微服务路由规则:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            # Ingest 服务路由
            - id: patra-ingest
              uri: lb://patra-ingest
              predicates:
                - Path=/patra-ingest/**
              filters:
                - StripPrefix=1

            # Registry 服务路由
            - id: patra-registry
              uri: lb://patra-registry
              predicates:
                - Path=/patra-registry/**
              filters:
                - StripPrefix=1

            # Catalog 服务路由
            - id: patra-catalog
              uri: lb://patra-catalog
              predicates:
                - Path=/patra-catalog/**
              filters:
                - StripPrefix=1
```

**路由说明**:
- `id`: 路由唯一标识符
- `uri`: 目标服务地址,`lb://` 前缀表示使用负载均衡
- `predicates`: 匹配条件,这里按路径前缀匹配
- `filters`: 请求处理过滤器,`StripPrefix=1` 会移除路径的第一段

### Nacos 集成
通过 Spring Cloud Alibaba Nacos 实现服务发现:

```yaml
spring:
  cloud:
    nacos:
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        server-addr: ${NACOS_HOST:127.0.0.1}:${NACOS_PORT:8848}
        service: ${spring.application.name}
        fail-fast: true
```

## 路由示例

### Ingest 服务
```bash
# 客户端请求
GET http://gateway:9528/patra-ingest/plans

# 网关转发到
GET http://patra-ingest:8082/plans
```

### Registry 服务
```bash
# 客户端请求
GET http://gateway:9528/patra-registry/provenance/pubmed

# 网关转发到
GET http://patra-registry:8081/provenance/pubmed
```

### Catalog 服务
```bash
# 客户端请求
GET http://gateway:9528/patra-catalog/venues?page=0&size=20

# 网关转发到
GET http://patra-catalog:8083/venues?page=0&size=20
```

## API 文档聚合（Scalar UI）

网关通过 Scalar UI 聚合各微服务的 OpenAPI 文档,提供统一的 API 文档入口。

### 访问方式

打开浏览器访问 `http://gateway:9528/scalar.html` 即可查看所有服务的 API 文档。

### 配置

```yaml
scalar:
  sources:
    - url: /patra-catalog/v3/api-docs
      title: Catalog Service
      slug: catalog
    - url: /patra-registry/v3/api-docs
      title: Registry Service
      slug: registry
    - url: /patra-ingest/v3/api-docs
      title: Ingest Service
      slug: ingest
```

各服务的 `/v3/api-docs` 请求通过已有路由代理到下游服务,无需额外配置。每个后端服务需引入 `patra-spring-boot-starter-openapi` 以暴露 OpenAPI 文档端点。

## 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 激活的配置文件 | `dev` |
| `NACOS_HOST` | Nacos 服务器地址 | `localhost` |
| `NACOS_PORT` | Nacos 端口 | `8848` |
| `NACOS_USERNAME` | Nacos 认证用户名 | `nacos` |
| `NACOS_PASSWORD` | Nacos 认证密码 | `nacos` |

### 端口配置
- **默认端口**: 9528
- **Nacos 端口**: 8848

### 日志配置
开发环境下启用 DEBUG 级别日志以便调试路由和负载均衡:

```yaml
logging:
  level:
    org.springframework.cloud.gateway: DEBUG
    org.springframework.cloud.loadbalancer: DEBUG
```

### Actuator 监控配置

网关集成了 Spring Boot Actuator 提供健康检查和指标监控：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: ${spring.application.name}
```

**端点说明**：

| 端点 | URL | 说明 |
|------|-----|------|
| Health | `/actuator/health` | 健康检查（认证后显示详情） |
| Info | `/actuator/info` | 应用信息 |
| Metrics | `/actuator/metrics` | 性能指标 |

**Metrics 导出**：通过 OTel Agent + Micrometer Bridge 导出到 OTel Collector，无需额外配置。

## 技术栈

| 组件 | 版本/说明 |
|------|----------|
| **Spring Boot** | 4.0.6 |
| **Spring Cloud Gateway** | 2025.1.0 |
| **Spring Cloud LoadBalancer** | 用于客户端负载均衡 |
| **Nacos Discovery** | 服务发现和注册 |
| **patra-spring-boot-starter-core** | Patra 核心 starter |
| **Scalar UI** | API 文档聚合展示 |
| **SpringDoc OpenAPI (WebFlux)** | OpenAPI 文档生成（WebFlux 版） |

---

**最后更新**: 2026-02-15
**版本**: 0.1.0-SNAPSHOT
