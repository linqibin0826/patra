# 《Patra Ingest · 切窗（Window Slicing）策略设计（最终版）》

> 目标：把“如何把**一次采集总窗口**切成**若干可并行、可重放、可幂等**的执行切片（PlanSlice）”讲到操作层级，**直接对应你的 SQL 结构**：`ing_plan` / `ing_plan_slice` / `ing_cursor(_event)` 与 Registry（唯一真实数据来源）的四类表（表达式字段字典、能力、渲染规则、参数映射）以及 starter-expr 的 AST 原型/局部化语义。

---

## 0. 范围与结论

* **范围**：HARVEST / BACKFILL / UPDATE 三类操作的“切窗策略选择与具体切法”。
* **结论**：切窗分四大策略 **TIME → CURSOR\_LANDMARK → ID\_RANGE → VOLUME\_BUDGET/HYBRID**（只做时间类型，但需要抽象，可以扩展），以**运行期快照**为基石、用**预算/观测**自适应细分或合并，**先看水位再切窗**，并将每个切片的边界落在 `ing_plan_slice.slice_spec`，局部化表达式落在 `ing_plan_slice.expr_snapshot`。
* **关键不变量**：UTC、半开区间 `[from, to)`、幂等键稳定、游标仅前进（BACKFILL 独立命名空间）。

---

## 1. 名词与字段映射（对齐 SQL）

* **Plan 层（`ing_plan`）**

    * `expr_proto_snapshot`：**表达式原型**（AST，JSON，不含切片边界）
    * `expr_proto_hash`：原型 AST 的规范化哈希
    * `window_from/to`：总窗口建议（UTC，\[from,to)）
    * `slice_strategy_code`：TIME / ID\_RANGE / CURSOR\_LANDMARK / VOLUME\_BUDGET / HYBRID
    * `slice_params`：策略参数（步长、目标量、预算、排序、安全延迟等）
    * `provenance_config_snapshot/hash`：来源配置执行期快照/指纹

* **Slice 层（`ing_plan_slice`）**

    * `slice_spec`：**切片边界/步进行为**（JSON，非业务条件）
    * `slice_signature_hash`：对 `slice_spec` 规范化后的指纹（去重）
    * `expr_snapshot`：**局部化表达式**（= 原型 + 本 slice 的边界）
    * `expr_hash`：局部化 AST 的规范化哈希

* **水位（`ing_cursor` / `_event`）**

    * 唯一键：(provenance\_code, operation\_code, cursor\_key, namespace\_scope\_code, namespace\_key)
    * `cursor_type_code`：TIME / ID / TOKEN；`cursor_value` 与 `normalized_*`
    * **只前进**：乐观锁版本；事件先写、当前值后更

---

# 二、策略抽象设计（便于扩展）

> 目标：**现在只实现 TIME**，但抽象到位，后续加 **CURSOR\_LANDMARK / ID\_RANGE / VOLUME\_BUDGET / HYBRID** 时“新增类、加枚举，不改老代码”。

## 1) DDD 分层落点（要点）

* **DOMAIN（纯内核）**：策略接口 + 值对象（SliceSpec/Window/Range/Cursor/Budget/Hint），与框架无关
* **APP（用例）**：PlannerUseCase 调 `SliceStrategyRegistry` 产 slice 集；ExecutorUseCase 只消费现成 `slice_spec`

## 2) 接口与注册中心（核心）

### 策略枚举

```java
public enum SliceStrategyCode { TIME, CURSOR_LANDMARK, ID_RANGE, VOLUME_BUDGET, HYBRID }
```

### 值对象（示意）

```java
public record WindowSpec(Instant from, Instant to, Boundary fromBoundary, Boundary toBoundary,
                         ZoneId zone, TimePrecision precision) {}
public record RangeSpec(String key, String from, String to, Boundary fromBoundary, Boundary toBoundary) {}
public record CursorSpec(CursorType type, String startToken, Long startOffset, Integer pageSize,
                         StopCondition stop) {}
public record BudgetSpec(Integer maxRequests, Integer maxRecords, Integer maxDurationSec, Priority priority) {}
public record SliceHints(Integer expectedPageSize, String order, Integer safetyLagSec, Boolean explore) {}

public record SliceSpecPayload( // ↔ ing_plan_slice.slice_spec JSON
    SliceStrategyCode strategy,
    WindowSpec window,
    RangeSpec range,
    CursorSpec cursor,
    BudgetSpec budget,
    SliceHints hints
) {}
```

