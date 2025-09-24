# 《Plan 阶段窗口确定设计（基于 Cursor / 用户入参 / window\_offset）》

## 1. 目标与输入/输出

* **目标**：在 Plan 阶段计算出**确定的总窗口**并落库到 `ing_plan.window_from / window_to`，后续切片器据此进行切窗。
* **输入**

    * **用户入参**：`Instant windowFrom`、`Instant windowTo`（可空）。
    * **水位表**：`ing_cursor`（必要时参考 `ing_cursor_event` 以审计）。
    * **Registry**：`reg_prov_window_offset_cfg`（当前生效配置）。
* **输出**

    * `ing_plan.window_from`（UTC，含）
    * `ing_plan.window_to`（UTC，不含）

## 2. 配置选择（window\_offset 的生效记录）

    provenanceConfigSnapshot中的值

## 3. HARVEST（增量）窗口确定

**语义**：从“前向水位”推进到“当前安全上界”，并以 `lookback` 轻度回看覆盖迟到数据。窗口为 **UTC 半开** `[from, to)`。

### 3.1 游标定位

* `operation_code=HARVEST`。
* `cursor_type=TIME`；`cursor_key`：优先使用 `offset_field_name`（若指向时间字段），否则使用 `default_date_field_name`（如
  PubMed: EDAT/PDAT/MHDA；Crossref: indexed-date）。
* 命名空间：按项目约定（常用 `EXPR`：`namespace_key=expr_hash`；或 `GLOBAL` 共享）。用于从 `ing_cursor` 读取“前向水位”。

### 3.2 计算步骤

1. **安全上界**

* `nowSafe = nowUTC - watermark_lag_seconds`（若为空则为 0）。
* 上界候选：`toCandidate = min(user.windowTo?, nowSafe)`（`?` 表示可空；为空则忽略该项）。

2. **下界候选**

* 若存在游标 `harvestWM`：

    * 先**回看**：`lowerByCursor = harvestWM - lookback`（lookback 可空；为空视为 0）。
    * 再与用户下界取最大：`fromCandidate = max(lowerByCursor, user.windowFrom?)`。
* 若不存在游标：

    * 若用户给了下界：`fromCandidate = user.windowFrom`；
    * 否则**默认回退**：按 `window_size_value/unit` 回看一窗：`fromCandidate = nowSafe - 1×window_size`。

3. **模式对齐（若 CALENDAR）**

* `from = floorAlign(fromCandidate, calendar_align_to)`；
* `to   = floorAlign(toCandidate,   calendar_align_to)`；

> 对齐规则：向**下**取整，使 `[from,to)` 左闭右开；若对齐后二者相等 → 空窗口。

4. **合法性校验与产出**

* 若 `from >= to` → 空窗口（Plan 可直接返回 0 切片并记录原因）；
* 否则写入 `ing_plan.window_from = from`、`ing_plan.window_to = to`。
* 审计记录：所用游标值、lookback、用户裁剪、对齐动作、`nowSafe` 等。

> **说明**：`overlap` 与 `max_window_span_seconds` 不在 Plan 总窗强行应用，而在**切片阶段**约束**单 slice** 的重叠与跨度。

## 4. BACKFILL（回填）窗口确定

**语义**：补齐历史空洞，不影响“前向增量”的水位。窗口为 **UTC 半开** `[from, to)`。

### 4.1 游标与锚点

* `operation_code=BACKFILL`。
* 使用**独立的回填进度游标**（建议命名空间 `CUSTOM`，`namespace_key` 取“回填活动ID/planId 的哈希”），`cursor_type=TIME`，
  `cursor_key` 通常与 HARVEST 相同的时间字段。
* 查询**前向增量水位**（HARVEST 的进度）作为天然上限 `forwardWM`。

### 4.2 计算步骤

1. **上界锚点**

* `nowSafe = nowUTC - watermark_lag_seconds`。
* `upperAnchor = min(user.windowTo?, forwardWM?, nowSafe)`；**回填不得超过这条锚线**（避免与增量重叠过深或读到未稳定数据）。

2. **下界候选**

