---
inclusion: always
---

# Agent工作规则（基于AGENTS.md）

## 工作原则

### Do（必须做）
- 遵循**六边形架构 + DDD**，严守依赖方向，不越层、不泄漏实现细节
- 信息不足时**先提问再动手**，优先复用已有能力（`patra-*` starters、`patra-common`、Hutool）
- 输出**小步变更/小Diff**，为关键决策写下**假设与权衡**
- 任何"数据落地链路"变更（采集→解析/清洗→入库）必须可回放、可幂等、可观测

### Don't（禁止做）
- 不在`domain`层引入任何框架依赖
- 不在代码中硬编码密钥/连接串/可变配置（统一走**Nacos**/环境变量）
- 不进行**破坏性数据操作**（删库、ES重建索引、MQ主题变更等）而不经审批

## 项目背景

Papertrace是医学文献数据平台，目标：
1. 采集10+医学文献源（PubMed、EPMC等）
2. 以SSOT（单一可信源patra-registry）管理配置/词典/元数据
3. 对原始文献数据解析、清洗与标准化
4. 后续提供搜索与智能分析

当前重点：保证数据"可靠落地"（采集→解析/清洗→入库）

## 依赖方向（严格遵守）

```
adapter → app + api (+ web starters)
app → domain + patra-common + core starter
infra → domain + mybatis starter + core starter
domain → 仅 patra-common（禁止引入Spring/框架）
api → 不依赖框架（对外暴露）
```

## 开发命令优先级

优先单模块、按文件粒度命令；全仓级命令仅在明确要求时使用。

```bash
# 编译（单模块）
mvn -q -DskipTests compile

# 测试（单模块）
mvn -q test

# 检查+测试（快速回归）
mvn -q -DskipITs test

# 全仓编译（仅必要时）
./mvnw clean compile
# 或
mvn -q -T 1C -DskipTests compile

# 打包
mvn clean package -DskipTests
```

## 代码约定

### DO/枚举/JSON
- 数据库存JSON字段在DO中统一使用Jackson `JsonNode`表示

### POJO形态
- 不可变/值对象优先使用`record`
- 需要可变时使用Lombok + class
- record内不使用Lombok

### Lombok使用
- 不手写样板代码（getter/setter/toString/equals/hashCode）
- 使用`@Data`或组合注解
- 在class中合理选用，避免过度注解

### 工具复用
- 不重复造轮子：优先使用Hutool与patra-common/starters提供的工具
- 新增前先检索现有能力

## 数据处理与一致性

### 幂等性
- 采集/解析/清洗流程可重入
- 为关键步骤设计幂等键/去重策略

### 事务
- 在app层编排
- 跨聚合用事件最终一致
- domain不引入事务框架

### 仓储
- 按聚合整体持久化
- infra使用MyBatis-Plus
- 实体转换由MapStruct完成

## 基础设施

### 配置管理
- 注册/配置：Nacos
- 不在代码中硬编码敏感信息

### 调度
- XXL-Job（作业在adapter/scheduler）
- 注意幂等、重试、限流

### 追踪/APM
- SkyWalking
- 在日志中传递trace/correlation ID

## Flyway迁移

- 每个微服务独立管理
- 脚本放在`patra-{service}-infra/src/main/resources/db/migration`
- 命名：`V{version}__{description}.sql`，版本号递增（如V1、V2）

## 日志规范

### 使用@Slf4j
- 需要日志的类使用`@Slf4j`
- 统一SLF4J API

### 日志级别
- **ERROR**: 系统异常
- **WARN**: 业务违例
- **INFO**: 关键操作
- **DEBUG**: 诊断细节

### 日志格式
- 使用参数化日志：`log.info("op: id={}", id)`
- 异常：`log.error("msg", e)`输出堆栈
- 不打印敏感信息
- 贯穿trace/correlation ID

## JavaDoc规范

### 类级别
- 每个类注明作者与版本：`@author linqibin @since 0.1.0`

### 方法级别
- 公共方法补全`@param`/`@return`/`@throws`
- 复杂类说明用途

## 测试规范

### 测试框架
- JUnit 5 + Spring Boot Test + MyBatis-Plus Test + AssertJ + Mockito

### 测试前提
- 单元测试前检查该子模块是否引入单元测试依赖
- 单元测试在各个子模块中进行，不依赖spring-boot-starter-test
- 集成测试在`patra-{service}-boot`中进行，依赖spring-boot-starter-test

### 测试类型
- **单元测试**: 每个模块必须有，覆盖核心逻辑与边界条件
- **集成测试**: 跨模块/跨服务的关键路径（如采集→解析→入库）

### 测试数据
- 使用内存DB（H2）或测试容器（Testcontainers）
- 避免依赖外部服务

### Mock
- 使用Mockito或类似工具
- 避免过度Mock导致测试失效

## 文档维护

### 同步更新
- 修改或新增代码、配置、脚本时，同步检查相关文档是否需要更新
- 确保描述与实现一致

### 文档位置
- 仓库根README
- `docs/`目录
- 模块README
- 运行手册

### 索引维护
- 新增文档时务必在`docs/README.md`补充索引
- 在受影响模块的README中添加跳转链接
- 避免信息孤岛

### 变更记录
- 变更规则或流程需记录来源（需求单、评审纪要等）
- 将链接或说明附在相关文档中，便于后续审计

## 变更边界原则

- **不明确** → 先问
- **能复用** → 不重造
- **不越层** → 不破边界
- 以**清晰、短小、可维护**的实现为先
- 显式说明假设与权衡
- 小步提交
- 任何"跨层/跨聚合"的变更要先给出影响面评估与回滚策略