### 策略接口（Sealed Interface + 扩展点）

```java
public sealed interface SliceStrategy permits TimeSliceStrategy, CursorSliceStrategy, IdRangeSliceStrategy,
                                           VolumeSliceStrategy, HybridSliceStrategy {
    SliceStrategyCode code();
    List<SliceSpecPayload> planSlices(PlanContext ctx);
    // 可选：估算器与再切片建议
    default Optional<ResliceAdvice> resliceIfNeeded(ExecutionObservation obs) { return Optional.empty(); }
}
```



### 策略注册表（Spring Bean + Map）

```java
@Component
public class SliceStrategyRegistry {
    private final Map<SliceStrategyCode, SliceStrategy> strategies;
    public SliceStrategyRegistry(List<SliceStrategy> beans) {
        this.strategies = beans.stream().collect(Collectors.toUnmodifiableMap(SliceStrategy::code, it -> it));
    }
    public SliceStrategy get(SliceStrategyCode code) { return Objects.requireNonNull(strategies.get(code)); }
}
```

> **扩展方式**：新增策略 = 新建一个实现类（如 `CursorSliceStrategy`），`code()` 返回对应枚举，Spring 自动注入到注册表。**PlannerUseCase 无需修改**。

## 3) TIME 策略的默认实现（算法要点）

* **归一总窗**：根据水位/请求/now−safetyLag，统一 UTC、`[from,to)`
* **初切**：按 `step` 线性切；每片注入 AST 的 `RANGE(updated_at)` → 生成 `expr_snapshot/expr_hash`
* **估算与二分**：

    * 优先 `count` 能力；否则“首页外推/历史回归”；
    * 超阈值则二分至 `minStep`；极小可合并
* **乱序防护**：若源无排序保障，自动加 `overlapDelta` 与执行端本地过滤
* **预算落库**：写入 `slice_spec.budget`，作为执行端硬限
* **签名**：`slice_signature_hash = hash(normalize(slice_spec))`

> **resliceIfNeeded(obs)**：执行端反馈页深/429/体积/错误率 → 返回 `[剩余窗拆分]` 建议。

## 4) 其他策略的骨架（先留接口，后加实现）

* **CURSOR\_LANDMARK**：`CursorSpec{type, startToken/offset, pageSize, stopCondition}`
* **ID\_RANGE**：`RangeSpec{key, from, to}`（或哈希分桶）
* **VOLUME\_BUDGET**：只设 `budget` + `hints.explore=true`
* **HYBRID**：Window + Cursor + Budget 同时生效

## 5) 关键协作对象（建议抽象）

* **WatermarkPort**：读 `ing_cursor`（Planner）、写事件与当前值（Executor）
* **RegistryPort**：读取 `reg_prov_*` 能力、渲染映射
* **ExprPort**：AST 注入与规范化哈希
* **Estimator**：记录量/页数估算（可插拔）
* **Normalizer**：`slice_spec` 规范化（生成签名）
* **Validator**：单 slice 规则校验（生成失败直接拒绝入库）

---

# 三、入参 → 用例编排（落到 App 层）

## PlannerUseCase（伪流程）

3. WatermarkPort 读取水位（不写）
4. 构建 `PlanContext` → `SliceStrategyRegistry.get(force)` → `planSlices(ctx)`
5. 逐片：`validate` → 计算 `slice_signature_hash` → 落 `ing_plan_slice`（同时落局部化 `expr_snapshot/expr_hash`）
6. 为每片落 `ing_task(QUEUED)`
7. 返回结果（切片数、跳过/去重数、预计请求量）


---

## 3. 窗口来源与水位读取（所有策略共通）

* **先看水位再切窗**（Planner 只读）：

    * HARVEST：`namespace_scope=EXPR`，`namespace_key=expr_proto_hash`（表达式变更不串线）；`cursor_key=updated_at`（或等价）。
    * BACKFILL：`namespace_scope=CUSTOM`，`namespace_key=plan_id`（与前向水位隔离）。
    * UPDATE：`CUSTOM`（依据刷新策略：时间型用 `refresh_checked_at`，ID 型用 `provider_id`/桶号）。
* **总窗计算**（以 HARVEST 为例）：

    * `from = max(request.minFrom, waterline, registry.lowerBound)`
    * `to = min(request.maxTo, now - safetyLag)`
    * **UTC**、半开 `[from, to)`、统一精度（推荐毫秒）。
