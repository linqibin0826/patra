# 项目信息

## 概览

**Patra** — 医学出版物数据平台 (v0.1.0-SNAPSHOT)，采集、解析、存储来自 PubMed/EPMC/Crossref 等 10+ 外部数据源的文献和期刊数据。

**架构**: 微服务 + 六边形架构 + DDD + 事件驱动
**技术栈**: Java 25 | Spring Boot 3.5.7 | MyBatis-Plus | MySQL 8.x | Nacos

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
- `patra-common-provenance-api` - Provenance API 定义

**Starter 模块**:
- `patra-spring-boot-starter-core` - 核心基础设施 (JSON/时间/观测性/错误处理)
- `patra-spring-boot-starter-web` - Web 层 (全局异常、参数验证、统一响应)
- `patra-spring-boot-starter-mybatis` - 数据访问 (MyBatis-Plus + Flyway)
- `patra-spring-boot-starter-batch` - 批处理 (Spring Batch + 断点续传)
- `patra-spring-boot-starter-rest-client` - RestClient 统一配置 (超时/重试/追踪)
- `patra-spring-boot-starter-object-storage` - 对象存储 (MinIO/S3 抽象)
- `patra-spring-boot-starter-observability` - 可观测性 (Metrics/Tracing/Logging)
- `patra-spring-boot-starter-redisson` - 分布式锁/Redis (Redisson + @DistributedLock)
- `patra-spring-boot-starter-provenance` - PubMed/EPMC HTTP 客户端
- `patra-spring-cloud-starter-feign` - Feign 客户端自动配置

## 核心概念

- **Provenance** - 外部数据源及其操作配置
- **Plan** - 数据采集蓝图 (时间窗口 + 切片策略)
- **Task** - 原子工作单元 (例如: 从 PubMed 抓取记录 1-1000)
- **Outbox** - 事务性事件发布
- **Cursor** - 水位线跟踪 (增量采集)

## 文档位置

每个模块 `README.md` + 每个包 `package-info.java`
