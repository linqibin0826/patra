# patra-object-storage — 对象存储元数据服务

## 概述

**patra-object-storage** 是 Patra 平台的对象存储元数据管理微服务,专门负责记录和管理上传到外部对象存储提供商(MinIO/S3)的文件元数据。

作为平台的基础设施服务,patra-object-storage 提供了文件上传记录的持久化存储,维护文件与业务上下文的关联关系,并通过唯一的 `storage_key` (bucket/objectKey) 确保幂等性。该服务仅对内部微服务提供 HTTP Interface API,不暴露公共 REST 接口。

**核心价值**:
- **元数据持久化**: 记录文件大小、校验和、MIME 类型、业务上下文等完整元数据
- **业务关联**: 将存储文件与上游业务(如文献批次、采集任务)关联,提供完整的数据血缘追踪
- **幂等性保障**: 通过唯一约束的 `storage_key` 防止重复记录,确保数据一致性
- **生命周期管理**: 支持文件过期时间、软删除、状态追踪等生命周期管理功能
- **审计追溯**: 完整记录创建人、更新人、IP 地址等审计信息,满足合规要求

## 核心职责

- **文件元数据记录**: 接受来自内部服务(patra-ingest、patra-registry 等)的上传记录请求,持久化文件元数据
- **业务上下文管理**: 存储 `serviceName`、`businessType`、`businessId`、`correlationData` 等业务上下文
- **幂等性控制**: 通过数据库唯一约束确保同一 `storage_key` 不会重复记录
- **生命周期追踪**: 追踪文件状态(ACTIVE/DELETED)、上传时间、过期时间、删除时间
- **内部 API 提供**: 为其他微服务提供 HTTP Interface 接口,实现类型安全的服务间调用

## 架构设计

### 六边形架构

本服务严格遵循**六边形架构**(Ports and Adapters)和**领域驱动设计**(DDD)原则:

```
┌──────────────────────────────────────────────────────────────┐
│                    patra-object-storage-boot                        │
│              (Spring Boot 应用启动入口)                        │
└──────────────────────────────────────────────────────────────┘
                              ▲
                              │
        ┌─────────────────────┴─────────────────────┐
        │                                           │
┌───────▼──────────┐                    ┌───────────▼─────────┐
│  adapter (入站)   │                    │   infra (出站)      │
│  REST 控制器      │                    │   仓储实现          │
│  实现 API 契约    │                    │   JPA Repository   │
└───────┬──────────┘                    └───────────▲─────────┘
        │                                           │
        │         ┌──────────────────┐              │
        └────────►│       app        │──────────────┘
                  │   用例编排器      │
                  │  @Transactional  │
                  └────────┬─────────┘
                           │
                  ┌────────▼─────────┐
                  │      domain      │
                  │   聚合根 + 端口   │
                  │   纯 Java 代码   │
                  └──────────────────┘
                           ▲
                           │
                  ┌────────┴─────────┐
                  │       api        │
                  │ HTTP Interface   │
                  │   DTO + 接口     │
                  └──────────────────┘
```

**依赖方向**: Adapter → App → Domain ← Infra

### 模块结构

```
patra-object-storage/
├── patra-object-storage-api/        # 外部契约层 - HTTP Interface 接口 + DTO
├── patra-object-storage-domain/     # 领域层 - 聚合根、值对象、仓储端口(纯 Java)
├── patra-object-storage-app/        # 应用层 - 用例编排器、事务管理
├── patra-object-storage-infra/      # 基础设施层 - JPA 仓储实现 + Flyway 迁移
├── patra-object-storage-adapter/    # 适配器层 - REST 控制器、端点实现
└── patra-object-storage-boot/       # 启动模块 - Spring Boot 应用入口
```

**架构约束**:
- **domain 层**: 禁止依赖 Spring/JPA 等框架,仅允许 `patra-common-core` + Lombok/Hutool
- **Gradle 架构检查**: 通过 `enforceDomainPurity` 任务强制检查 domain 层纯净性
- **依赖方向**: 所有模块均指向 domain,确保业务逻辑独立于框架

## 领域模型

### 聚合根

**FileMetadata** (文件元数据聚合根):
- **标识符**: `id` (Long, 数据库主键)
- **存储键**: `storageKey` (StorageKey 值对象, bucket/objectKey 组合)
- **文件属性**: `fileSize`、`contentType`、`checksum` (MD5/SHA256)
- **业务上下文**: `context` (BusinessContext 值对象, 包含 serviceName/businessType/businessId/correlationData)
- **存储提供商**: `provider` (StorageProvider 枚举, 如 MINIO/S3)
- **生命周期**: `status` (FileStatus 枚举)、`uploadedAt`、`expiresAt`、`deletedAt`
- **审计信息**: `createdBy`、`updatedBy`、`ipAddress`、`recordRemarks`、`version` (乐观锁)

