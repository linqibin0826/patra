# MyBatis-Plus 数据访问模式指南

> **目的**: 在六边形架构的基础设施层使用 MyBatis-Plus 实现数据持久化

## 🚀 快速开始

### 需要实现新的仓储？

```java
// 1. 创建 DO (Data Object)
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_plan", autoResultMap = true)
public class PlanDO extends BaseDO {
    @TableField("plan_key")
    private String planKey;

    @TableField("status_code")
    private String statusCode;

    // JSON 列使用 JsonNode + TypeHandler
    @TableField(value = "window_spec", typeHandler = JacksonTypeHandler.class)
    private JsonNode windowSpec;
}

// 2. 创建 Mapper 接口
public interface PlanMapper extends BaseMapper<PlanDO> {
    // 自定义查询方法
    PlanDO findByPlanKey(@Param("planKey") String planKey);
}

// 3. 创建 MapStruct 转换器
@Mapper(componentModel = "spring")
public interface PlanConverter {
    PlanDO toEntity(PlanAggregate aggregate);
    PlanAggregate toAggregate(PlanDO entity);
}

// 4. 实现仓储接口
@Repository
@RequiredArgsConstructor
public class PlanRepositoryImpl implements PlanRepository {
    private final PlanMapper mapper;
    private final PlanConverter converter;

    @Override
    public PlanAggregate save(PlanAggregate plan) {
        PlanDO entity = converter.toEntity(plan);

        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }

        return converter.toAggregate(entity);
    }

    @Override
    public Optional<PlanAggregate> findByPlanKey(String planKey) {
        PlanDO entity = mapper.findByPlanKey(planKey);
        return Optional.ofNullable(entity).map(converter::toAggregate);
    }
}
```

---

## 📊 决策矩阵

### 何时使用什么查询方式？

| 场景 | 推荐方式 | 原因 |
|------|---------|------|
| 简单查询 (1-2 条件) | LambdaQueryWrapper | 类型安全、简洁 |
| 复杂查询 (3+ 条件) | XML Mapper | 可读性、可维护性 |
| 动态条件查询 | LambdaQueryWrapper | 条件式查询支持 |
| 批量操作 | XML Mapper | 性能优化 |
| JOIN 查询 | XML Mapper | SQL 复杂度 |
| 单表 CRUD | BaseMapper 内置方法 | 开箱即用 |

### 查询方式选择决策树

```
查询复杂度如何？
  ├─ 简单 (1-2 条件) → LambdaQueryWrapper
  └─ 复杂 → 需要 JOIN？
             ├─ 是 → XML Mapper
             └─ 否 → 需要动态条件？
                     ├─ 是 → LambdaQueryWrapper
                     └─ 否 → XML Mapper (便于维护)
```

---

## 🎯 常见场景与模板

### 场景 1: 实现带 JSON 列的持久化

<details>
<summary>查看完整实现</summary>

```java
// DO 定义
@Data
@TableName(value = "ing_outbox_message", autoResultMap = true)
public class OutboxMessageDO extends BaseDO {

    @TableField("channel")
    private String channel;

    // ✅ JSON 列使用 JsonNode + JacksonTypeHandler
    @TableField(value = "payload_json", typeHandler = JacksonTypeHandler.class)
    private JsonNode payloadJson;

    @TableField(value = "headers_json", typeHandler = JacksonTypeHandler.class)
    private JsonNode headersJson;
}

// MapStruct 转换器
@Mapper(componentModel = "spring")
public interface OutboxMessageConverter {

    @Mapping(target = "payloadJson",
        expression = "java(jsonStringToNode(message.getPayloadJson()))")
    OutboxMessageDO toEntity(OutboxMessage message);

    default OutboxMessage toAggregate(OutboxMessageDO entity) {
        return OutboxMessage.builder()
            .id(entity.getId())
            .channel(entity.getChannel())
            .payloadJson(jsonNodeToString(entity.getPayloadJson()))
            .build();
    }

    // 辅助方法
    default JsonNode jsonStringToNode(String json) {
        if (json == null) return null;
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(json);
    }

    default String jsonNodeToString(JsonNode node) {
        if (node == null) return null;
        return node.toString();
    }
}
```

</details>

### 场景 2: 批量操作优化

<details>
<summary>查看批量插入和更新实现</summary>

