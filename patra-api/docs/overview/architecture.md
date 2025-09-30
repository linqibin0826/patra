# 平台架构全览

Papertrace 聚焦医学文献的采集、标准化与服务化。整体采用“微服务 + 六边形架构 + 事件驱动”模式，强调幂等、可回放与可观测性。

## 1. 核心组件
- **patra-registry**：单一可信源（SSOT），管理来源配置、字典、表达式能力
- **patra-ingest**：采集与计划装配引擎，负责调度、窗口切分、任务出站
- **patra-gateway-boot**：统一接入网关，承担路由、鉴权、流控与错误形态对齐
- **自研 Starters**：封装错误解析、Web 输出、Feign 调用、RocketMQ、MyBatis 等跨服务标准
- **patra-expr-kernel**：表达式 AST 内核，保证跨服务规则的确定性
- **patra-common**：领域基类、错误码模型、JSON 规范化工具

## 2. 分层约束（Hexagonal / DDD）
- **领域层 (domain)**：纯 Java，对外通过聚合、值对象与领域事件表达业务不变量
- **应用层 (app)**：编排用例、事务、幂等控制，仅依赖领域端口
- **适配层 (adapter)**：承载 REST/MQ/调度等入站交互（Inbound Only），负责协议转换与错误映射；MQ 入站统一使用 `@Consumes(channel, consumer)` 声明，Starter 自动映射并校验 `topic/tag/group`
- **基础设施层 (infra)**：实现仓储、消息、Feign 等出站二级端口（Outbound），由领域端口约束
- **启动层 (boot)**：整合配置，约束依赖方向 `adapter → app → domain ← infra`

保持“内环无框架、外环可替换”，确保测试与演进成本可控。
## 3. 数据采集主流程
1. **调度触发**：XXL-Job 将任务上下文推送至 patra-ingest adapter 层
2. **配置组装**：adapter 调用 `app.planning.PlanIngestionApplicationService`，通过 Feign 获取 provenance 与表达式快照
3. **窗口解析**：`app.planning.window` 根据 HARVEST/BACKFILL/UPDATE 策略生成 Plan 与 PlanSlice
4. **任务装配**：`app.planning` 构建 Task + OutboxMessage，并写入事务性表
5. **消息发布**：`app.relay` 扫描待发布消息（租约 + 退避），基于领域通道目录（ChannelKey）发送至 RocketMQ 指定 Topic
6. **下游消费**：后续解析/清洗/索引服务消费 RocketMQ 事件完成链路闭环

所有步骤遵循幂等键、租约与指数退避策略，保证可回放与稳定性。
## 4. 基础设施依赖
- **Nacos**：注册中心 + 配置管理，统一聚合路由、Starter 属性与业务参数
- **MySQL**：主数据存储（计划、配置、字典、快照等）
- **Redis**：缓存与限流（规划中）
- **Elasticsearch**：文献索引（后续阶段）
- **RocketMQ**：事件总线，承载计划任务与后续处理通知
- **SkyWalking**：全链路追踪；Starter 负责注入 TraceId
- **XXL-Job**：调度中心，驱动采集窗口与回放任务

## 5. 观测性与风险控制
- 指标：统一通过 Micrometer 输出计数、耗时、慢调用、错误分类
- 日志：`@Slf4j` + 参数化格式，透传 traceId / scheduleInstanceId
- 错误：所有服务输出 RFC7807 ProblemDetail（code/status/path/timestamp）
- 风险点：配置一致性（Registry）、任务堆积（Outbox）、外部 API 限流
- 预案：租约+重试+死信队列、健康巡检任务、配置版本化
## 6. 对外接口与安全
- API Gateway 统一入口，后续接入 JWT 鉴权、限流、熔断
- 内部服务通过 Feign + ProblemDetail 协议交互，禁止裸返回字符串或 null
- 配置、密钥、连接串全部通过 Nacos 或环境变量注入，仓库不保存敏感信息

## 7. 演进方向
- 建立完整的“采集→解析→索引”事件编排链路（包括执行端）
- 引入配置变更审计与灰度机制，提升 Registry 可控性
- 构建指标看板与报警策略，覆盖任务堆积、错误码 TopN、接口耗时
- 推进 Docs-as-Code（Docusaurus/VitePress）以提升文档可发现性
