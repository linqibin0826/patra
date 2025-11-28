# MyBatis-Plus 使用规范

## 基础规范

1. 禁止在 Mapper 接口中使用 `@Select` 等注解编写 SQL，简单查询使用 `LambdaQueryWrapper`，复杂查询使用 XML
2. 所有 DO 必须继承 `BaseDO`（包含雪花 ID 主键和 10 个审计字段）
3. 所有 Mapper 必须继承 `PatraBaseMapper`（而非 `BaseMapper`），获得 `insertBatchSomeColumn` 批量插入能力

## 批量插入规范

### 强制要求

1. **数据量 > 100 条必须使用批量插入**，禁止循环调用 `insert()` 或 `save()`
2. **禁止使用 `ServiceImpl.saveBatch()`**，底层是循环 INSERT，性能差
3. **禁止继承 `ServiceImpl`**，RepositoryAdapter 应直接注入 Mapper
4. 使用 `PatraBaseMapper.insertBatchSomeColumn()` 或 `BatchInsertHelper.batchInsert()` 进行批量插入

### 使用方式

```java
// 推荐：使用 BatchInsertHelper 自动分片（固定 1000 条/批）
BatchInsertHelper.batchInsert(dataList, mapper::insertBatchSomeColumn);

// 自定义批次大小（通过方法参数调整）
BatchInsertHelper.batchInsert(dataList, 500, mapper::insertBatchSomeColumn);

// 小数据量可直接调用（确定 < 1000 条时）
mapper.insertBatchSomeColumn(dataList);
```

### 分片策略

默认批次大小固定为 1000 条/批，可通过 `batchInsert()` 方法参数调整。

| 数据量 | 批次大小 | 说明 |
|--------|----------|------|
| < 1000 | 不分片 | 直接调用 `insertBatchSomeColumn` |
| 1K-10K | 1000/批 | 使用 `BatchInsertHelper` 自动分片 |
| 10K-100K | 5000/批 | 需确保 `max_allowed_packet >= 64MB` |

### 审计字段

- `insertBatchSomeColumn` 会自动触发 `AuditMetaObjectHandler.insertFill()` 填充审计字段
- 无需手动设置 `createdAt`、`updatedAt`、`version`、`deleted` 等字段

### ArchUnit 静态检查

项目配置了 `MyBatisArchRules.noServiceImplInRepository()` 规则：
- 禁止 RepositoryAdapter 继承 ServiceImpl
- 违反规则会在架构测试中失败

### 正确示例

```java
// ✅ 正确：直接注入 Mapper，使用 insertBatchSomeColumn
@Repository
@RequiredArgsConstructor
public class MeshQualifierRepositoryAdapter implements MeshQualifierRepository {

    private final MeshQualifierMapper mapper;
    private final MeshQualifierConverter converter;

    @Override
    public void saveBatch(List<MeshQualifierAggregate> qualifiers) {
        List<MeshQualifierDO> dataObjects = qualifiers.stream()
            .map(converter::toDataObject)
            .toList();

        BatchInsertHelper.batchInsert(dataObjects, mapper::insertBatchSomeColumn);
    }
}
```

### 错误示例

```java
// ❌ 错误：继承 ServiceImpl，使用 saveBatch()
@Repository
public class MeshQualifierRepositoryAdapter
    extends ServiceImpl<MeshQualifierMapper, MeshQualifierDO>
    implements MeshQualifierRepository {

    @Override
    public void saveBatch(List<MeshQualifierAggregate> qualifiers) {
        super.saveBatch(dataObjects);  // 底层是循环 INSERT，性能差
    }
}
```
