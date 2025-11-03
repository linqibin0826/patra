# patra-registry-domain

## 概述

`patra-registry-domain` 是 patra-registry 服务的**领域层模块**,包含纯 Java 业务逻辑和领域模型,不依赖任何外部框架。本模块遵循六边形架构和 DDD 设计原则,实现了领域模型的纯粹性和可测试性。

在六边形架构中,本模块处于核心位置,定义了业务规则、领域对象、仓储接口(出站端口),被应用层(`patra-registry-app`)调用,由基础设施层(`patra-registry-infra`)实现。

## 核心职责

- **领域模型定义**: 定义聚合根、值对象、领域事件等核心业务概念
- **业务规则封装**: 在领域对象中封装业务不变性约束和验证逻辑
- **出站端口声明**: 定义仓储接口(`ProvenanceConfigRepository`、`ExprRepository`)
- **框架无关**: 保持领域层的纯粹性,仅依赖 `patra-common-core` 和标准 Java 库

## 模块结构

```
patra-registry-domain/
└── src/main/java/com/patra/registry/domain/
    ├── exception/                       # 领域异常
    │   ├── DomainValidationException.java
    │   ├── RegistryException.java
    │   ├── RegistryNotFound.java
    │   ├── RegistryConflict.java
    │   ├── RegistryQuotaExceeded.java
    │   ├── RegistryRuleViolation.java
    │   └── provenance/
    │       └── ProvenanceNotFoundException.java
    ├── model/                           # 领域模型
    │   ├── aggregate/                   # 聚合根
    │   │   └── ProvenanceConfiguration.java
    │   ├── vo/                          # 值对象
    │   │   ├── provenance/              # 数据源相关值对象
    │   │   │   ├── Provenance.java
    │   │   │   ├── WindowOffsetConfig.java
    │   │   │   ├── PaginationConfig.java
    │   │   │   ├── HttpConfig.java
    │   │   │   ├── BatchingConfig.java
    │   │   │   ├── RetryConfig.java
    │   │   │   └── RateLimitConfig.java
    │   │   └── expr/                    # 表达式相关值对象
    │   │       ├── ExprSnapshot.java
    │   │       ├── ExprCapability.java
    │   │       ├── ApiParamMapping.java
    │   │       ├── ExprField.java
    │   │       └── ExprRenderRule.java
    │   └── read/                        # 读模型(CQRS 查询对象)
    │       ├── provenance/              # 数据源查询模型
    │       │   ├── ProvenanceQuery.java
    │       │   ├── ProvenanceConfigQuery.java
    │       │   ├── WindowOffsetQuery.java
    │       │   ├── PaginationConfigQuery.java
    │       │   ├── HttpConfigQuery.java
    │       │   ├── BatchingConfigQuery.java
    │       │   ├── RetryConfigQuery.java
    │       │   └── RateLimitConfigQuery.java
    │       └── expr/                    # 表达式查询模型
    │           ├── ExprSnapshotQuery.java
    │           ├── ExprCapabilityQuery.java
    │           ├── ApiParamMappingQuery.java
    │           ├── ExprFieldQuery.java
    │           └── ExprRenderRuleQuery.java
    ├── port/                            # 出站端口(仓储接口)
    │   ├── ProvenanceConfigRepository.java
    │   └── ExprRepository.java
    └── support/                         # 领域支持类
        ├── TemporalEntity.java          # 时态实体基类
        ├── RegistryKeyStandardizer.java # 键标准化器
        └── RegistryKeyPlaceholders.java # 键占位符工具
```

## 主要组件

### ProvenanceConfiguration (聚合根)

数据源配置聚合根,提供数据源和多个配置维度的整合只读视图。

**核心字段**:
- `provenance`: 数据源元数据(必须)
- `windowOffset`: 时间窗口偏移配置(可选)
- `pagination`: 分页策略(可选)
- `http`: HTTP 客户端配置(可选)
- `batching`: 批处理配置(可选)
- `retry`: 重试策略(可选)
- `rateLimit`: 速率限制(可选)

**设计模式**: 只读聚合,用于 CQRS 读端

**使用示例**:
```java
ProvenanceConfiguration config = new ProvenanceConfiguration(
    provenance,
    windowOffset,
    pagination,
    http,
    batching,
    retry,
    rateLimit
);

if (config.hasHttpConfig()) {
    HttpConfig httpConfig = config.http();
    // 使用 HTTP 配置
}
```

### Provenance (值对象)

数据源的核心元数据值对象,包含唯一标识符、显示名称、默认基础 URL、默认时区、激活状态等。

