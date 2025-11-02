# patra-spring-boot-starter-mybatis

> MyBatis-Plus 自动配置,支持分页、乐观锁和性能优化。

## 📌 目的

为仓储层提供 **MyBatis-Plus** 配置:
- 分页插件
- 乐观锁插件(`@Version`)
- 批量操作优化
- `JsonNode` 字段的 JSON 类型处理器
- SQL 日志记录(仅开发/测试环境)

## 🔧 自动配置

### MyBatis-Plus 插件
- **PaginationInnerInterceptor**: 分页支持
- **OptimisticLockerInnerInterceptor**: 用于并发控制的 `@Version`
- **BlockAttackInnerInterceptor**: 防止全表更新/删除

### 类型处理器
- **JsonNodeTypeHandler**: 映射 `JsonNode` ↔ VARCHAR/JSON 列
- 自动注册自定义类型处理器

### 全局配置
- ID 生成: `AUTO`(数据库自增)
- 逻辑删除支持
- 字段策略: `NOT_NULL`

## 🔗 依赖

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-mybatis</artifactId>
</dependency>
```

包含: MyBatis-Plus、HikariCP、MapStruct

## 🚀 用法

### 仓储实现
```java
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {

    private final PlanMapper mapper;
    private final PlanConverter converter;

    @Override
    public PlanAggregate save(PlanAggregate aggregate) {
        IngestPlanDO entity = converter.toEntity(aggregate);

        if (aggregate.isTransient()) {
            mapper.insert(entity);
            aggregate.assignId(entity.getId());
        } else {
            mapper.updateById(entity);  // 通过 @Version 实现乐观锁
        }

        return aggregate;
    }
}
```

### 分页
```java
Page<IngestPlanDO> page = new Page<>(1, 20);
IPage<IngestPlanDO> result = mapper.selectPage(page, queryWrapper);
```

---

**最后更新**: 2025-01-12
