# patra-storage-infra — 基础设施层

## 概述

**patra-storage-infra** 是 patra-storage 服务的基础设施层,负责提供领域层所需的技术实现,主要包括数据持久化、数据库访问、对象映射和数据库迁移。

在六边形架构中,基础设施层实现领域层定义的**仓储端口**(Repository Port),提供具体的持久化技术实现(MyBatis-Plus + MySQL),同时通过 MapStruct 完成领域对象与数据对象之间的转换。

**核心原则**:
- **依赖倒置**: 实现领域层定义的端口接口,依赖方向指向领域层
- **技术隔离**: 将技术细节(MyBatis、SQL)隔离在基础设施层,不泄露到领域层
- **对象转换**: 通过 Converter 转换领域对象(Aggregate)和数据对象(DO)
- **数据库迁移**: 使用 Flyway 管理数据库版本和 Schema 变更

## 核心职责

- **仓储实现**: 实现 `FileMetadataRepository` 端口接口
- **数据访问**: 通过 MyBatis-Plus 提供 CRUD 操作
- **对象映射**: 使用 MapStruct 转换聚合根与数据对象
- **数据库迁移**: 使用 Flyway 管理 Schema 版本
- **查询优化**: 提供高效的数据库查询和索引策略

## 模块结构

```
patra-storage-infra/
└── src/main/
    ├── java/com/patra/storage/infra/
    │   └── persistence/
    │       ├── repository/
    │       │   └── FileMetadataRepositoryImpl.java    # 仓储实现
    │       ├── mapper/
    │       │   └── FileMetadataMapper.java            # MyBatis Mapper
    │       ├── entity/
    │       │   └── FileMetadataDO.java                # 数据对象
    │       └── converter/
    │           └── FileMetadataConverter.java         # 对象转换器
    └── resources/
        ├── mapper/
        │   └── FileMetadataMapper.xml                 # MyBatis XML 映射
        └── db/migration/
            └── V0.1.0__init_storage_schema.sql        # Flyway 迁移脚本
```

## 主要组件

### FileMetadataRepositoryImpl (仓储实现)

实现领域层的 `FileMetadataRepository` 端口接口,提供持久化能力。

```java
@Repository
@RequiredArgsConstructor
public class FileMetadataRepositoryImpl implements FileMetadataRepository {

    private final FileMetadataMapper mapper;
    private final FileMetadataConverter converter;

    @Override
    public FileMetadata save(FileMetadata metadata) {
        // 1. 转换为 DO
        FileMetadataDO dataObject = converter.toDO(metadata);

        // 2. 执行插入或更新
        if (metadata.getId() == null) {
            mapper.insert(dataObject);
        } else {
            mapper.updateById(dataObject);
        }

        // 3. 重新加载最新数据
        FileMetadataDO persisted = mapper.selectById(dataObject.getId());

        // 4. 转换回聚合根
        return converter.toAggregate(persisted);
    }

    @Override
    public Optional<FileMetadata> findByStorageKey(StorageKey storageKey) {
        FileMetadataDO dataObject = mapper.findByStorageKey(storageKey.fullKey());
        return Optional.ofNullable(converter.toAggregate(dataObject));
    }
}
```

**设计要点**:
- **依赖注入**: 注入 MyBatis Mapper 和对象转换器
- **双向转换**: 保存时 Aggregate → DO,查询时 DO → Aggregate
- **重新加载**: 保存后重新加载确保获取数据库生成的字段(ID、timestamp)
- **空值处理**: `findByStorageKey` 使用 `Optional` 处理不存在的情况

**性能优化**:
- 使用 MyBatis-Plus `BaseMapper` 提供的批量操作
- 通过 `selectById` 利用主键索引快速查询
- 避免 N+1 查询问题

### FileMetadataMapper (MyBatis Mapper)

MyBatis-Plus Mapper 接口,提供数据库访问能力。

```java
@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadataDO> {

    /**
     * 通过存储键查询记录
     *
     * @param storageKey bucket/objectKey 组合
     * @return 匹配的记录,不存在返回 null
     */
    FileMetadataDO findByStorageKey(@Param("storageKey") String storageKey);
}
```

**继承的能力**:
- `insert(FileMetadataDO)`: 插入新记录
- `updateById(FileMetadataDO)`: 根据 ID 更新记录
- `selectById(Serializable)`: 根据 ID 查询记录
- `deleteById(Serializable)`: 根据 ID 删除记录
- 分页查询、条件构造器等 MyBatis-Plus 提供的丰富功能