### 值对象

- **StorageKey**: 不可变存储定位符,包含 `bucket` 和 `objectKey`,提供 `fullKey()` 方法生成规范键
- **FileSize**: 文件大小封装,单位字节
- **FileChecksum**: 校验和封装,包含 `md5Hash` 和 `sha256Hash`
- **BusinessContext**: 业务上下文封装,包含调用服务、业务类型、业务标识、关联数据

### 仓储端口

**FileMetadataRepository** (领域层接口):
- `save(FileMetadata metadata)`: 保存或更新聚合根
- `findByStorageKey(StorageKey storageKey)`: 通过存储键查询元数据

## 数据模型

### 数据库表结构

**storage_file_metadata** (文件元数据表):

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | BIGINT UNSIGNED | 主键 |
| `storage_key` | VARCHAR(768) | 完整存储键 (bucket/objectKey), **唯一索引** |
| `bucket_name` | VARCHAR(128) | 存储桶名称 |
| `object_key` | VARCHAR(512) | 对象键 |
| `file_size` | BIGINT | 文件大小(字节) |
| `content_type` | VARCHAR(128) | MIME 类型 |
| `md5_hash` | VARCHAR(64) | MD5 校验和 |
| `sha256_hash` | VARCHAR(128) | SHA-256 校验和(可选) |
| `service_name` | VARCHAR(64) | 调用服务名称 |
| `business_type` | VARCHAR(64) | 业务分类 |
| `business_id` | VARCHAR(128) | 业务标识 |
| `correlation_data` | JSON | 关联元数据(JSON 格式) |
| `provider_type` | VARCHAR(32) | 存储提供商类型 |
| `file_status` | VARCHAR(32) | 文件状态(ACTIVE/DELETED) |
| `uploaded_at` | TIMESTAMP(6) | 上传完成时间 |
| `expires_at` | TIMESTAMP(6) | 过期时间(可选) |
| `deleted_at` | TIMESTAMP(6) | 软删除时间(可选) |
| `record_remarks` | JSON | 审计备注(JSON 格式) |
| `version` | BIGINT UNSIGNED | 乐观锁版本号 |
| `ip_address` | VARBINARY(16) | 请求者 IP(IPv4/IPv6) |
| `created_at` | TIMESTAMP(6) | 创建时间 |
| `created_by` | BIGINT UNSIGNED | 创建人 ID |
| `created_by_name` | VARCHAR(100) | 创建人姓名 |
| `updated_at` | TIMESTAMP(6) | 更新时间 |
| `updated_by` | BIGINT UNSIGNED | 更新人 ID |
| `updated_by_name` | VARCHAR(100) | 更新人姓名 |
| `deleted` | TINYINT(1) | 软删除标志 |

**索引**:
- `PRIMARY KEY (id)`: 主键索引
- `UNIQUE KEY uk_storage_key (storage_key)`: 唯一索引,确保幂等性
- `KEY idx_uploaded_at (uploaded_at)`: 上传时间索引
- `KEY idx_deleted (deleted)`: 软删除标志索引

## API 契约

### HTTP Interface 端点

**StorageEndpoint** (`@HttpExchange` 注解定义):
```java
@HttpExchange(url = "/_internal/storage", accept = "application/json", contentType = "application/json")
public interface StorageEndpoint {
    @PostExchange("/files/record")
    RecordUploadResponse recordUpload(@RequestBody UploadRecordRequest request);
}
```

### 端点定义

**POST /internal/storage/files/record** - 记录文件上传元数据

**请求体** (`UploadRecordRequest`):
```json
{
  "bucketName": "publication-files",
  "objectKey": "2024/01/pubmed/articles/PMC12345678.pdf",
  "fileSize": 1024000,
  "contentType": "application/pdf",
  "md5Hash": "5d41402abc4b2a76b9719d911017c592",
  "sha256Hash": "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae",
  "serviceName": "patra-ingest",
  "businessType": "publication_batch",
  "businessId": "batch-2024-01-15-001",
  "correlationData": {
    "sourceId": "pubmed",
    "pmcId": "PMC12345678",
    "downloadUrl": "https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12345678/pdf"
  },
  "providerType": "MINIO",
  "expiresAt": "2025-12-31T23:59:59Z",
  "recordRemarks": "Initial publication batch upload"
}
```

**响应体** (`RecordUploadResponse`):
```json
{
  "metadataId": 123456,
  "recordedAt": "2024-01-15T10:30:45.123456Z"
}
```

## 使用示例

### 在其他服务中集成

**1. 添加依赖** (在 `build.gradle.kts` 中):
```kotlin
implementation(project(":patra-object-storage:patra-object-storage-api"))
```

