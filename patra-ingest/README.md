# `patra-ingest`（简化版说明）

统一的“计划式采集 + 任务排队”引擎：从调度触发开始，按来源(provenance)+操作(operation) 拉取配置 → 解析采集窗口 → 生成计划(Plan) → 切片(PlanSlice) → 任务(Task) → 写入 Outbox → Relay 发布至 MQ，保证：幂等、安全、可回放、可扩展、可观察。

当前代码实现的核心能力：
1. 窗口策略：HARVEST / BACKFILL / UPDATE 三类窗口解析与安全边界控制
2. 切片策略：TIME、SINGLE，可扩展注册
3. 计划装配：Plan + Slices + Tasks 原子构建与部分失败处理
4. 幂等体系：表达式/配置/切片规范化哈希 + 任务幂等键 + Outbox (channel,dedup_key)
5. Outbox & Relay：租约、指数退避、死信标记、分区(partition_key) 控制顺序
6. 错误码映射：ING-12xx..16xx 覆盖验证、装配、持久化、Outbox、外部依赖

---
## 1. 模块角色
| 模块 | 说明 |
| ---- | ---- |
| patra-ingest-api | 错误码与对外 DTO（当前主要是错误码枚举） |
| patra-ingest-adapter | XXL-Job 调度入口 / 远程 provenance 配置适配端口 / Outbox Relay Job |
| patra-ingest-app | 计划构建、窗口解析、切片、任务与 Outbox 组装、Relay 业务逻辑 |
| patra-ingest-domain | 聚合与领域异常（Plan / PlanSlice / Task / ScheduleInstance 等）以及仓储端口 |
| patra-ingest-infra | MyBatis-Plus DO、Mapper、仓储实现、Outbox 持久化 |
| patra-ingest-boot | Spring Boot 启动与错误码映射装配 |

---
## 2. 核心领域概念 & 幂等
| 概念 | 功能 | 幂等/关键字段 |
| ---- | ---- | ------------- |
| ScheduleInstance | 一次调度触发上下文 | scheduler_* + provenance_code |
| Plan | 计划蓝图（窗口+配置+表达式+策略） | plan_key / exprProtoHash / provenanceConfigHash |
| PlanSlice | 计划切片（可独立执行子窗口） | slice_signature_hash |
| Task | 待执行任务（由切片派生） | idempotent_key |
| OutboxMessage | 出站消息行 | (channel,dedup_key) + 租约字段 |

哈希规范：
1. 表达式原型 JSON → 规范化 → sha256 = exprProtoHash
2. 来源配置快照 JSON → 规范化 → sha256 = provenanceConfigHash
3. 切片 spec（窗口 from/to 等）规范化 → slice_signature_hash
4. 任务幂等键 = provenance:operation:slice_signature_hash:exprProtoHash

---
## 3. 处理流程（文字描述）
1. 调度作业（XXL Job）解析参数并记录 `ScheduleInstance`
2. 远程拉取 provenance 配置 → 快照化 + Hash
3. 根据操作模式解析窗口（校验跨度、齐次对齐、滞后安全）
4. 规范化表达式（JSON + Hash）
5. 校验（窗口合法、队列压力、来源能力）
6. 装配：Plan(DRAFT→SLICING) → 切片列表 → 任务列表（计算幂等键）
7. 持久化（Plan / Slice / Task + OutboxMessage 同事务）
8. Relay（租约扫描 PENDING Outbox → 发布 MQ → 更新状态或重试）

失败时：若未写库直接失败；若写库但发布失败，通过 retry_count + next_retry_at 指数退避；超过阈值标记 DEAD。

---
## 4. 窗口与切片
窗口模式：
| 模式 | 用途 | 关键参数 | 保护策略 |
| ---- | ---- | -------- | -------- |
| HARVEST | 增量 | lookback / alignment / lagSafety | 超近实时截断 & 滞后缓冲 |
| BACKFILL | 历史回填 | from / to / alignment | 跨度上限限制 |
| UPDATE | 选择性更新 | 输入窗口或游标 | 空/过小跳过 |

切片策略：
| 策略 | 行为 | 场景 |
| ---- | ---- | ---- |
| TIME | 按步长拆分多窗口子段 | 大跨度/并行 |
| SINGLE | 不拆分 | 小窗口或轻量操作 |

---
## 5. 计划装配与错误分类
状态流转：DRAFT → SLICING → READY / PARTIAL / FAILED / COMPLETED（后续执行端可更新）。

错误分类（与 IngestErrorCode 区段对应）：
| 异常类型 | 触发点 | 错误码段 |
| -------- | ------ | -------- |
| PlanValidationException | 窗口非法/队列过载 | ING-12xx |
| PlanAssemblyException | 切片为空/表达式局部化失败 | ING-13xx |
| PlanPersistenceException | Plan/Slice/Task 写入失败 | ING-14xx |
| OutboxPersistenceException | Outbox 写入失败 | ING-15xx |
| IngestConfigurationException | 远程配置缺失/错误 | ING-16xx |

