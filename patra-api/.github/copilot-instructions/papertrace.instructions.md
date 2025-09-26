> Papertrace（医学文献数据平台）的智能体工作手册。面向 Codex / Copilot / Cursor 等编码智能体读取。
> 本文件为“智能体版 README”：集中提供**环境、命令、架构约束与行为边界**，帮助你在本仓库可靠工作。

## 作用域与层级合并
- 本文件位于仓库 **根目录**，适用于整个仓库。
- 允许在子目录放置更细粒度的 `AGENTS.md`（如 `patra-ingest/AGENTS.md`）。**“就近优先”**：离被修改文件最近的 AGENTS.md 生效；与上层内容**自上而下合并**。
- 若同一规则冲突：**子目录覆盖根目录**；显式用户指令优先于文件指令。

---

## 0. 你是谁 & 如何工作（Do / Don’t）
**Do**
- 遵循 **六边形架构 + DDD**；**严守依赖方向**；不越层、不泄漏实现细节。
- 信息不足时**先提问再动手**；优先复用已有能力（`patra-*` starters、`patra-common`、Hutool）。
- 输出 **小步变更 / 小 Diff**；为关键决策写下**假设与权衡**。
- 任何“数据落地链路”变更（采集→解析/清洗→入库）必须可回放、可幂等、可观测。

**Don’t**
- 不在 `domain` 引入任何框架依赖。
- 不在代码中硬编码密钥/连接串/可变配置（统一走 **Nacos** / 环境变量）。
- 不进行**破坏性数据操作**（删库、ES 重建索引、MQ 主题变更等）而不经审批（见“安全与权限”）。

---

## 1. 快速开始（本地开发）
> 目标：在本地启动基础设施 + 单模块开发编译测试

```bash
# 1) JDK 与构建
java -version        # 要求 Java 21
mvn -v               # Maven 多模块，父 POM 统一依赖

# 2) 全仓快速编译（不打包）
mvn -q -T 1C -DskipTests compile

# 3) 单模块开发（例：patra-registry）
cd patra-registry && mvn -q clean test

# 4) 打包（当前默认跳过测试；如需跑测，去掉 -DskipTests）
mvn clean package -DskipTests
```

⸻

## 2. 项目概览
	• 名称：Papertrace – 医学文献数据平台
	• 目标：
        1. 采集 10+ 医学文献源（PubMed、EPMC…）
        2. 以 SSOT（单一可信源 patra-registry）管理配置/词典/元数据
        3. 对原始文献数据解析、清洗与标准化
        4. 后续提供搜索与智能分析
	• 架构：微服务 + 六边形架构 + 事件驱动（异步通信）
	• 当前重点：保证数据“可靠落地”（采集 → 解析/清洗 → 入库）

⸻

## 3. 技术栈与版本（关键）
	• 语言/构建：Java 21，Maven（多模块；父 POM 统一依赖），UTF-8
	• 核心框架：Spring Boot 3.2.4；Spring Cloud 2023.0.1；Spring Cloud Alibaba 2023.0.1.0
	• 数据持久：MyBatis-Plus 3.5.12，MySQL 8.0，Redis 7.0，Elasticsearch 8.14，RocketMQ 5.3.2
	• 基础设施：Nacos（注册/配置），SkyWalking 10.2（APM/Tracing），XXL-Job 3.2.0（调度），Docker Compose（本地）
	• 工具/映射：Lombok 1.18.38，MapStruct 1.6.3，Hutool 5.8.22
	• 自研 Starters：patra-spring-boot-starter-core/-web/-mybatis，patra-spring-cloud-starter-feign
	• 表达式引擎：patra-expr-kernel

⸻

## 4. 仓库结构（精简）

Papertrace/
├─ patra-parent/                 # 父 POM（依赖/插件管理）
├─ patra-common/                 # 公共工具&基类
├─ patra-expr-kernel/            # 表达式引擎
├─ patra-gateway-boot/           # API Gateway
├─ patra-registry/               # SSOT 注册微服务
├─ patra-ingest/                 # 采集/摄取微服务
├─ patra-spring-boot-starter-*/  # 自研 starters
└─ docker/                       # 本地基础设施

### 4.1 微服务模块通用子结构

patra-{service}/
├─ patra-{service}-boot/      # 可执行入口
├─ patra-{service}-api/       # 外部 API 契约（DTO/接口）
├─ patra-{service}-domain/    # 领域（实体/聚合/枚举/端口）
├─ patra-{service}-app/       # 应用（用例编排）
├─ patra-{service}-infra/     # 基础设施（仓储）
└─ patra-{service}-adapter/   # 适配层（REST/调度/MQ）

### 4.2 依赖方向（必须遵守）

	• adapter → app + api（+ web starters）
	• app → domain + patra-common + core starter
	• infra → domain + mybatis starter + core starter
	• domain → 仅 patra-common（禁止引入 Spring/框架）
	• api：不依赖框架（对外暴露）

### 5. 开发命令