* **首跑缺水位**：以 registry/产品定义的基线起步；切片必须二分限流。

---

## 4. `slice_spec` 总体结构（规范）

```json
{
  "strategy": "TIME | ID_RANGE | CURSOR_LANDMARK | VOLUME_BUDGET | HYBRID",
  "window": { /* 时间窗 */ },
  "range":  { /* ID 区间/哈希桶 */ },
  "cursor": { /* token/offset/page/size 与停止条件 */ },
  "budget": { "maxRequests": 0, "maxRecords": 0, "maxDurationSec": 0, "priority": "HIGH|MEDIUM|LOW" },
  "hints":  { /* 排序、安全延迟、期望页大小、试探标志等 */ }
}
```

> **签名规范化**（生成 `slice_signature_hash`）：固定键顺序、小写键、UTC ISO-8601、枚举大写、去默认空值、数值统一字符串或统一数值。

---

## 5. TIME 策略（最常用，重点讲透）


### 5.2 初始切窗（Planner）

* **输入**：`ing_plan.window_from/to`、`slice_params.step`（ISO-8601 Duration，如 `PT6H/P1D`）、`targetRecordsPerSlice`（可选）、`expectedPageSize`、`safetyLagSec`、`maxRequests/maxDurationSec` 等。
* **初切**：按 `step` 从 `[from,to)` 线性切段（UTC）。
* **注入表达式**：将每段 `[f,t)` 注入 `expr_proto_snapshot` 的 `RANGE(updated_at)`，形成 `slice.expr_snapshot` 与 `expr_hash`。
* **形成 `slice_spec`**：

```json
{
  "strategy":"TIME",
  "window":{"from":"...Z","to":"...Z","boundary":{"from":"CLOSED","to":"OPEN"},"timezone":"UTC","precision":"MILLIS"},
  "budget":{"maxRequests":800,"maxRecords":200000,"maxDurationSec":1200,"priority":"HIGH"},
  "hints":{"expectedPageSize":200,"order":"updated_at:asc","safetyLagSec":300}
}
```

### 5.3 规模估算与自适应二分/合并

* **估算优先级**：
  A) 供应商 `count`（若有）→ B) 历史统计（同来源/相似窗）→ C) 首页样本外推（`firstPageCount * pages`）→ D) 保守常量。
* **目标**：每 slice 落在目标“请求数/记录数/时长”区间。
* **二分规则**：如估算 > `targetRecordsPerSlice * (1 + ε)` 或预计页数 > `maxPagesPerSlice`，对该窗做二分，递归直至落入目标或达到 `minStep`。
* **合并规则**：估算极小且合并后仍在预算内，可合并相邻窗，减少碎片。
* **安全延迟自适应**：若近 N 次运行“晚到率”> 阈值（如 0.5%），`safetyLagSec += Δ`（上限可配，如 1h）；低于阈值则渐进回调。

### 5.4 边界与排序约束

* **统一半开** `[from,to)`；`updated_at == to` 由下一窗处理。
* **排序**：尽量 `updated_at:asc`；若源不保证排序，执行端本地过滤 `updated_at`，并**增加窗重叠 Δ**（可配，如 1–5 分钟）来容忍乱序；重叠区靠唯一键/updated 覆盖去重。
* **分辨率对齐**：若供应商只支持“到日”，则 `from/to` 向日整；`precision` 标记为 `DAY`，并用本地过滤精化到毫秒。

### 5.5 计划 → 任务

* 对每个窗生成一条 `ing_plan_slice` 与一条 `ing_task(QUEUED)`。
* 幂等：`slice_signature_hash` 与 `uk_slice_sig` 保证同窗不重复。

### 5.6 执行期在线再切片（自愈）

* **触发阈值（任一命中）**：

    * 页深 > `maxPagesHard`（如 2000）；
    * 连续 429 或 `Retry-After` 总时长超 `maxBackoffSec`；
    * 单页响应体 > `maxBytesPerPage`；
    * 错误率 > X%。
* **动作**：将**剩余**窗口二分为 `[mid,to)` 新窗，写入“重切请求队列”（Planner 消费生成新的 slice）；当前 task 以 `PARTIAL` 收口。
* **审计**：`ing_task_run_batch` 记录触发原因与上下文（token/pageNo/bytes）。

---

## 6. CURSOR\_LANDMARK 策略（游标/令牌）

    暂时不用做
---

## 7. ID\_RANGE 策略（ID 段/分桶）
    暂时不用做
---

