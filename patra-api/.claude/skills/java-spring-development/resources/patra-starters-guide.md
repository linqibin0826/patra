# Patra 自定义 Starter 完整使用指南

## 🚨 强制使用规范

**Patra 项目必须使用自定义 Starter，禁止直接引入原始依赖。**

## Starter 与模块层对应关系

| 模块层 | 必须使用的 Starter | Maven 坐标 |
|-------|------------------|-----------|
| **adapter 层** | Web Starter | `com.patra:patra-spring-boot-starter-web` |
| **infra 层（数据库）** | MyBatis Starter | `com.patra:patra-spring-boot-starter-mybatis` |
| **infra 层（Feign）** | Feign Starter | `com.patra:patra-spring-cloud-starter-feign` |
| **infra 层（对象存储）** | Object Storage Starter | `com.patra:patra-spring-boot-starter-object-storage` |
| **所有层（除 domain）** | Core Starter | `com.patra:patra-spring-boot-starter-core` |

## 1. patra-spring-boot-starter-web

### 适用场景
`patra-{service}-adapter` 模块

### 提供功能
- REST Controller 支持（Spring MVC）
- 统一异常处理（GlobalExceptionHandler）
- 参数校验（Validation）
- 请求响应日志
- CORS 配置
- API 文档（Swagger/OpenAPI）

### Maven 依赖
```xml
<!-- patra-xxx-adapter/pom.xml -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-web</artifactId>
</dependency>
```

### 使用示例
```java
// ✅ 正确：在 adapter 模块中使用
@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {
    // Web Starter 自动配置了异常处理、参数校验等
}
```

## 2. patra-spring-boot-starter-mybatis

### 适用场景
`patra-{service}-infra` 模块（涉及数据库时）

### 提供功能
- MyBatis-Plus 核心功能
- 分页插件
- 乐观锁插件
- 自动填充（创建时间、更新时间等）
- 雪花 ID 生成器
- 审计字段支持（BaseDO）

### Maven 依赖
```xml
<!-- patra-xxx-infra/pom.xml -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-mybatis</artifactId>
</dependency>
```

### BaseDO 继承规范
```java
// ✅ 正确：所有 DO 必须继承 BaseDO
@TableName("t_resource")
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceDO extends BaseDO {
    // BaseDO 提供：id (雪花ID)、10个审计字段

    @TableField("resource_name")
    private String name;

    @TableField("resource_type")
    private String type;

    @TableField("resource_status")
    private Integer status;
}
```

### Mapper 使用规范
```java
// ✅ 正确：使用 MyBatis-Plus BaseMapper
@Mapper
public interface ResourceMapper extends BaseMapper<ResourceDO> {
    // 自动继承 CRUD 方法
}

// ✅ 正确：复杂查询使用 XML
// resources/mapper/ResourceMapper.xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.patra.xxx.infra.mapper.ResourceMapper">
    <select id="selectByComplexCondition" resultType="com.patra.xxx.infra.dataobject.ResourceDO">
        SELECT * FROM t_resource
        WHERE status = #{status}
        <if test="keyword != null">
            AND name LIKE CONCAT('%', #{keyword}, '%')
        </if>
    </select>
</mapper>
```

### Repository 实现模式
```java
@Repository
@RequiredArgsConstructor
public class ResourceRepositoryImpl implements ResourceRepository {
    private final ResourceMapper mapper;
    private final ResourceConverter converter;

    @Override
    public void save(Resource resource) {
        var resourceDO = converter.toDO(resource);
        mapper.insert(resourceDO);
        resource.setId(resourceDO.getId());
    }

    @Override
    public Optional<Resource> findById(Long id) {
        var wrapper = new LambdaQueryWrapper<ResourceDO>()
            .eq(ResourceDO::getId, id)
            .eq(ResourceDO::getDeleted, false);

        return Optional.ofNullable(mapper.selectOne(wrapper))
            .map(converter::toDomain);
    }

    @Override
    public Page<Resource> search(ResourceQuery query, Pageable pageable) {
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<ResourceDO>(
            pageable.getPageNumber() + 1,
            pageable.getPageSize()
        );

        var wrapper = new LambdaQueryWrapper<ResourceDO>()
            .like(StringUtils.isNotBlank(query.getKeyword()),
                  ResourceDO::getName, query.getKeyword())
            .eq(query.getType() != null,
                ResourceDO::getType, query.getType())
            .orderByDesc(ResourceDO::getCreatedAt);

        var result = mapper.selectPage(page, wrapper);

        return new PageImpl<>(
            result.getRecords().stream()
                .map(converter::toDomain)
                .toList(),
            pageable,
            result.getTotal()
        );
    }
}
```