**自定义查询**:
- `findByStorageKey`: 根据唯一索引 `uk_storage_key` 查询,用于幂等性检查

**XML 映射示例**:
```xml
<mapper namespace="com.patra.storage.infra.persistence.mapper.FileMetadataMapper">
    <select id="findByStorageKey" resultType="com.patra.storage.infra.persistence.entity.FileMetadataDO">
        SELECT * FROM storage_file_metadata
        WHERE storage_key = #{storageKey} AND deleted = 0
    </select>
</mapper>
```

### FileMetadataDO (数据对象)

数据对象,映射到 `storage_file_metadata` 数据库表。

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "storage_file_metadata", autoResultMap = true)
public class FileMetadataDO extends BaseDO {

    @TableField("storage_key")
    private String storageKey;              // bucket/objectKey 完整键

    @TableField("bucket_name")
    private String bucketName;              // 存储桶名称

    @TableField("object_key")
    private String objectKey;               // 对象键

    @TableField("file_size")
    private Long fileSize;                  // 文件大小(字节)

    @TableField("content_type")
    private String contentType;             // MIME 类型

    @TableField("md5_hash")
    private String md5Hash;                 // MD5 校验和

    @TableField("sha256_hash")
    private String sha256Hash;              // SHA-256 校验和

    @TableField("service_name")
    private String serviceName;             // 调用服务名

    @TableField("business_type")
    private String businessType;            // 业务类型

    @TableField("business_id")
    private String businessId;              // 业务标识

    @TableField(value = "correlation_data", typeHandler = JacksonTypeHandler.class)
    private JsonNode correlationData;       // 关联数据(JSON)

    @TableField("provider_type")
    private String providerType;            // 存储提供商

    @TableField("file_status")
    private String fileStatus;              // 文件状态

    @TableField("uploaded_at")
    private Instant uploadedAt;             // 上传时间

    @TableField("expires_at")
    private Instant expiresAt;              // 过期时间

    @TableField("deleted_at")
    private Instant deletedAt;              // 删除时间
}
```

**设计要点**:
- **继承 BaseDO**: 继承公共审计字段(createdAt、updatedAt、deleted 等)
- **@TableName**: 指定表名,`autoResultMap = true` 支持 TypeHandler
- **@TableField**: 指定列名,处理驼峰命名转换
- **JacksonTypeHandler**: 自动序列化/反序列化 JSON 字段

**与聚合根的差异**:
- **DO**: 扁平结构,所有字段都是基本类型或简单对象,便于 ORM 映射
- **Aggregate**: 富对象模型,包含值对象和业务行为

### FileMetadataConverter (对象转换器)

MapStruct 转换器,负责聚合根与数据对象之间的双向转换。

```java
@Mapper(componentModel = "spring")
public interface FileMetadataConverter {

    /**
     * 将聚合根转换为数据对象
     *
     * @param aggregate 文件元数据聚合根
     * @return 数据对象
     */
    @Mapping(target = "storageKey", expression = "java(aggregate.getStorageKey().fullKey())")
    @Mapping(target = "bucketName", expression = "java(aggregate.getStorageKey().bucket())")
    @Mapping(target = "objectKey", expression = "java(aggregate.getStorageKey().objectKey())")
    @Mapping(target = "fileSize", expression = "java(aggregate.getFileSize().bytes())")
    @Mapping(target = "md5Hash", expression = "java(aggregate.getChecksum().md5Hash())")
    @Mapping(target = "sha256Hash", expression = "java(aggregate.getChecksum().sha256Hash())")
    @Mapping(target = "serviceName", expression = "java(aggregate.getContext().serviceName())")
    @Mapping(target = "businessType", expression = "java(aggregate.getContext().businessType())")
    @Mapping(target = "businessId", expression = "java(aggregate.getContext().businessId())")
    @Mapping(target = "correlationData", expression = "java(toJsonNode(aggregate.getContext().correlationData()))")
    @Mapping(target = "providerType", expression = "java(aggregate.getProvider().name())")
    @Mapping(target = "fileStatus", expression = "java(aggregate.getStatus().name())")
    FileMetadataDO toDO(FileMetadata aggregate);

