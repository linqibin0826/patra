# patra-egress-gateway 核心概览

## 项目定位
patra-egress-gateway（南向网关）是 Papertrace 项目中负责**统一管理所有出站外部服务调用**的微服务。

## 核心职责
1. **透传外部服务调用**：接收业务方的请求参数和认证信息，原样透传给外部服务
2. **弹性能力提供**：提供限流、重试、熔断、超时等通用弹性能力
3. **响应语义统一**：将外部服务的响应封装为统一的语义结构
4. **配置管理**：管理系统级弹性配置，支持业务方覆盖（不超过最大值）
5. **可观测性**：记录每次外部调用的详细日志和指标

## 非职责（严格边界）
- ❌ 不进行业务数据转换和处理
- ❌ 不包含业务规则判断
- ❌ 不持久化业务数据
- ❌ 不解析外部服务的业务数据内容

## 架构设计
- **架构模式**：六边形架构 + DDD
- **模块结构**：api / domain / app / infra / adapter / boot
- **依赖方向**：adapter → app → domain ← infra

## 支持的外部服务类型
- 医学文献数据源（PubMed/PMC/Crossref等）
- 对象存储服务（OSS/MinIO/S3等）
- 邮件服务
- 短信/验证码服务

## 技术栈
- Java 21 + Spring Boot 3.2.4 + Spring Cloud 2023.0.1
- Resilience4j（限流、重试、熔断）
- Spring RestClient（HTTP 客户端）
- Micrometer（指标收集）
- Lombok、MapStruct、Hutool
