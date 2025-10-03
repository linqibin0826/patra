# patra-spring-boot-starter-mybatis

提供 Papertrace 约定的 MyBatis-Plus 基础配置、插件链与错误映射。

## 1. 模块定位
- **服务/组件作用**：标准化分页、乐观锁、全表防御、审计字段填充以及数据库异常 → 平台错误码映射
- **主要消费者**：`patra-*-infra` 层、需要数据库访问的微服务
- **架构边界**：Starter 只提供配置与拦截器；业务 SQL、Mapper 定义仍由各模块维护

## 2. 核心能力
- **插件链**：分页、乐观锁、防全表更新删除、自动填充器
- **错误映射**：`DataLayerErrorMappingContributor` 将驱动异常转换为标准错误码
- **基础实体**：`BaseDO` 审计字段、逻辑删除、乐观锁版本号
- **JSON 字段处理**：提供 `JsonNode`、`Map<String,Object>` TypeHandler
- **Mapper 扫描约定**：自动扫描 `infra` 包下 Mapper

本模块 README 即为权威文档；如需对比其它 Starter，请参考各 Starter 模块 README（`patra-spring-boot-starter-*`、`patra-spring-cloud-starter-feign`）。

## 3. 分层结构与依赖
- 核心包：`config`（自动配置）、`error`、`handler`
- 依赖：`patra-common`、MyBatis-Plus、Spring Boot DataSource、Jackson

## 4. 运行与配置
- Maven 引入：
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-mybatis</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- 自动配置前提：项目已声明数据源（Hikari 等）并启用 MyBatis Mapper 扫描
- 可调配置（摘录）：分页最大页、慢查询日志、自动填充开关（详见深度文档）

## 5. 观测与运维
- DB 错误通过核心 Starter 输出统一错误码，可在 dashboard 上按 code 统计
- 建议启用 MyBatis-Plus SQL 日志与慢查询阈值（配合 Micrometer）
- 警惕全表操作被插件拦截，必要时在 Migration 场景禁用

## 6. 测试策略
- 使用 H2 / Testcontainers 验证 Mapper 与插件行为
- 覆盖自动填充、乐观锁、全表更新防御场景
- 模拟唯一键冲突、外键约束等异常，确认错误码映射

## 7. Roadmap 与风险
| 项目 | 状态 | 风险/备注 |
|------|------|-----------|
| 插件可配置链 | 规划 | 允许服务按需禁用个别插件，需处理顺序依赖 |
| SQL 观测指标 | 规划 | 集成 Micrometer 记录执行耗时/行数 |
| JSON Schema 校验 | 规划 | 对 JSON 字段写入前进行可选校验 |

风险：自动填充字段与业务逻辑冲突、TypeHandler 未注册导致序列化失败、防全表拦截误伤批处理。

## 8. 参考资料
- 其他 Starter：`patra-spring-boot-starter-core/README.md`、`patra-spring-boot-starter-web/README.md`、`patra-spring-cloud-starter-feign/README.md`、`patra-spring-boot-starter-expr/README.md`
- 错误规范：`docs/standards/platform-error-handling.md`
- Registry/ingest Infra 实现：参见各模块 `infra` 包
