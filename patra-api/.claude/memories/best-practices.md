# 开发最佳实践

## 推荐做法

1. Domain 层保持纯 Java，禁止依赖 Spring、MyBatis 等框架注解和类
2. Application 层统一管理事务边界，使用 `@Transactional` 注解，禁止在其他层管理事务
3. 使用 MapStruct 进行对象转换（DO/DTO/Entity 之间），禁止手动编写转换代码
4. 通过 Port 和 Repository 接口定义依赖，Infrastructure 层提供实现，实现依赖倒置
5. **聚合即整体**：聚合内任何子实体或值对象的变更，在逻辑上等同于聚合根状态变更。保存时始终通过 `Repository.save(aggregateRoot)` 持久化整个聚合，禁止单独保存子实体
6. **聚合根版本锁**：修改聚合内任何数据（无论主表还是子表），必须递增聚合根主表的 `version` 字段。该版本号作为整个聚合的"总锁"，通过乐观锁防止并发修改破坏一致性

## 禁止行为

1. 禁止在 Domain 层使用 Spring 注解（如 `@Service`、`@Autowired`、`@Value` 等）
2. 禁止跨层直接调用（如 Controller 直接调用 Repository，必须经过 Application 层编排）
3. 禁止在 Controller 层处理业务逻辑，Controller 仅负责参数校验、协议转换和调用 Application 层
4. 禁止硬编码配置值，使用 `@ConfigurationProperties` 或 `@Value` 注入配置