* 若存在回填游标 `backfillWM`：`fromCandidate = max(backfillWM, user.windowFrom?)`；
* 若不存在回填游标：

    * 若用户给了下界：`fromCandidate = user.windowFrom`；
    * 否则**默认回退**：以 `upperAnchor - 1×window_size` 作为下界（至少一窗的回看量）。

3. **边界收敛**

* 防越界：`fromCandidate = min(fromCandidate, upperAnchor)`；
* 若为 CALENDAR：

    * `from = floorAlign(fromCandidate, calendar_align_to)`；
    * `to   = floorAlign(upperAnchor,   calendar_align_to)`。

4. **合法性校验与产出**

* 若 `from >= to` → 空窗口；
* 否则写入 `ing_plan.window_from = from`、`ing_plan.window_to = to`。
* 审计记录：`forwardWM`、`backfillWM`、用户裁剪、对齐动作、`nowSafe` 等。

> **说明**：回填不应“回退”前向水位；它维护的是**自己的进度游标**（与增量隔离）。`overlap` 仍在切片阶段使用。

## 5. UPDATE（刷新/核对）窗口确定

**语义**：对既有记录再抓取与校对。Plan 需要给出本次 UPDATE 的时间窗口，用于**节奏控制与统计口径**；是否作为筛选条件取决于“时间驱动
vs ID 驱动”模式。

### 5.1 模式判定

* 满足以下任一情况 → **时间驱动 UPDATE**：

    * `cfg.offset_type_code=DATE` 且用户给了 `windowFrom/windowTo`；
    * 或明确要求按时间巡检。
* 其他情况 → **ID 驱动 UPDATE**（更常见；候选 ID 来自读侧/信号），时间窗仅用于**分摊与限流**。

### 5.2 时间驱动 UPDATE（与 HARVEST 类似，但推进的是“刷新检查时间”）

* 游标：`operation_code=UPDATE`；`cursor_type=TIME`；`cursor_key='refresh_checked_at'`（约定名）；命名空间通常取 `GLOBAL` 或
  `EXPR`。
* 计算：

    1. `nowSafe = nowUTC - watermark_lag_seconds`；
    2. `toCandidate = min(user.windowTo?, nowSafe)`；
    3. `fromCandidate = max(updateWM?, user.windowFrom?)`；若无游标且无下界，默认
       `fromCandidate = nowSafe - 1×window_size`；
    4. 若 CALENDAR → 各自向下对齐；
    5. 若 `from >= to` → 空窗口；否则写 `ing_plan.window_from/to`。
* 注：推进的是“刷新检查时间”的游标，**不影响业务 updated 水位**。

### 5.3 ID 驱动 UPDATE（常态）

* 候选记录来自读侧或信号（撤稿/合并/错误隔离等）。Plan 仍需产出**巡检时间窗**用于**调度节奏**：

    * 若用户给了 `windowFrom/windowTo` → 直接采用（上界仍裁剪 `nowSafe`；CALENDAR 需对齐）；
    * 若用户未给 → 取 `nowSafe - 1×window_size` 到 `nowSafe` 作为**本轮巡检窗**（可对齐）。
* 可选：维护一个 `UPDATE/GLOBAL` 的“刷新检查时间”游标，代表**巡检覆盖进度**，便于分摊 1/N 日常刷新。
* 产出依然写 `ing_plan.window_from/to`，但执行期的筛选以**候选 ID 集**为主，时间窗仅用于节奏/预算。

## 6. 产出与落库（Plan）

---

### 小结（拿来就能用）

* **HARVEST**：`to = min(user.to?, now - lag)`；`from = max(user.from?, harvestWM - lookback)`；CALENDAR 则对齐；合法性检查后落库。
* **BACKFILL**：`to = min(user.to?, forwardWM?, now - lag)`；`from = user.from? / backfillWM? / (to - 1×window_size)`
  ；对齐后落库；不触碰前向水位。
* **UPDATE**：

    * 时间驱动：同 HARVEST，但推进的是“刷新检查时间”；
    * ID 驱动：窗口用于**节奏**（用户给定或默认一窗），候选来自读侧/信号，必要时维护巡检游标。