### 注意事项
- ✅ **所有 DO 必须继承 `BaseDO`**
- ✅ **表没有外键关联，id 使用雪花 ID**
- ❌ **不要在 Mapper.java 中编写 SQL**，简单查询使用 `LambdaQueryWrapper`，复杂查询使用 XML

## 3. patra-spring-cloud-starter-feign

### 适用场景
`patra-{service}-infra` 模块（调用方和提供方都需要）

### 提供功能
- OpenFeign 客户端
- 负载均衡（Nacos Discovery）
- 请求重试
- 熔断降级（Resilience4j）
- 请求日志
- 统一错误处理

### Maven 依赖
```xml
<!-- patra-xxx-infra/pom.xml (调用方) -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-cloud-starter-feign</artifactId>
</dependency>

<!-- patra-xxx-api/pom.xml (提供方也需要，用于定义 FeignClient 接口) -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-cloud-starter-feign</artifactId>
    <scope>provided</scope>
</dependency>
```

### FeignClient 定义规范
```java
// ✅ 正确：在 -api 模块定义接口
package com.patra.registry.api.client;

@FeignClient(name = "patra-registry", path = "/api/v1/metadata")
public interface MetadataServiceClient {

    @GetMapping("/{id}")
    MetadataDTO getById(@PathVariable Long id);

    @GetMapping("/list")
    List<MetadataDTO> list(@RequestParam("type") String type);

    @PostMapping
    MetadataDTO create(@RequestBody CreateMetadataRequest request);
}
```

### Adapter 实现模式
```java
// ✅ 正确：在 -infra 模块实现 Port
@Component
@RequiredArgsConstructor
public class MetadataServiceAdapter implements MetadataPort {
    private final MetadataServiceClient client;
    private final MetadataConverter converter;

    @Override
    public Metadata fetchById(Long id) {
        try {
            var dto = client.getById(id);
            return converter.toDomain(dto);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("元数据不存在: " + id);
        } catch (FeignException e) {
            log.error("调用元数据服务失败", e);
            throw new ExternalServiceException("元数据服务不可用");
        }
    }

    @Override
    public List<Metadata> fetchByType(String type) {
        var dtos = client.list(type);
        return dtos.stream()
            .map(converter::toDomain)
            .toList();
    }
}
```

### 配置示例
```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 5000
        readTimeout: 10000
        loggerLevel: BASIC
  circuitbreaker:
    enabled: true

resilience4j:
  circuitbreaker:
    instances:
      patra-registry:
        slidingWindowSize: 100
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
```

## 4. patra-spring-boot-starter-object-storage

### 适用场景
`patra-{service}-infra` 模块（需要对象存储时）

### 提供功能
- 阿里云 OSS 支持
- MinIO 支持
- 统一的对象存储接口
- 自动配置

### Maven 依赖
```xml
<!-- patra-xxx-infra/pom.xml -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-object-storage</artifactId>
</dependency>
```

### 使用示例
```java
// ✅ 正确：使用统一接口
@Component
@RequiredArgsConstructor
public class FileStorageAdapter implements FileStoragePort {
    private final ObjectStorageService storageService;

    @Override
    public String upload(FileData file) {
        var objectKey = generateObjectKey(file);
        return storageService.upload(
            file.getInputStream(),
            objectKey,
            file.getContentType()
        );
    }

    @Override
    public byte[] download(String objectKey) {
        return storageService.download(objectKey);
    }

    @Override
    public void delete(String objectKey) {
        storageService.delete(objectKey);
    }

    private String generateObjectKey(FileData file) {
        var date = LocalDate.now();
        return String.format("%d/%02d/%02d/%s_%s",
            date.getYear(),
            date.getMonthValue(),
            date.getDayOfMonth(),
            UUID.randomUUID().toString(),
            file.getOriginalName()
        );
    }
}
```