```java
@Repository
@RequiredArgsConstructor
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxMessageMapper mapper;
    private final OutboxMessageConverter converter;

    // ❌ 错误: 逐条插入
    public void saveAllWrong(List<OutboxMessage> messages) {
        for (OutboxMessage msg : messages) {
            mapper.insert(converter.toEntity(msg));  // N 次数据库调用
        }
    }

    // ✅ 正确: 批量插入
    @Override
    public void saveAll(List<OutboxMessage> messages) {
        if (messages.isEmpty()) return;

        List<OutboxMessageDO> entities = messages.stream()
            .map(converter::toEntity)
            .collect(Collectors.toList());

        // 使用 MyBatis-Plus 批量插入 (100 条一批)
        new ServiceImpl<>(OutboxMessageMapper.class, OutboxMessageDO.class)
            .saveBatch(entities, 100);
    }
}
```

</details>

### 场景 3: 避免 N+1 查询

<details>
<summary>查看优化方案</summary>

```java
// ❌ 错误: N+1 查询
public void processPlansWrong() {
    List<PlanDO> plans = planMapper.selectList(wrapper);  // 1 次查询

    for (PlanDO plan : plans) {
        // ❌ 每个 plan 查询一次，共 N 次
        List<SliceDO> slices = sliceMapper.selectList(
            Wrappers.lambdaQuery(SliceDO.class)
                .eq(SliceDO::getPlanId, plan.getId())
        );
        // 处理...
    }
}

// ✅ 正确: 批量查询
public void processPlans() {
    // 1. 查询所有 plans
    List<PlanDO> plans = planMapper.selectList(wrapper);

    // 2. 批量查询所有 slices
    List<Long> planIds = plans.stream()
        .map(PlanDO::getId)
        .collect(Collectors.toList());

    List<SliceDO> slices = sliceMapper.selectList(
        Wrappers.lambdaQuery(SliceDO.class)
            .in(SliceDO::getPlanId, planIds)  // 1 次查询
    );

    // 3. 在内存中分组
    Map<Long, List<SliceDO>> slicesByPlan = slices.stream()
        .collect(Collectors.groupingBy(SliceDO::getPlanId));

    // 4. 处理
    for (PlanDO plan : plans) {
        List<SliceDO> planSlices = slicesByPlan.getOrDefault(
            plan.getId(), List.of()
        );
        // 处理...
    }
}
```

</details>

---

## 📋 速查表

### MyBatis-Plus 注解

| 注解 | 用途 | 示例 |
|------|------|------|
| @TableName | 表名映射 | `@TableName("ing_plan")` |
| @TableField | 字段映射 | `@TableField("plan_key")` |
| @TableField + typeHandler | JSON 列 | `@TableField(value="data", typeHandler=JacksonTypeHandler.class)` |
| @TableId | 主键 | `@TableId(type=IdType.AUTO)` |
| @Version | 乐观锁 | `@Version private Long version` |

### LambdaQueryWrapper 常用方法

| 方法 | 说明 | 示例 |
|------|------|------|
| eq | 等于 | `.eq(PlanDO::getPlanKey, "key")` |
| ne | 不等于 | `.ne(PlanDO::getStatus, "DELETED")` |
| in | IN 查询 | `.in(PlanDO::getId, ids)` |
| like | 模糊查询 | `.like(PlanDO::getName, "test")` |
| ge / le | 大于等于 / 小于等于 | `.ge(PlanDO::getCreatedAt, start)` |
| orderByDesc | 降序排序 | `.orderByDesc(PlanDO::getCreatedAt)` |
| select | 选择字段 | `.select(PlanDO::getId, PlanDO::getName)` |
| last | 添加 SQL 片段 | `.last("LIMIT 10")` |

### 基础设施层约束

| ✅ 应该 | ❌ 不应该 |
|---------|-----------|
| 实现领域层定义的端口接口 | 创建基础设施层专用接口 |
| 使用 MapStruct 转换 | 手动写转换代码 |
| 返回领域类型 | 返回 DO 给上层 |
| JSON 列用 JsonNode + TypeHandler | JSON 存为 String |
| 使用乐观锁 (@Version) | 依赖数据库锁 |
| 批量操作 (>10 条记录) | 逐条操作 |

---

## 🏗️ 核心组件详解

### 1. Data Object (DO)

**定义**: 直接映射数据库表的持久化对象，只在基础设施层使用。

**关键模式**:
```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_plan", autoResultMap = true)  // 表映射
public class PlanDO extends BaseDO {  // 继承 BaseDO (id, version, created_at, etc.)

    @TableField("plan_key")  // 字段映射
    private String planKey;

    // JSON 列处理
    @TableField(value = "window_spec", typeHandler = JacksonTypeHandler.class)
    private JsonNode windowSpec;
}
```