    /**
     * 将数据对象转换为聚合根
     *
     * @param dataObject 数据对象
     * @return 文件元数据聚合根
     */
    @Mapping(target = "storageKey", expression = "java(new StorageKey(dataObject.getBucketName(), dataObject.getObjectKey()))")
    @Mapping(target = "fileSize", expression = "java(new FileSize(dataObject.getFileSize()))")
    @Mapping(target = "checksum", expression = "java(new FileChecksum(dataObject.getMd5Hash(), dataObject.getSha256Hash()))")
    @Mapping(target = "context", expression = "java(new BusinessContext(dataObject.getServiceName(), dataObject.getBusinessType(), dataObject.getBusinessId(), toMap(dataObject.getCorrelationData())))")
    @Mapping(target = "provider", expression = "java(StorageProvider.fromName(dataObject.getProviderType()))")
    @Mapping(target = "status", expression = "java(FileStatus.valueOf(dataObject.getFileStatus()))")
    FileMetadata toAggregate(FileMetadataDO dataObject);

    // 辅助方法:Map → JsonNode
    default JsonNode toJsonNode(Map<String, Object> map) {
        // 使用 Jackson ObjectMapper 转换
    }

    // 辅助方法:JsonNode → Map
    default Map<String, Object> toMap(JsonNode jsonNode) {
        // 使用 Jackson ObjectMapper 转换
    }
}
```

**设计要点**:
- **MapStruct**: 使用 MapStruct 自动生成转换代码,编译时生成实现类
- **值对象拆分**: 将值对象拆分为基本类型字段存储
- **JSON 转换**: `correlationData` 在 Map 和 JsonNode 之间转换
- **枚举转换**: 枚举类型与字符串之间转换

**性能优势**:
- 编译时生成转换代码,无运行时反射
- 零依赖,生成的代码为纯 Java 赋值语句
- 类型安全,编译期检查

## 数据库设计

### 表结构

**storage_file_metadata** (文件元数据表):

```sql
CREATE TABLE storage_file_metadata (
    id                 BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    storage_key        VARCHAR(768) NOT NULL COMMENT '完整存储键(bucket/objectKey)',
    bucket_name        VARCHAR(128) NOT NULL COMMENT '存储桶名称',
    object_key         VARCHAR(512) NOT NULL COMMENT '对象键',
    file_size          BIGINT NOT NULL COMMENT '文件大小(字节)',
    content_type       VARCHAR(128) COMMENT 'MIME 类型',
    md5_hash           VARCHAR(64) NOT NULL COMMENT 'MD5 校验和',
    sha256_hash        VARCHAR(128) COMMENT 'SHA-256 校验和',
    service_name       VARCHAR(64) NOT NULL COMMENT '调用服务名',
    business_type      VARCHAR(64) NOT NULL COMMENT '业务类型',
    business_id        VARCHAR(128) NOT NULL COMMENT '业务标识',
    correlation_data   JSON COMMENT '关联元数据',
    provider_type      VARCHAR(32) NOT NULL COMMENT '存储提供商',
    file_status        VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '文件状态',
    uploaded_at        TIMESTAMP(6) NOT NULL COMMENT '上传时间',
    expires_at         TIMESTAMP(6) COMMENT '过期时间',
    deleted_at         TIMESTAMP(6) COMMENT '删除时间',
    record_remarks     JSON COMMENT '审计备注',
    version            BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    ip_address         VARBINARY(16) COMMENT '请求者 IP',
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by         BIGINT UNSIGNED,
    created_by_name    VARCHAR(100),
    updated_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by         BIGINT UNSIGNED,
    updated_by_name    VARCHAR(100),
    deleted            TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标志',

    UNIQUE KEY uk_storage_key (storage_key),
    KEY idx_uploaded_at (uploaded_at),
    KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 索引策略

**主键索引**:
- `PRIMARY KEY (id)`: 自增主键,确保唯一性

**唯一索引**:
- `UNIQUE KEY uk_storage_key (storage_key)`: 确保同一 `storage_key` 不会重复,实现幂等性

**普通索引**:
- `KEY idx_uploaded_at (uploaded_at)`: 支持按上传时间范围查询
- `KEY idx_deleted (deleted)`: 优化软删除查询

**复合索引**(未来可能添加):
- `KEY idx_business (service_name, business_type, business_id)`: 支持业务维度查询

### 数据类型选择

- **BIGINT UNSIGNED**: ID 字段,支持海量数据
- **VARCHAR**: 字符串字段,UTF-8MB4 编码支持 Emoji
- **JSON**: 半结构化数据(correlationData、recordRemarks),支持查询和索引
- **TIMESTAMP(6)**: 时间戳,微秒精度,避免并发冲突
- **VARBINARY(16)**: IP 地址,支持 IPv4(4 字节)和 IPv6(16 字节)

### Flyway 迁移

**版本管理**:
```
db/migration/
├── V0.1.0__init_storage_schema.sql       # 初始化 Schema
├── V0.1.1__add_index_business.sql        # 添加业务索引(未来)
└── V0.2.0__add_file_tags.sql             # 添加文件标签(未来)
```

**命名规范**:
- `V{version}__{description}.sql`: 版本迁移脚本
- `R__{description}.sql`: 可重复执行脚本(如视图、存储过程)

**执行时机**:
- 应用启动时自动执行未应用的迁移脚本
- 通过 `flyway_schema_history` 表追踪已执行的迁移

## 依赖关系

### 上游依赖

- **patra-storage-domain**: 领域层(聚合根、值对象、端口接口)
- **patra-spring-boot-starter-mybatis**: Patra MyBatis 集成 Starter
- **patra-spring-boot-starter-core**: Patra 核心 Starter
- **MapStruct**: 对象映射框架
- **MyBatis-Plus**: ORM 框架
- **MySQL Connector**: MySQL 驱动
- **Flyway**: 数据库迁移工具

### 下游消费者

- **patra-storage-boot**: 启动模块依赖本模块提供仓储实现

## 使用示例

### 直接使用仓储(单元测试)

```java
@SpringBootTest
class FileMetadataRepositoryImplTest {

    @Autowired
    private FileMetadataRepository repository;

    @Test
    @Transactional
    void save_shouldPersistAndReturnAggregate() {
        // Given
        FileMetadata metadata = FileMetadata.create(
            new StorageKey("test-bucket", "test-key"),
            new FileSize(1024),
            new FileChecksum("md5hash", null),
            new BusinessContext("test-service", "test-type", "test-id", Map.of()),
            StorageProvider.MINIO
        );

        // When
        FileMetadata saved = repository.save(metadata);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStorageKey().fullKey()).isEqualTo("test-bucket/test-key");
    }

    @Test
    void findByStorageKey_shouldReturnExistingMetadata() {
        // Given
        StorageKey key = new StorageKey("existing-bucket", "existing-key");

        // When
        Optional<FileMetadata> result = repository.findByStorageKey(key);

        // Then
        assertThat(result).isPresent();
    }
}
```

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| MyBatis-Plus | 3.5.9+ | ORM 框架 |
| MapStruct | 1.6.5+ | 对象映射框架 |
| Flyway | 10.30.0+ | 数据库迁移工具 |
| MySQL | 8.0+ | 关系型数据库 |
| HikariCP | 6.2.1+ | 连接池(Spring Boot 默认) |
| Spring Boot | 3.5.7 | 应用框架 |

## 最佳实践

### 仓储实现原则

1. **领域对象隔离**: 仓储接口参数和返回值使用领域对象,而非 DO
2. **双向转换**: 通过 Converter 完成聚合根与 DO 的双向转换
3. **乐观锁**: 使用 `version` 字段防止并发更新冲突
4. **软删除**: 使用 `deleted` 标志而非物理删除,保留审计记录

### MyBatis-Plus 配置

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true     # 驼峰命名转换
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      logic-delete-field: deleted          # 软删除字段
      logic-delete-value: 1                # 删除值
      logic-not-delete-value: 0            # 未删除值
  type-handlers-package: com.patra.storage.infra.persistence.typehandler
```

### 性能优化

1. **索引优化**: 为常用查询条件添加索引
2. **批量操作**: 使用 MyBatis-Plus 的批量插入/更新能力
3. **连接池调优**: 根据负载调整 HikariCP 配置
4. **查询优化**: 避免 `SELECT *`,仅查询需要的字段

### 数据库迁移最佳实践

1. **版本递增**: 版本号严格递增,不回退
2. **幂等性**: 使用 `IF NOT EXISTS` 确保脚本可重复执行
3. **向后兼容**: 新版本不删除现有字段,使用 `ALTER TABLE ADD COLUMN`
4. **回滚计划**: 为破坏性变更准备回滚脚本

## 相关文档

- **领域层**: 参见 `patra-storage-domain/README.md` 了解聚合根和端口接口
- **应用层**: 参见 `patra-storage-app/README.md` 了解如何调用仓储
- **数据库设计**: 参见 `src/main/resources/db/migration/V0.1.0__init_storage_schema.sql`