### 配置示例
```yaml
object-storage:
  type: oss  # oss 或 minio
  oss:
    endpoint: oss-cn-hangzhou.aliyuncs.com
    access-key-id: ${OSS_ACCESS_KEY_ID}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET}
    bucket-name: patra-files
  minio:
    endpoint: http://localhost:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket-name: patra-files
```

## 5. patra-spring-boot-starter-core

### 适用场景
**所有模块（除了 domain 层）**

### 提供功能
- 公共工具类（Hutool 增强）
- 基础数据对象（BaseDO、BaseDTO）
- 通用异常定义
- 日期时间工具
- JSON 序列化配置
- ID 生成器

### Maven 依赖
```xml
<!-- patra-xxx-adapter/pom.xml -->
<!-- patra-xxx-app/pom.xml -->
<!-- patra-xxx-infra/pom.xml -->
<!-- patra-xxx-api/pom.xml -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-core</artifactId>
</dependency>

<!-- ❌ domain 模块不能使用 -->
```

### 使用示例
```java
// ✅ 正确：在 app/adapter/infra 中使用
import com.patra.common.util.DateUtils;
import com.patra.common.exception.BusinessException;
import com.patra.common.util.IdGenerator;
import com.patra.common.util.JsonUtils;

@Service
public class SomeService {
    public void process() {
        // 使用日期工具
        var now = DateUtils.now();
        var formattedDate = DateUtils.format(now, "yyyy-MM-dd");

        // 生成唯一 ID
        var uniqueId = IdGenerator.generateId();

        // JSON 序列化
        var json = JsonUtils.toJson(someObject);
        var object = JsonUtils.fromJson(json, SomeClass.class);

        // 抛出业务异常
        if (!isValid) {
            throw new BusinessException("ERROR_CODE", "错误信息");
        }
    }
}

// ❌ 错误：domain 层不能使用
// domain 层必须保持纯 Java，不能依赖任何框架
```

## 依赖声明检查清单

开发新功能时，按以下检查清单添加依赖：

```
[ ] 1. 是 adapter 层吗？
       → 是 → 添加 patra-spring-boot-starter-web

[ ] 2. 是 infra 层且需要数据库吗？
       → 是 → 添加 patra-spring-boot-starter-mybatis
       → 确认 DO 继承了 BaseDO

[ ] 3. 需要调用其他服务吗？
       → 是 → 添加 patra-spring-cloud-starter-feign
       → 在 -api 模块定义 FeignClient
       → 在 -infra 模块实现 Adapter

[ ] 4. 需要对象存储吗？
       → 是 → 添加 patra-spring-boot-starter-object-storage

[ ] 5. 是 domain 层吗？
       → 是 → ❌ 不能添加任何 Spring Boot Starter
       → 否 → ✅ 添加 patra-spring-boot-starter-core
```

## 常见错误与解决

### 错误 1：直接使用原始依赖
```xml
<!-- ❌ 错误 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
</dependency>

<!-- ✅ 正确 -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-mybatis</artifactId>
</dependency>
```

### 错误 2：domain 层引入 Spring 依赖
```xml
<!-- ❌ 错误：domain 模块的 pom.xml -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-core</artifactId>
</dependency>

<!-- ✅ 正确：domain 层只能使用纯 Java -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>
```

### 错误 3：DO 没有继承 BaseDO
```java
// ❌ 错误
@TableName("t_resource")
@Data
public class ResourceDO {
    private Long id;
    private String name;
    // 手动添加审计字段
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// ✅ 正确
@TableName("t_resource")
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceDO extends BaseDO {
    // BaseDO 已提供 id 和审计字段
    private String name;
}
```

### 错误 4：在 Mapper 接口中写 SQL
```java
// ❌ 错误
@Mapper
public interface ResourceMapper extends BaseMapper<ResourceDO> {
    @Select("SELECT * FROM t_resource WHERE name = #{name}")
    ResourceDO findByName(String name);
}

// ✅ 正确：简单查询使用 LambdaQueryWrapper
public Optional<Resource> findByName(String name) {
    var wrapper = new LambdaQueryWrapper<ResourceDO>()
        .eq(ResourceDO::getName, name);
    return Optional.ofNullable(mapper.selectOne(wrapper))
        .map(converter::toDomain);
}

// ✅ 正确：复杂查询使用 XML
// 在 resources/mapper/ResourceMapper.xml 中定义
```