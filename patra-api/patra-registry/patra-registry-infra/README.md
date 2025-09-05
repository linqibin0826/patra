# `patra-registry-infra`

> `registry` 是系统的唯一真实数据来源（Single Source of Truth, SSOT）。`infra`
> 负责将领域对象可靠、可追溯地落库，并通过仓储实现对上提供一致的访问能力。

## 1. 职责边界

* 实现 `domain.port.*` 的仓储与相关技术细节；对上只暴露**领域端口**，不泄露持久化细节。
* 完成 **聚合 ↔ 数据对象（DO）** 的转换与持久化；不承载任何业务规则与业务决策。
* 维护数据一致性与审计信息，确保 `registry` 的权威性与可追溯。

## 2. 依赖与禁区

* ✅ 允许依赖：**domain**、`patra-starter-mybatis-plus`。
* ⛔ 禁止依赖：`app`、`adapter`、`api`、Web 框架、平台网关 SDK、搜索客户端等。

## 3. 目录结构

```
persistence/
  entity/            // 表结构映射的数据对象
    xxxDO.java
    yyyDO.java
  mapper/            // MyBatis Mapper 接口
    XxxMapper.java
    YyyMapper.java
  repository/        // 领域仓储实现（实现 domain.port.*）
    XxxRepositoryImpl.java

mapstruct/           // DO ↔ 聚合/值对象 转换器
  XxxDoConverter.java
  YyyDoConverter.java
```

## 4. 数据建模与命名

* **DO 命名**：`XxxDO.java`（Data Object）。
* **表命名**：使用前缀 `reg_`；列名使用 `snake_case`。
* **主键策略**：统一的全局主键（如雪花/UUID），由 `infra` 负责生成，不耦合数据库自增。
* **审计字段**：`patra-starter-mybatis-plus`引入了 `BaseDO.java`，只需继承即可。

## 5. 映射与转换

* 所有 DO 与聚合/值对象的映射集中在 `mapstruct/*DoConverter`；仓储实现只调用转换器，不手写字段拷贝。
* 字段新增/重命名时只需调整转换器与 DO，避免上层受影响。

## 6. 仓储实现规范

* **以聚合为单位**提供 `load/save` 语义（如 `findById`、`findByCode`），不向上暴露行级别 CRUD。
* **事务边界**：以应用层为主；仓储内部可使用最小粒度事务以保证原子写入。
* **并发控制**：使用 `version` 乐观锁(BaseDO.java包含)；冲突统一转译为可识别的上层异常（如“已变更”“唯一冲突”）。
* **批处理与分页**：starter中已集成 MyBatis-Plus 分页插件，支持分页查询与批量插入/更新。
* **只处理持久化技术**：仓储不做权限、策略与业务校验。

## 7. 一致性、缓存与事件

* **SSOT 原则**：以数据库为准；缓存（若有）只是派生视图。
* 缓存采用 **read-through** 与**事件驱动失效**：上层在“配置变更”等集成事件后主动失效缓存。
* 如需“写入即发布事件”，推荐 **Outbox 模式**（可选）：写库与写 outbox 同一事务，由异步转发器投递到 MQ。

## 8. 错误处理与观测性

* 异常分类与转译：将数据库异常统一转换为受控异常并打标签（`DB_UNAVAILABLE`、`CONSTRAINT_VIOLATION`、`DEADLOCK_RETRY`）。
* 日志与指标：记录 CRUD 次数、慢查询、重试次数、乐观锁冲突率、批处理大小等，便于容量规划与性能治理。

## 9. 配置与类型处理

* 在 `config/` 统一维护：starter中已集成数据源与 MyBatis-Plus 配置。如需自定义可在该目录下新增。

## 10. 迁移与变更管理

* 使用迁移工具（如 Flyway/Liquibase）管理表结构与基础数据；脚本与版本一一对应，可回滚。

## 11. 测试策略

* **仓储单测**：覆盖 DO↔聚合映射、典型 CRUD 与并发场景（乐观锁冲突）。
* **集成测试**：使用容器化真实数据库进行端到端验证；避免仅依赖内存数据库导致 SQL 行为偏差。

## 12. 性能与容量

* 设定分页上限与合理的批大小；避免 N+1 查询。
* 监控慢查询并及时加索引或改写 SQL；按访问模式做冷热分层（必要时采用读写分离，但不改变 SSOT 原则）。

> `registry` = **单一真实数据来源**。`infra` 只做把关的“管道工”，把业务语义保护在 `domain`，把实现细节封装在这里。
