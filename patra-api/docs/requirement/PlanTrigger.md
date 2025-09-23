
# 一、分层职责（只覆盖 触发→Plan→Slice→Task）

* **Adapter（XXL-Job）**
  每个数据源一个 Job 类，每种模式一个方法（`harvest/backfill/update`）。在这里**解析/补齐缺省**（`provenance_code / endpoint_name / operation_code / trigger_* / window_* / priority`），然后调用 App 层用例。
* **App（Planner 用例编排）**
  核心：**强校验 → 调 registry（Feign）编译快照 → 计算总窗 → 切片 → 派生任务（QUEUED）**。
  只写 `ing_schedule_instance / ing_plan / ing_plan_slice / ing_task`，不触碰 run/batch/cursor。
* **Ports（六边形端口）**

    * `RegistryPort`（Feign）：按 `provenance_code` 取来源配置、端点能力、表达式原型、分页/限流/重试等。
    * `PlanRepository / SliceRepository / TaskRepository / ScheduleRepository`：对应 ingest 四张表的仓储端口。
    * `CursorQueryPort`（可选）：若 HARVEST 需要用当前水位计算窗口起点，则读 `ing_cursor`。

---

# 二、Adapter 入口（XXL-Job）要做什么

**输入**：允许尽量少（比如 `provenance_code` + 可选窗口）。
**缺省解析（必须做）**：

1. `operation_code`：方法内锁死（`harvest()`=HARVEST / `backfill()`=BACKFILL / `update()`=UPDATE）。
2. `endpoint_name`：按“显式入参 > registry 映射 > 端点能力推断 > 单端点兜底”解析。
3. `trigger_type_code/scheduler_code`：由运行环境推断（XXL → `SCHEDULE/XXL`）。
4. `window_from/to`：

    * HARVEST：一般不传 → 交由 App 用**水位 + 安全延迟**计算。
    * BACKFILL：通常由入参提供历史区间。
    * UPDATE：通常不传时间窗，用读侧候选集（本次不实现候选集，仅到 Task）。
      **产物**：组装一个 **PlanCommand DTO**（显式、确定的字段），调用 App 用例。

---

# 三、App 层——Planner 编排（一步步要做什么）

## 步骤 0：记录触发根 —— `ing_schedule_instance`

* 写入：`scheduler_code / scheduler_job_id / scheduler_log_id(如果有) / trigger_type_code / triggered_at / trigger_params`、`provenance_code`。
* 目的：将本次触发**固定成可追踪的根**。

## 步骤 1：拉取配置（Feign → RegistryPort）

* 调用 registry 获取以下**统一模型**（建议一次批量接口）：

    * 端点定义与能力（search/detail/metrics…，是否支持 `updatedSince/hasNext/itemsPath/idParam/batchIds` 等）
    * 分页策略（offset/page、token/url、路径表达式）
    * 时间窗策略（安全延迟、默认步长、最大/最小切片窗）
    * 速率/配额与重试策略
    * 表达式**原型**（用于渲染成执行期表达式）

* **强校验**（失败则终止）：

    * 枚举合法：`operation_code / slice_strategy_code` 等
    * 能力匹配：所选 `endpoint_name` 必须满足该模式所需能力
    * 表达式原型必填项齐全（`itemsPath/idPath/updatedAtPath` 等）
    * **库存与预算闸门**：队列库存（`ing_task` 的 `QUEUED`）未超过阈值（避免越切越堆）

## 步骤 2：计算“本次计划的总窗口/候选”

* **HARVEST**：

    * `from =` 若入参空 → 读 `ing_cursor`（`CursorQueryPort`）的前向水位或回退到“系统起点”；
    * `to = now - safetyLag`（来自 registry 配置或系统默认）；
    * 统一 UTC & 半开区间 `[from, to)`，`from < to` 方可继续。
* **BACKFILL**：

    * 采用入参历史区间（或策略自动向过去扩展）；
    * **不**改变前向水位；命名空间留给执行期，这里只做蓝图。