**2. 创建 HTTP Interface 代理并注入**:
```java
@Configuration
public class StorageClientConfig {
    @Bean
    public StorageEndpoint storageEndpoint(
            @Qualifier("httpInterfaceLoadBalancedRestClientBuilder") RestClient.Builder builder,
            RestClientFactory factory) {
        RestClient client = factory.createRestClient(builder, "storage", "lb://patra-object-storage");
        return factory.createProxy(client, StorageEndpoint.class);
    }
}

@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final StorageEndpoint storageEndpoint;

    public void recordUpload(String bucket, String key, long size, String md5) {
        var request = new UploadRecordRequest(
            bucket, key, size, "application/octet-stream",
            md5, null, "my-service", "data_export",
            "export-001", Map.of(), "MINIO", null, null
        );

        RecordUploadResponse response = storageEndpoint.recordUpload(request);
        log.info("Metadata recorded with id: {}", response.metadataId());
    }
}
```

## 配置说明

### application.yml 示例

```yaml
spring:
  application:
    name: patra-object-storage

  datasource:
    url: jdbc:mysql://localhost:3306/patra_storage?useUnicode=true&characterEncoding=utf8mb4
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:password}

  cloud:
    consul:
      host: ${CONSUL_HOST:localhost}
      port: ${CONSUL_PORT:8500}
      discovery:
        service-name: ${spring.application.name}
        health-check-interval: 10s
        health-check-path: /actuator/health

spring:
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          batch_size: 50
```

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 25 | 语言版本 |
| Spring Boot | 4.0.1 | 应用框架 |
| Spring Cloud | 2025.1.0 | 微服务框架 |
| Spring Data JPA | (Spring Boot 管理) | ORM 框架 |
| MapStruct | 1.6.5+ | 对象映射 |
| Consul | 1.18+ | 服务注册中心 |
| MySQL | 8.0+ | 数据库 |
| Flyway | 10.30.0+ | 数据库迁移工具 |
| Hutool | 5.8.36+ | 工具库 |

## 开发指南

### 前置要求

- **JDK 25+**: 确保安装 Java 25 或更高版本
- **Gradle 8.x+**: 用于构建和依赖管理（已内置 Wrapper）
- **MySQL 8.0+**: 数据库服务
- **Consul**: 服务注册中心(开发环境可选)

### 本地启动

1. **初始化数据库**:
```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS patra_storage CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

2. **配置环境变量** (可选):
```bash
export DB_USERNAME=root
export DB_PASSWORD=your_password
export CONSUL_HOST=localhost
export CONSUL_PORT=8500
```

3. **编译并启动**:
```bash
# 在项目根目录执行
./gradlew :patra-object-storage:patra-object-storage-boot:build -x test
./gradlew :patra-object-storage:patra-object-storage-boot:bootRun
```

4. **验证启动**:
```bash
# 检查健康端点
curl http://localhost:8080/actuator/health

# 查看服务注册（Consul）
curl http://localhost:8500/v1/catalog/service/patra-object-storage
```

### 代码规范

遵循项目统一的代码规范:
- **Google Java Style Guide**: 所有 Java 代码遵循 Google 风格
- **中文注释**: 所有注释、文档、提交信息使用简体中文
- **领域层纯净性**: domain 模块禁止引入框架依赖,由 Gradle `enforceDomainPurity` 任务强制检查
- **依赖方向**: 严格遵守六边形架构的依赖方向规则

## 故障排查

### 常见问题

**Q: 启动时报错 "storage_key 唯一约束冲突"**
- **原因**: 尝试记录已存在的 storage_key
- **解决**: 检查上游服务是否正确处理幂等性,或手动清理测试数据

**Q: HTTP Interface 调用超时**
- **原因**: 网络延迟或服务未启动
- **解决**:
  - 检查 Consul 服务注册状态
  - 调整 RestClient 超时配置: `patra.http.interface.connect-timeout=5s`

**Q: Flyway 迁移失败**
- **原因**: 数据库版本不兼容或迁移脚本错误
- **解决**:
  - 确保 MySQL 版本 >= 8.0
  - 检查 `db/migration` 目录下的 SQL 脚本语法
  - 必要时清理 `flyway_schema_history` 表重新迁移

## 相关文档

- **子模块文档**: 每个子模块目录下都有独立的 `README.md`,详细说明模块职责和设计
- **API 契约**: 参见 `patra-object-storage-api/README.md`
- **领域模型**: 参见 `patra-object-storage-domain/README.md`
- **数据库设计**: 参见 `patra-object-storage-infra/src/main/resources/db/migration/V0.1.0__init_storage_schema.sql`

## 许可证

版权所有 © 2024 Patra 团队
