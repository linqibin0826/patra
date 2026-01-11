# 项目信息

## 概览

**Patra** — 医学出版物数据平台 (v0.1.0-SNAPSHOT)，采集、解析、存储来自 PubMed/EPMC/Crossref 等 10+ 外部数据源的文献和期刊数据。

**架构**: 微服务 + 六边形架构 + DDD + 事件驱动
**技术栈**: Java 25 | Spring Boot 4.0.1 | Spring Data JPA | MySQL 8.x | Consul

## 核心服务

- `patra-registry` - SSOT 注册中心 (Provenance 配置、Expression 元数据、字典管理)
- `patra-ingest` - 数据采集服务 (Plan → Task → 外部 API 调用)
- `patra-catalog` - 目录服务 (文献、期刊数据索引)
- `patra-gateway-boot` - API 网关 (路由、认证、限流)

## 模块结构

**微服务模块** (六边形架构):
```
patra-{service}/
├── patra-{service}-domain   # 纯 Java 领域模型
├── patra-{service}-app      # 编排层 (@Transactional)
├── patra-{service}-infra    # 基础设施 (Repository 实现、Feign 客户端)
├── patra-{service}-adapter  # 适配器 (Controller、Job)
├── patra-{service}-api      # 服务契约 (DTO、接口定义)
└── patra-{service}-boot     # 启动入口 (@SpringBootApplication)
```

**通用模块**:
- `patra-common-core` - DDD 基类、异常体系、共享枚举、工具类
- `patra-common-model` - Shared Kernel 数据模型
- `patra-common-storage` - 对象存储键生成模板

**Starter 模块**:
- `patra-spring-boot-starter-core/web/jpa/batch/rest-client/object-storage/observability/redisson/provenance/test`
- `patra-spring-cloud-starter-feign`