### 2. MyBatis-Plus Mapper

**定义**: 继承 BaseMapper 的接口，提供数据访问方法。

**关键模式**:
```java
public interface PlanMapper extends BaseMapper<PlanDO> {
    // ✅ 自定义查询方法
    PlanDO findByPlanKey(@Param("planKey") String planKey);

    // ✅ 批量操作
    int batchUpdateStatus(
        @Param("ids") List<Long> ids,
        @Param("status") String status
    );
}
```

### 3. MapStruct Converter

**定义**: 在 DO 和领域模型之间转换的接口。

**关键模式**:
```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = IGNORE)
public interface PlanConverter {

    // DO → 领域模型
    PlanDO toEntity(PlanAggregate aggregate);

    // 领域模型 → DO
    default PlanAggregate toAggregate(PlanDO entity) {
        // 使用领域工厂方法
        return PlanAggregate.restore(
            entity.getId(),
            // ... 其他字段
            entity.getVersion()
        );
    }

    // ✅ @AfterMapping 后处理
    @AfterMapping
    default void populateExtraFields(
        PlanAggregate aggregate,
        @MappingTarget PlanDO entity
    ) {
        // 自定义转换逻辑
    }
}
```

### 4. Repository Implementation

**定义**: 实现领域层定义的仓储端口接口。

**关键模式**:
```java
@Repository
@RequiredArgsConstructor
public class PlanRepositoryImpl implements PlanRepository {

    private final PlanMapper mapper;
    private final PlanConverter converter;

    @Override
    public PlanAggregate save(PlanAggregate plan) {
        // 1. 转换为 DO
        PlanDO entity = converter.toEntity(plan);

        // 2. 执行持久化
        if (entity.getId() == null) {
            mapper.insert(entity);  // 插入
        } else {
            mapper.updateById(entity);  // 更新 (带乐观锁)
        }

        // 3. 转换回领域模型
        return converter.toAggregate(entity);
    }

    @Override
    public Optional<PlanAggregate> findByPlanKey(String planKey) {
        PlanDO entity = mapper.findByPlanKey(planKey);
        // ✅ 转换并包装为 Optional
        return Optional.ofNullable(entity).map(converter::toAggregate);
    }
}
```

---

## 🔧 高级用法

### LambdaQueryWrapper 实战

<details>
<summary>查看常用查询模式</summary>

```java
// 1. 简单条件查询
LambdaQueryWrapper<PlanDO> wrapper = Wrappers.lambdaQuery();
wrapper.eq(PlanDO::getPlanKey, "key")
       .eq(PlanDO::getDeleted, 0);
PlanDO plan = mapper.selectOne(wrapper);

// 2. 多条件 AND 查询
wrapper = Wrappers.lambdaQuery();
wrapper.eq(PlanDO::getProvenanceCode, "PUBMED")
       .eq(PlanDO::getOperationCode, "HARVEST")
       .in(PlanDO::getStatusCode, List.of("READY", "RUNNING"));
List<PlanDO> plans = mapper.selectList(wrapper);

// 3. 动态条件查询 (条件为 null 时跳过)
wrapper = Wrappers.lambdaQuery();
wrapper.eq(code != null, PlanDO::getProvenanceCode, code)
       .ge(start != null, PlanDO::getCreatedAt, start)
       .le(end != null, PlanDO::getCreatedAt, end);
List<PlanDO> plans = mapper.selectList(wrapper);

// 4. 排序和分页
wrapper = Wrappers.lambdaQuery();
wrapper.eq(PlanDO::getProvenanceCode, "PUBMED")
       .orderByDesc(PlanDO::getCreatedAt)
       .last("LIMIT 10");
List<PlanDO> recent = mapper.selectList(wrapper);

// 5. 只查询部分字段
wrapper = Wrappers.lambdaQuery();
wrapper.select(PlanDO::getId, PlanDO::getPlanKey, PlanDO::getStatusCode)
       .eq(PlanDO::getProvenanceCode, "PUBMED");
List<PlanDO> lightweight = mapper.selectList(wrapper);

// 6. 统计查询
wrapper = Wrappers.lambdaQuery();
wrapper.eq(PlanDO::getStatusCode, "READY");
Long count = mapper.selectCount(wrapper);
```

</details>

### XML Mapper 实战

