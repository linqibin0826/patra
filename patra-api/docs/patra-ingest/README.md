# Papertrace · Patra Ingest 文档体系总览

本目录重构为四个子域：Core / Strategy / Cursor / Runtime，并提供 SQL 与术语表。所有长文保持高内聚，避免碎片；命名与 Registry 对齐。

## 导航（推荐顺序）
1) Core（核心流程与数据对象）  
   索引：`core/Ingest-core-schema-design.md`
2) Strategy（策略位矩阵与编排能力）  
   索引：`strategy/Ingest-strategy-schema-design.md`
3) Cursor（水位与命名空间模型）  
   索引：`cursor/Ingest-cursor-schema-design.md`
4) Runtime（执行流水线 / 状态机 / 运维 / 观测）  
   索引：`runtime/Ingest-runtime-schema-design.md`
5) SQL（DDL + 索引）  
   文件：`sql/patra-ingest.sql`
6) 术语词汇表：`GLOSSARY.md`

## 子域作用
- Core：调度→计划→切片→任务→运行→批次对象模型与快照边界。
- Strategy：分页 / 时间窗 / 两段式 / 限流 / 预算 / 重试 / 自适应切片策略位与校验矩阵。
- Cursor：事件先行、仅前进、多命名空间（HARVEST/BACKFILL/UPDATE）水位推进模型。
- Runtime：Planner / Executor 双流水线、状态机、幂等、自愈、黑匣子观测与指标。

## 全局原则（不在子文档重复）
- 时间窗统一 UTC、半开区间 `[from,to)`；等于 `to` 归下一窗。
- “事件先行”：写 `ing_cursor_event` 后再尝试推进 `ing_cursor`，仅前进。
- 幂等分层：Task / Batch / CursorEvent + 业务唯一键 `(provenance_code, endpoint, provider_id)`。
- 快照不可变：执行期完全依赖表达式/Spec 快照，不受 Registry 后续漂移影响。

## 快速入口
- 核心模型：`core/Ingest-core-schema-design.md`
- 策略矩阵：`strategy/Ingest-strategy-schema-design.md`
- 游标模型：`cursor/Ingest-cursor-schema-design.md`
- 执行流水线：`runtime/Ingest-runtime-schema-design.md`
- 全部 DDL：`sql/patra-ingest.sql`
- 术语表：`GLOSSARY.md`

---
提示：各索引页含同域导航；引用跨域概念时链接其 schema-design 或 reference 文件，避免冗复制。 