部分失败策略：切片生成过程中局部异常 → 标记 PLAN=PARTIAL，仍保留已成功任务；致命问题直接 FAIL。

---
## 6. Outbox & Relay（精要）
核心字段：channel / partition_key / dedup_key / status_code / retry_count / next_retry_at / pub_lease_owner / pub_leased_until。

发布步骤：
1. 扫描满足 (status=PENDING AND (not_before 为空或已到期) AND 租约未占用) 的行
2. 设置租约并置为 PUBLISHING
3. 同步发送 MQ → 成功：PUBLISHED+msgId；失败：计算 next_retry_at 回退 PENDING 或超过阈值 DEAD

指数退避：`delay = baseMs * 2^retry`（裁剪上限），避免集中风暴。

分区顺序：通过 partition_key（推荐 `provenance:operation`）保障链路内局部有序且受控并发。

---
## 7. 配置项（精选）
| Key | 作用 | 示例默认 |
| --- | ---- | -------- |
| patra.ingest.outbox-relay.enabled | 是否启用 Relay | true |
| patra.ingest.outbox-relay.batch-size | 每次扫描条数 | 200 |
| patra.ingest.outbox-relay.lease-duration | 租约秒数/ISO8601 | PT30S |
| patra.ingest.outbox-relay.max-retry | 最大重试 | 8 |
| patra.ingest.outbox-relay.backoff-base-ms | 退避基准(ms) | 1000 |
| patra.ingest.planner.queue-threshold | 排队任务上限 | 10000 |

---
## 8. 关键表要点
| 表 | 唯一/幂等键 | 说明 |
| -- | ---------- | ---- |
| ing_plan | uk_plan_key(plan_key) | 计划蓝图复用与幂等 |
| ing_plan_slice | plan_id + slice_signature_hash | 防止重复切片生成 |
| ing_task | uk_task_idem(idempotent_key) | 避免重复任务 |
| ing_outbox_message | channel + dedup_key | 幂等发布 + 可重试 |
| ing_schedule_instance | (无) | 调度审计/回放 |

索引建议：Outbox 按 (status_code, not_before, id) + (channel, partition_key, status_code)；Task 按 (status_code, priority) 做调度筛选。

---
## 9. 扩展点
| 扩展点 | 目的 | 做法 |
| ------ | ---- | ---- |
| SlicePlannerRegistry | 新增切片策略 | 实现接口 + 注册 Bean |
| PlanningWindowResolver | 自定义窗口算法 | 替换默认实现 |
| PlanExpressionBuilder | 新 DSL/过滤表达式 | 组合/装饰模式 |
| OutboxDestinationResolver | 动态目的地路由 | 新策略 Bean |
| IngestErrorMappingContributor | 扩展错误码映射 | 追加异常映射 |

---
## 10. 运维排障（速查）
| 现象 | 排查路径 | 处理建议 |
| ---- | -------- | -------- |
| 无任务生成 | 看计划表是否有新 Plan；日志中是否有 ING-12xx | 调整窗口或降低队列压力阈值 |
| Plan 长期 SLICING | 切片生成异常或空集 | 检查切片策略与窗口跨度 |
| Outbox 堆积 | status=PENDING 行增多 | 查看发布错误码；检查 MQ 可用性 |
| 多条消息 DEAD | 重试次数超限 | 定位 error_code 分类后修复再人工回放 |
| 发布抖动 | retry_count 激增 | 调整 backoff 或临时降并发 |

---
## 11. Roadmap（精简）
| 项目 | 优先级 | 价值 |
| ---- | ------ | ---- |
| CURSOR / ID_RANGE 切片策略 | High | 支持非时间序列来源 |
| 指标 & 健康探针 | High | 可观测性闭环 |
| 批量插入优化（PlanSlice/Task） | Mid | 降低写放大 |
| 任务执行端/领取协议 | Mid | 闭环执行路径 |
| Relay 熔断与分级限速 | Low | 防重试风暴 |

---
## 12. 快速上手
1. 新增调度：继承 `AbstractProvenanceScheduleJob`，配置 XXL 参数
2. 运行：触发作业 → 检查 ing_plan / ing_plan_slice / ing_task / ing_outbox_message
3. 验证发布：观察 Outbox 状态从 PENDING → PUBLISHED
4. 模拟失败：临时阻断 MQ，观察 retry_count 与 next_retry_at 变化
5. 扩展策略：实现新 SlicePlanner 并在注册表追加

---
文档基于当前源码（2025-09-28）审阅编写；结构性变更后请同步更新。