<details>
<summary>查看 XML Mapper 示例</summary>

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
  "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.patra.ingest.infra.persistence.mapper.PlanMapper">

  <!-- ResultMap 定义 -->
  <resultMap id="PlanResultMap" type="com.patra.ingest.infra.persistence.entity.PlanDO">
    <id column="id" property="id"/>
    <result column="plan_key" property="planKey"/>
  </resultMap>

  <!-- 简单查询 -->
  <select id="findByPlanKey" resultMap="PlanResultMap">
    SELECT * FROM ing_plan
    WHERE plan_key = #{planKey}
      AND deleted = 0
  </select>

  <!-- IN 查询 -->
  <select id="findByStatusCodes" resultMap="PlanResultMap">
    SELECT * FROM ing_plan
    WHERE provenance_code = #{provenanceCode}
      AND status_code IN
      <foreach collection="statusCodes" item="status" open="(" separator="," close=")">
        #{status}
      </foreach>
      AND deleted = 0
    ORDER BY created_at DESC
  </select>

  <!-- 批量更新 -->
  <update id="batchUpdateStatus">
    UPDATE ing_plan
    SET status_code = #{statusCode},
        updated_at = NOW(),
        version = version + 1
    WHERE id IN
    <foreach collection="ids" item="id" open="(" separator="," close=")">
      #{id}
    </foreach>
      AND deleted = 0
  </update>

  <!-- JSON 部分更新 -->
  <update id="updateRemarks">
    UPDATE ing_plan
    SET record_remarks = JSON_SET(
          COALESCE(record_remarks, '{}'),
          '$.updateReason',
          #{reason}
        ),
        version = version + 1
    WHERE id = #{id}
  </update>

</mapper>
```

</details>

---

## ⚠️ 常见问题与解决

### 问题 1: 乐观锁更新失败

**症状**: `updateById` 返回 0，更新未生效

**解决方案**:
```java
// ✅ 检查更新结果
PlanDO entity = mapper.selectById(id);
entity.setStatusCode("COMPLETED");

int affected = mapper.updateById(entity);
if (affected == 0) {
    throw new OptimisticLockException("记录已被其他事务修改");
}
```

### 问题 2: JSON 列反序列化失败

**症状**: `JsonNode` 为 null 或解析错误

**解决方案**:
```java
// ✅ 确保 autoResultMap = true
@TableName(value = "ing_plan", autoResultMap = true)  // 必需！

// ✅ 确保使用 TypeHandler
@TableField(value = "window_spec", typeHandler = JacksonTypeHandler.class)
private JsonNode windowSpec;
```

### 问题 3: 批量插入性能差

**症状**: 批量插入慢，数据库连接耗尽

**解决方案**:
```java
// ✅ 使用批量插入服务
IService<PlanDO> service = new ServiceImpl<>(PlanMapper.class, PlanDO.class);
service.saveBatch(entities, 100);  // 100 条一批

// 或使用 MyBatis-Plus 批处理
try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
    PlanMapper mapper = session.getMapper(PlanMapper.class);
    for (PlanDO entity : entities) {
        mapper.insert(entity);
    }
    session.commit();
}
```

---

## ✅ 最佳实践清单

### 设计原则
- [ ] DO 只在基础设施层使用，不暴露给上层
- [ ] 所有仓储实现领域层端口接口
- [ ] 使用 MapStruct 自动转换，避免手动代码
- [ ] JSON 列使用 JsonNode + JacksonTypeHandler

### 性能优化
- [ ] 避免 N+1 查询（使用批量查询）
- [ ] 批量操作超过 10 条使用 saveBatch
- [ ] 只查询需要的字段（使用 select）
- [ ] 为常用查询添加索引

### 数据一致性
- [ ] 使用 @Version 乐观锁
- [ ] 软删除查询始终过滤 deleted = 0
- [ ] 检查批量更新的影响行数

### 代码质量
- [ ] 复杂查询使用 XML Mapper
- [ ] DEBUG 级别记录 CRUD 日志
- [ ] 单元测试覆盖仓储实现

---

## 📚 相关文档

### 核心概念
- [architecture-overview.md](architecture-overview.md) - 六边形架构概览
- [domain-modeling-patterns.md](domain-modeling-patterns.md) - 领域建模模式

### 测试指南
- [testing-guide.md](testing-guide.md) - 完整测试策略
- [test-templates-infrastructure.md](test-templates-infrastructure.md) - 基础设施层测试模板