**核心字段**:
- `id`: 主键,所有下游配置引用的唯一来源标识符
- `code`: 来源代码,全局唯一、稳定(如 `pubmed`、`crossref`)
- `name`: 来源显示名称(如 "PubMed"、"Crossref")
- `baseUrlDefault`: 默认基础 URL
- `timezoneDefault`: 默认时区(IANA TZ,如 `UTC`、`Asia/Shanghai`)
- `active`: 激活标志

**验证规则**:
- `id` 必须为正数
- `code`、`name`、`timezoneDefault` 不能为空白
- 所有字符串字段自动 trim

### ProvenanceConfigRepository (出站端口)

数据源配置仓储接口,定义了数据源和配置的查询操作。

**核心方法**:
- `findProvenanceByCode(ProvenanceCode)`: 根据代码查询数据源
- `findAllProvenances()`: 查询所有数据源
- `findActiveWindowOffset(Long, String, Instant)`: 查询有效的时间窗口偏移配置
- `findActivePagination(Long, String, Instant)`: 查询有效的分页配置
- `loadConfiguration(Long, String, Instant)`: 加载完整配置聚合

**时态查询**: 所有 `findActive*` 方法接受 `at` 参数,查询指定时刻有效的配置

### ExprRepository (出站端口)

表达式仓储接口,定义了表达式元数据的查询操作。

**核心方法**:
- `loadSnapshot(ProvenanceCode, String, String, Instant)`: 加载完整表达式快照

**返回对象**: `ExprSnapshot` 包含字段定义、能力、渲染规则和参数映射

### 读模型(CQRS)

本模块采用 CQRS 模式,区分写模型和读模型:
- **值对象(`vo`)**: 用于写端,包含业务逻辑和验证
- **查询对象(`read`)**: 用于读端,简化查询,无业务逻辑

**设计目的**: 优化读写性能,简化查询复杂度

## 依赖关系

**上游依赖**:
- `patra-common-core`: 共享枚举(`ProvenanceCode`、`ConfigScope` 等)和工具类
- 标准 Java 库: 无外部框架依赖

**下游消费者**:
- `patra-registry-app`: 使用领域模型和仓储接口编排用例
- `patra-registry-infra`: 实现仓储接口,持久化领域对象

## 领域概念

### 1. 时态配置

所有运营配置都具有时间有效性范围:
- `effectiveFrom`: 配置生效时间
- `effectiveUntil`: 配置失效时间

**查询模式**: 通过 `at` 参数查询指定时刻有效的配置,支持配置的安全更新和审计。

### 2. 配置作用域

配置按作用域分为三级(优先级从高到低):
- **TASK 级**: 任务特定配置
- **OPERATION 级**: 操作类型特定配置(HARVEST、UPDATE)
- **SOURCE 级**: 数据源默认配置

**优先级规则**: 高优先级配置覆盖低优先级配置

### 3. 领域验证

领域对象在构造时执行验证,确保业务不变性:
- `DomainValidationException.notBlank(value, fieldName)`: 非空白验证
- `DomainValidationException.positive(value, fieldName)`: 正数验证
- `DomainValidationException.nonNull(value, fieldName)`: 非空验证

**验证时机**: 对象构造时立即验证,快速失败

## 架构约束

### Maven Enforcer 规则

本模块通过 Maven Enforcer 插件强制执行领域层纯粹性约束:

**禁止依赖**:
- Spring Framework (`org.springframework:*`)
- Jakarta EE (`jakarta.persistence:*`、`jakarta.validation:*`)
- 持久化框架 (`com.baomidou:*`、`org.mybatis:*`、`org.hibernate:*`)
- Web/Servlet (`org.apache.tomcat:*`、`io.netty:*`)

**设计目的**: 确保领域层的框架无关性和可测试性

**验证阶段**: Maven `validate` 阶段自动检查

## 设计原则

### 1. 纯粹性

- 领域层仅依赖纯 Java 和 `patra-common-core`
- 不引入 Spring、JPA、MyBatis 等框架
- 业务逻辑完全独立于技术实现

### 2. 不变性

- 值对象使用 `record` 实现不可变性
- 聚合根字段为 `final`,确保状态不可变
- 线程安全,无并发问题

### 3. 自验证

- 领域对象在构造时自我验证
- 通过规范构造器强制执行业务规则
- 失败时抛出 `DomainValidationException`

### 4. 端口模式

- 仓储接口定义在领域层,实现在基础设施层
- 依赖倒置,领域层不依赖具体实现
- 易于测试和替换实现

## 相关文档

- [patra-registry 顶层文档](../README.md)
- [patra-registry-app 模块](../patra-registry-app/README.md) - 领域模型的消费方
- [patra-registry-infra 模块](../patra-registry-infra/README.md) - 仓储接口的实现方

---

**最后更新**: 2025-01-12
