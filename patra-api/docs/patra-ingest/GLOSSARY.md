# Ingest 术语词汇表 (Glossary)

| 术语 | 定义 | 作用域 | 备注 |
|------|------|-------|------|
| Spec Snapshot | Registry 配置编译后的执行期不可变快照 | Core/Strategy | plan(proto) 与 slice(localized) 双层 |
| Strategy Bits | 分页/窗口/两段式/限流/预算/重试等组合策略位 | Strategy | 统一压缩来源差异 |
| Planner | 计划流水线生产者 | Runtime | 生成 schedule/plan/slice/task |
| Executor | 任务执行流水线 | Runtime | 消费 task → run → batch |
| Slice | 可并行+幂等最小计划单元 | Core | 一片一 task |
| Task Lease | 租约 (lease_owner / leased_until / lease_count) | Runtime | 防止多实例重复消费 |
| Batch | 分页/令牌最小账目 (ing_task_run_batch) | Runtime | 幂等 & 断点续跑 |
| Cursor Namespace | 水位命名空间 (GLOBAL/EXPR/CUSTOM) | Cursor | 分离 HARVEST/BACKFILL/UPDATE |
| Cursor Event | append-only 推进事件 | Cursor | 先事件后现值，幂等键保护 |
| Safety Lag | now 向后安全延迟，缓冲晚到 | Strategy | 调整窗口上界 & 减少乱序影响 |
| Online Re-slicing | 剩余窗口在线二分重切 | Strategy/Runtime | 429/页深/延迟触发 |
| Budgeter | 计划/执行期预算控制 (请求/配额) | Strategy | 与 Rate Gate 协同 |
| Rate Gate | 集中令牌 + AIMD 限速组件 | Strategy/Runtime | acquire/feedback 循环 |
| AIMD | 加性增 / 乘性减 降速算法 | Strategy | 429/5xx 降速，稳定后缓慢恢复 |
| Two-Phase | IDs→详情 两段式拉取模式 | Strategy | Phase1 枚举 ID，Phase2 详情 |
| Idempotent Key | 幂等签名键 | 全域 | Task/Batch/CursorEvent 分层 |
| Black Box Telemetry | 可回放所需最小观测集合 | Runtime | run/batch/cursor event lineage |

> 术语新增请同步引用位置，避免多义。