* **UPDATE**：

    * 本次仅生成任务，不做候选集枚举；ID 切片留给后续扩展（现在可将 ID 切片参数置空或占位）。

从配置项和窗口构建出整个计划的Expr (ExprPort 间接使用patra-spring-boot-starter-expr构建并编译Expr。(Exprs/ExprCompiler))

## 步骤 3：生成计划蓝图 —— `ing_plan`

* 字段：
  `schedule_instance_id / plan_key / provenance_code / endpoint_name / operation_code`
  `expr_proto_hash/expr_proto_snapshot`
  `provenance_config_snapshot / provenance_config_hash`
  `window_from/window_to`（HARVEST/BACKFILL）
  `slice_strategy_code / slice_params`（来自 registry 策略或默认）
  `status_code = DRAFT → SLICING`
* 备注：写入默认解析链路（endpoint/operation/trigger 如何推断）。

## 步骤 4：自适应切片 —— `ing_plan_slice`

* **策略选择**：

    * HARVEST/BACKFILL → `TIME`；
    * UPDATE → `ID_RANGE`（若当前不做 ID 集，可先生成 1 片占位，后续再切）。
* **规模估算**（尽量使用 registry 的 `totalPath`/meta；否则抽样页/历史经验）：确定大致片数与步长。
* **切片生成**：每片写入：

    * `slice_no` 递增
    * `slice_spec`（统一 JSON：时间/ID/token/预算等）
    * **局部化表达式**：将片内边界渲染到 `expr_snapshot`，并计算 `expr_hash`
    * `slice_signature_hash = SHA256(plan_id + normalized(slice_spec) + expr_hash)`
    * `status_code = PENDING`
* **幂等与并发**：命中 `uk_slice_unique/uk_slice_sig` 视为已存在，直接复用。

## 步骤 5：派生任务 —— `ing_task`

* **一片一任务**（当前阶段建议 1:1）：

    * 冗余关联：`schedule_instance_id / plan_id / slice_id`
    * 语义：`provenance_code / operation_code / credential_id?`
    * `params`：规范化任务参数（如 two-phase 标志、初始分页位）
    * `expr_hash`：冗余执行表达式哈希
    * 调度：`priority / scheduled_at?`
    * `status_code = QUEUED`
* **幂等**：`idempotent_key = SHA256(slice_signature_hash + expr_hash + operation + trigger + normalized(params))`；命中 `uk_task_idem` 则**不重复创建**。
* **背压**：在派生循环中持续检查库存阈值；超阈则**立即停下发**：`ing_plan.status=PARTIAL` 收口。

## 步骤 6：收口

* 切片全部成功派生 → `ing_plan.status=READY`。
* 发生编译/切片/派生错误 → `ing_plan.status=FAILED` 并记录原因。
* 发生背压停发 → `ing_plan.status=PARTIAL`（等待下次触发继续切）。

> **事务建议**：
>
> * `plan` 独立提交；
> * 切片+任务按 **小批（如 200\~500）** 提交，避免长事务；
> * 幂等冲突（唯一键违反）按“已存在=成功”处理，继续下一个。

---

# 四、与 registry 的 Feign 交互约定（不写代码，只定契约）

使用patra-registry-api下的ProvenanceClient 提供的接口，若接口不契合，可以直接修改这个接口（暂时无人使用）。

---

# 五、字段落库对齐（关键映射）

* `ing_schedule_instance`：**只存触发上下文**（不混入执行快照）。
* `ing_plan`：**存“原型快照”与“来源配置快照/哈希”**，以及总窗口与策略。
* `ing_plan_slice`：\*\*存“局部化表达式快照/哈希”\*\*与统一 `slice_spec`（是并行与幂等边界）。
* `ing_task`：**只入队**，携带幂等键/调度属性/冗余哈希，不含执行账目。

Expr的处理可以用starter-expr模块下的ExprCompiler