优先单模块、按文件粒度命令；全仓级命令仅在明确要求时使用。

	• 编译（单模块）：mvn -q -DskipTests compile
	• 测试（单模块）：mvn -q test
	• 检查 + 测试（快速回归）：mvn -q -DskipITs test
	• 全仓编译：.mvn clean compile（或 mvn -q -T 1C -DskipTests compile）
	• 打包：mvn clean package -DskipTests（必要时移除 -DskipTests）
	• 运行本地基础设施：进入 docker/，执行 docker compose up -d
	• 关闭基础设施：docker compose down

⸻

## 6. 代码约定

### 6.1 DO/枚举/JSON
	• 数据库存 JSON 字段在 DO 中统一使用 Jackson JsonNode 表示。

### 6.2 POJO 形态
	• 不可变/值对象优先使用 record。
	• 需要可变时使用 Lombok + class；record 内不使用 Lombok。

### 6.3 Lombok
	• 不手写样板代码（getter/setter/toString/equals/hashCode）；使用 @Data 或组合注解。
	• 在 class 中合理选用，避免过度注解。

### 6.4 工具复用
	• 不重复造轮子：优先使用 Hutool 与 patra-common/starters 提供的工具；新增前先检索。

## 7. 数据处理与一致性
	• 幂等：采集/解析/清洗流程可重入；为关键步骤设计幂等键/去重策略。
	• 事务：在 app 层编排；跨聚合用事件最终一致；domain 不引入事务框架。
	• 仓储：按聚合整体持久化；infra 使用 MyBatis-Plus，实体转换由 MapStruct 完成。

## 8. 基础设施与可观测性
	• 注册/配置：Nacos；不在代码中硬编码敏感信息。
	• 调度：XXL-Job（作业在 adapter/scheduler），注意幂等、重试、限流。
	• 追踪/APM：SkyWalking；在日志中传递 trace/correlation ID。

## 9. 安全与权限（智能体必须遵守）

允许无需确认
	• 读取/列出文件、编译单模块、运行单元测试、静态检查（lint/format/type-check）。
	• 在工作目录内创建/修改代码与测试文件。

需要事先获得批准
	• 变更数据库结构/清空数据/大规模回填；RocketMQ 主题/订阅调整。
	• 执行全仓重构/跨大量模块的改名、移动。
	• 任何会暴露或写入敏感信息的操作（密钥、令牌、证书、隐私数据）。

禁止
	• 提交包含明文密钥/凭据的内容。
	• 对 domain 层引入框架依赖或从下层向上反向依赖。

## 10. 日志规范（@Slf4j）
	• 需要日志的类使用 @Slf4j；统一 SLF4J API。
	• 级别：ERROR 系统异常；WARN 业务违例；INFO 关键操作；DEBUG 诊断细节。
	• 使用参数化日志：log.info("op: id={}", id)；异常：log.error("msg", e) 输出堆栈。
	• 不打印敏感信息；贯穿 trace/correlation ID。

⸻

## 11. JavaDoc（极简）
	• 每个类注明作者与版本：@author linqibin @since 0.1.0
	• 公共方法补全 @param/@return/@throws；复杂类说明用途。


## 12. 当你卡住时
	•	小步提交；任何“跨层/跨聚合”的变更要先给出影响面评估与回滚策略。

⸻

## 13. 附：按层包结构（关键目录）
**Domain (`com.patra.{service}.domain`)**

```
model/
  aggregate/{name}/   # aggregates/entities
  vo/                 # value objects
  readonly/           # read-only objects
  event/              # domain events
  enums/              # domain enums (may be reused across layers)
port/                 # repository/service ports
```

**Application (`com.patra.{service}.app`)**

```
service/              # application services (use-case orchestration)
usecase/command|query # command/query DTOs
converter/              # Domain ↔ App (MapStruct)
security/             # authorization checks
model/readonly/event/ # models used by app/adapter
tx/                   # transaction helpers (idempotency/locks)
config/               # application config
```

**Infrastructure (`com.patra.{service}.infra`)**

```
persistence/
  entity/             # DB entities (extends BaseDO)
  mapper/             # MyBatis-Plus mappers
  repository/         # repository implementations
  converter/          # Entity ↔ Domain converters
config/               # infra config
```

**Adapter (`com.patra.{service}.adapter`)**

```
inbound/
  /rest/               # REST controllers
  /scheduler/          # scheduled jobs (XXL-Job)
  /mq/                 # MQ listeners
outbound/
  /rest/             # REST clients (Feign)
  /mq/                 # MQ producers
  /other/              # other adapters (e.g., file, email)
config                # adapter configuration
```


⸻

## 14. 变更边界（TL;DR）
	• 不明确 → 先问；能复用 → 不重造；不越层 → 不破边界。
	• 以 清晰、短小、可维护 的实现为先；显式说明假设与权衡。
	• 子目录若存在 AGENTS.md，就近优先采用更细规则（本文件为全局基线）。