## 8. VOLUME\_BUDGET 策略与 HYBRID

    暂时不用做

---

## 9. 与游标（`ing_cursor`）的联动规则

* **读取（Planner）**：定位唯一键后读取 `cursor_value`；Planner **只读不写**。
* **推进（Executor）**：

    1. 先写 `ing_cursor_event`（包含 `direction=FORWARD|BACKFILL`、窗口、lineage、`observed_max_value`）；
    2. 再用乐观锁更新 `ing_cursor`（**只允许前进**；BACKFILL 在自己的 `namespace`）。
* **命名空间**：

    * HARVEST：`EXPR` + `expr_proto_hash`；
    * BACKFILL：`CUSTOM(plan_id)`；
    * UPDATE：`CUSTOM`（按刷新策略定义）。
* **比较函数**：

    * TIME：`normalized_instant`；
    * ID：`normalized_numeric`；
    * TOKEN：不做“前向”持久水位。
* **observed\_max\_value**：记录此次观测到的最大 `updated_at/ID`，供下次规划参考（可选）。

---

## 10. 预算器与并发（与切窗的耦合点）

* **每个 slice** 必须带 `budget`：`maxRequests/maxRecords/maxDurationSec/priority`；
* **规划期**：依据 `reg_prov_rate_limit_cfg`、`reg_prov_retry_cfg`、`reg_prov_batching_cfg` 粗估“每 slice 可承载的请求与时间”；
* **运行期**：遇 429/5xx → 退避降速；超过预算即收口 `PARTIAL` 并触发**在线再切片**；
* **配额优先级**：HARVEST > UPDATE > BACKFILL（可配），Planner 生成时即写入 `priority`，Executor 按优先队列消费。

---

## 11. 边界与异常场景（强规则）

* **半开区间**：统一 `[from,to)`；`==to` 的记录肯定归下一窗。
* **乱序/晚到**：配置 `overlapDelta` 与 `safetyLagSec`；晚到率超阈值自动增大 `safetyLagSec`。
* **分辨率不一致**：供应商日粒度 → `precision=DAY`，本地再精化。
* **无排序保证**：必须本地过滤 `updated_at`；页深/429 触发回切。
* **无 `RANGE`**：优先转为 CURSOR\_LANDMARK，再不行用 HYBRID/VOLUME。
* **撤稿/合并**：交由 UPDATE 路线处理（Signals）；不破坏 HARVEST 切窗。
* **UTF/时区**：一切时间字段**强制 UTC**；禁止在 `slice_spec` 出现本地时区。

---

## 12. 校验清单（Validator）

对每条 `slice_spec` 生成后执行以下校验（失败拒绝入库）：

* `strategy` 与子段一致性（TIME→必须有 `window`；ID\_RANGE→必须有 `range`；CURSOR→必须有 `cursor`）。
* 时间：`from < to`、UTC ISO-8601、`boundary.from/to` 合法、`precision` 合法。


---


## 14. 与表达式（starter-expr AST）的契约

* **Plan 原型**：`expr_proto_snapshot`（不含任何窗/ID 边界）
* **Slice 局部化**：将 `slice_spec` 的窗口/ID 植入 AST（`RANGE(updated_at)` / `TERM(id in)` / `TOKEN` 不落入持久水位）
* **Hash 稳定性**：AND/OR 子节点**排序规范**；时间统一精度；枚举大写；数值一致化。
* **安全**：仅允许 JSONPath/白名单函数；请求参数渲染后进行供应商参数白名单校验（映射表）。

---


## 附录 B：在线再切片触发阈值（建议默认）

* `maxPagesSoft=1000`，`maxPagesHard=2000`
* `429WindowSec=300` 内 `429Count>=5` → 触发
* `maxBytesPerPage=5MB`（或来源定）
* `errorRate>=5%`（近 50 页窗口统计）
* 动作：将**剩余**窗口二分为两半；生成新 slice（`slice_spec.window.from=mid`）；当前 task 以 `PARTIAL` 收口。

---

## 附录 C：安全延迟（SafetyLag）自适应

* 指标：最近 7 天晚到率（`late_arrival = items(updatedAt > window.to)` / `totalItems`）。
* 调整：

    * `late_arrival > 0.5%` → `+5m`，上限 1h；
    * `< 0.1%` 且 `safetyLag > 5m` → `-1m`；
* 生效：写入 Planner 的默认 `slice_params`，并在 `slice_spec.hints.safetyLagSec` 回显。
