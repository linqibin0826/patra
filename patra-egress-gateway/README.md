# patra-egress-gateway

## 概述

patra-egress-gateway（南向网关）是 Papertrace 项目中负责统一管理所有出站外部服务调用的微服务。它为上游业务方提供标准化的外部服务访问能力，包括医学文献数据源（PubMed/PMC/Crossref等）、对象存储服务（OSS/MinIO/S3等）、邮件服务、短信/验证码服务等。

## 核心职责

1. **透传外部服务调用**：接收业务方的请求参数和认证信息，原样透传给外部服务
2. **弹性能力提供**：提供限流、重试、熔断、超时等通用弹性能力
3. **响应语义统一**：将外部服务的响应封装为统一的语义结构
4. **配置管理**：管理系统级弹性配置，支持业务方覆盖（不超过最大值）
5. **可观测性**：记录每次外部调用的详细日志和指标

## 非职责

- 不进行业务数据转换和处理
- 不包含业务规则判断
- 不持久化业务数据
- 不解析外部服务的业务数据内容

## 模块结构

```
patra-egress-gateway/
├── patra-egress-gateway-api/          # 错误码、外部 DTOs
├── patra-egress-gateway-adapter/      # Inbound adapters (REST)
├── patra-egress-gateway-app/          # Use case orchestration
├── patra-egress-gateway-domain/       # Aggregates, entities, domain ports
├── patra-egress-gateway-infra/        # Outbound implementations
└── patra-egress-gateway-boot/         # Spring Boot application
```

## 技术栈

- **Java**: 21
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.1
- **Resilience4j**: 弹性能力实现（限流、重试、熔断）
- **Spring RestClient**: HTTP 客户端
- **Micrometer**: 指标收集
- **Lombok**: 代码生成
- **Hutool**: 工具类库

## 快速开始

### 编译

```bash
# 编译模块
mvn -q -DskipTests compile

# 打包
mvn clean package -DskipTests
```

### 运行

```bash
# 运行 Boot 模块
mvn -pl patra-egress-gateway/patra-egress-gateway-boot spring-boot:run
```

### 配置

主要配置项在 `application.yaml` 中：

```yaml
patra:
  egress:
    resilience:
      max:
        timeout: 60s
        maxRetries: 5
        rateLimit: 1000
      default:
        timeout: 30s
        maxRetries: 3
        rateLimit: 100
```

## 使用示例

### 调用 PubMed API

```java
ExternalCallRequest request = ExternalCallRequest.builder()
    .url("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi")
    .method("GET")
    .headers(Map.of("User-Agent", "Papertrace/0.1.0"))
    .resilienceConfig(ResilienceConfigDTO.builder()
        .timeout(10)
        .maxRetries(3)
        .rateLimit(10)
        .build())
    .build();

ExternalCallResponse response = egressClient.call(request);
```

### 调用 OSS API

```java
ExternalCallRequest request = ExternalCallRequest.builder()
    .url("https://bucket.oss-cn-hangzhou.aliyuncs.com/file.pdf")
    .method("PUT")
    .headers(Map.of(
        "Authorization", "OSS " + accessKeyId + ":" + signature,
        "Content-Type", "application/pdf"
    ))
    .body(fileContent)
    .resilienceConfig(ResilienceConfigDTO.builder()
        .timeout(60)
        .maxRetries(5)
        .build())
    .build();

ExternalCallResponse response = egressClient.call(request);
```

## 详细文档

更多详细信息请参考：

- [需求文档](.kiro/specs/patra-egress-gateway/requirements.md)
- [设计文档](.kiro/specs/patra-egress-gateway/design.md)
- [任务列表](.kiro/specs/patra-egress-gateway/tasks.md)

## 版本

当前版本：0.1.0-SNAPSHOT

## 作者

@linqibin

## 许可

Copyright © 2025 Papertrace
