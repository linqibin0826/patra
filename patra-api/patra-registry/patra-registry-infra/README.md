# `patra-registry-infra`

> `registry` 是系统的唯一真实数据来源（Single Source of Truth, SSOT）。`infra` 负责将领域对象可靠、可追溯地落库，并通过仓储实现对上提供一致的访问能力。&#x20;

## 1. 职责边界

* 实现 `domain.port.*` 的仓储与相关技术细节；对上只暴露**领域端口**，不泄露持久化细节。&#x20;
* 完成 **聚合 ↔ 数据对象（DO）** 的转换与持久化；不承载任何业务规则与业务决策。&#x20;
* 维护数据一致性与审计信息，确保 `registry` 的权威性与可追溯。&#x20;

## 2. 依赖与禁区

* ✅ 允许依赖：**domain**、`patra-spring-boot-starter-mybatis`、`patra-spring-boot-starter-core`。
* ⛔ 禁止依赖：`app`、`adapter`、`api`、Web 框架、平台网关 SDK、搜索客户端等。&#x20;

## 3. 目录结构

```
persistence/
  entity/            // 表结构映射的数据对象
    XxxDO.java
    YyyDO.java
  mapper/            // MyBatis-Plus Mapper 接口（见第 6 节约定）
    XxxMapper.java
    YyyMapper.java
  repository/        // 领域仓储实现（实现 domain.port.*）
    XxxRepositoryImpl.java

mapstruct/           // DO ↔ 聚合/值对象 转换器（见第 7 节约定）
  XxxDoConverter.java
  YyyDoConverter.java
```



## 4. 数据建模与命名

* **DO 命名**：`XxxDO.java`（Data Object）。
* **表命名**：使用前缀 `reg_`；列名使用 `snake_case`。
* **主键策略**：统一的全局主键（如雪花/UUID），由 `infra` 负责生成，不耦合数据库自增。
* **审计字段**：继承 `BaseDO`（在 starter 中提供）获取通用审计与乐观锁字段。&#x20;

### 4.1 DO 中的“数据库枚举字段”规范

* **必须直接使用 `domain` 中定义的领域枚举类型**，禁止在 `infra` 再定义同名/等价枚举。
* 领域枚举如代表库中字段，需在 `domain` 实现 `CodeEnum<C>`（`C` 与库中列类型对应，如 `INT`/`VARCHAR`）。
* **无需在本模块注册 `CodeEnum` 的 TypeHandler** —— `patra-spring-boot-starter-mybatis-plus` 已统一自动注册并完成 `code ↔ enum` 的双向映射。
* DO 字段命名保持语义化（如 `status`/`type`），不要使用“魔法数/字符串”。;

### 4.2 DO 中的 JSON 字段规范

* 数据库中 `json` 列，**在 DO 中统一声明为 `com.fasterxml.jackson.databind.JsonNode`**。
* **无需在字段上标注 `@TableField(typeHandler = …)`** —— 已在 Starter 中全局注册 `JsonNode` 的 TypeHandler。
* 映射示例：

  ```java
  // DO 示例（无需额外 TypeHandler 注解）
  private JsonNode extConfig;
  ```

## 5. 映射与转换

* 所有 DO 与聚合/值对象的映射集中在 `mapstruct/*DoConverter`；仓储实现只调用转换器，不手写字段拷贝。
* 字段新增/重命名时只需调整转换器与 DO，避免上层受影响。&#x20;

## 6. Mapper 约定

* **所有 `xxxMapper.java` 都必须 `extends BaseMapper<XxxDO>`**，复用 MyBatis-Plus 的通用 CRUD 能力。
* **禁止在 `xxxMapper.java` 上使用 `@Mapper` 注解** —— `patra-spring-boot-starter-mybatis` 已通过包扫描自动注册，无需重复标注。
* **简单 SQL 不写 XML**：常见单表 CRUD、条件查询，直接在 **repository 实现** 中使用 `baseMapper` 与 `LambdaQueryWrapper`/`Wrapper` 组合完成；**不为仅一次使用的简单语句新建 XML/方法**。
* **确有必要才写 XML**：仅当出现复杂联表/分页优化/特定数据库特性（如窗口函数）且难以通过 Wrapper 表达时，再编写 XML 并在 Mapper 中声明方法。
* **命名规范**：自定义方法名体现语义（如 `listBy...`、`update...If...`），避免 `select1/2` 之类无语义命名。&#x20;

## 7. Converter（MapStruct）约定

* **保持简洁**：每个聚合对应**一个** `*DoConverter` 为宜，方法仅保留必要的 DO↔领域对象/值对象转换。
* **注解最小化**：`@Mapper` 仅配置必要项（如 `componentModel`），不要在无关类到处铺设；公共映射逻辑可通过 `uses = {CommonConverters.class}` 引入。
* **职责单一**：Converter 只做**字段映射**与**轻量规则（如空值默认）**，**不写业务**、不做数据库访问。
* **命名规范**：`XxxDoConverter`，便于识别“DO ↔ 领域对象”。&#x20;

## 8. 仓储实现规范

* **以聚合为单位**提供 `load/save` 语义（如 `findById`、`findByCode`），不向上暴露行级别 CRUD。
* **事务边界**：以应用层为主；仓储内部可使用最小粒度事务以保证原子写入。
* **并发控制**：使用 `version` 乐观锁（`BaseDO` 已包含）；冲突统一转译为可识别的上层异常（如“已变更”“唯一冲突”）。
* **批处理与分页**：集成 MyBatis-Plus 分页与批量能力。
* **只处理持久化技术**：仓储不做权限、策略与业务校验。&#x20;

## 9. 一致性、缓存与事件

* **SSOT 原则**：以数据库为准；缓存（若有）只是派生视图。
* 缓存采用 **read-through** 与 **事件驱动失效**；如需“写入即发布事件”，推荐 **Outbox 模式**。&#x20;

## 10. 错误处理与观测性

* 异常分类与转译：将数据库异常统一转换为受控异常并打标签（`DB_UNAVAILABLE`、`CONSTRAINT_VIOLATION`、`DEADLOCK_RETRY`）。
* 记录 CRUD 次数、慢查询、重试、乐观锁冲突率、批处理大小等指标。&#x20;

## 11. 配置与类型处理

* 在 `config/` 统一维护数据源与 MyBatis-Plus 配置。
* **枚举与 JSON 类型处理统一约定**：

    * **`CodeEnum` 枚举映射**：**无需手工注册**，`patra-spring-boot-starter-mybatis` 已自动注册相应 TypeHandler/全局映射策略，确保 `code ↔ enum` 的双向转换。
    * **`JsonNode` 映射**：**无需在字段上声明 `typeHandler`**，Starter 已全局注册 `JsonNode` TypeHandler，DO 直接使用 `JsonNode` 类型即可。
    * 列类型与 `CodeEnum<C>` 的 `C` 保持一致（如 `INT`/`VARCHAR`），避免隐式转换。
    * 严禁在各个 Mapper/Converter 内部“就地 if/else”手动转换。;

## 12. 迁移与变更管理

* SQL 目录：`resources/db/migration`；采用 Flyway/Liquibase 管理版本与回滚。&#x20;

## 13. 测试策略

* **仓储单测**：覆盖 DO↔聚合映射、典型 CRUD 与并发（乐观锁）场景。
* **集成测试**：容器化真实数据库做端到端验证，避免仅用内存库。&#x20;

## 14. 性能与容量

* 设定分页上限与合理批大小；避免 N+1 查询。
* 监控慢查询并加索引/改写 SQL；必要时冷热分层或读写分离（不改变 SSOT 原则）.;
