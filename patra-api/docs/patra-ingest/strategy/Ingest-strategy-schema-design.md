# Ingest · Strategy Schema 设计索引

导航：Strategy[本页] | Guide | Reference | Ops | Examples ｜ 总览：`../README.md`

## 1. 设计目标
将来源异构性压缩为有限“Strategy Bits”，经编译形成不可变 Spec Snapshot，使执行逻辑免于硬编码来源特质。策略位具备：
- 独立验证与降级路径
- 可组合性（分页 + 窗口 + 两段式 + 限流 + 预算）
- 指纹化（fingerprint）支持快照复用与回放一致性

## 2. 编译入口（Registry → Spec）
输入：触发上下文（provenance_code / endpoint_name / operation_code / 窗口请求 / 参数 / 优先级） + Registry 汇总配置。
合并优先级（低→高）：平台默认 < provenance 默认 < endpoint 定义 < *_cfg 子域覆盖 < 触发参数。
输出：Spec Snapshot（http/auth/pagination/window/retry/rate/batching/expr/mapping/twoPhase + 指纹） + expr_proto_hash。

## 3. 策略位分类总览
| 分类 | 主要字段 (Spec) | 关键验证 | 降级策略 | 运行期关注 |
|------|----------------|---------|---------|------------|
| Auth/HTTP | auth.type/location, http.method/baseUrl/templates | method 字典, https 强制 | 不降级到匿名 | 签名钩子/脱敏 |
| Pagination | pagination.type(page/offset/token/url/time), pageSize, nextTokenPath | 路径必填/基数 | fallback TIME/hasMore 退化 | hasNext 判定 & 二次探针 |
| Window & Watermark | supportsUpdatedSince, safetyLag, max/minWindow, watermarkKey | key 必填, 半开区间 | 客户端过滤 + UPDATE 路线 | SafetyLag 调优 |
| Ordering | orderBy | 字段合法性 | 默认 updated desc | 防乱序重复页 |
| Two-Phase | twoPhase.enabled, idBatchSize | DETAIL/SEARCH 成对 | 关闭 twoPhase | Phase1/2 分离统计 |
| Response Parsing | itemsPath/idPath/updatedAtPath/hasMorePath | 三件套必填 | UPDATE-only (缺 updated) | 数据污染隔离 |
| Expr & Mapping | allowedFunctions, render rules, requestParams | 函数白名单 | omit/nullPolicy | 安全/防注入 |
| Idempotence | 公式见 reference | 组成字段全不空 | - | 重放读旧 |
| Rate & Quota (AIMD) | rate.qps/burst, headerAdaptive, quotaDaily | qps>0 | r_min=0.1 | acquire/feedback 降速 |
| Retry | retry.policy, maxAttempts, retryableStatus | maxAttempts>0 | 停止 | 与限流协作 |
| Budgeter | budget hints, expectedRequests | 估算合法 | PARTIAL 下发 | 切片粒度调整 |
| Error Taxonomy | L1-L4 分类规则 | 分类完整 | - | 自愈动作矩阵 |
| Re-slicing | trigger thresholds | 阈值数值合法 | 手动触发 | 动态二分 |

## 4. 校验与降级通则
1. 必填缺失 / 字典非法 / 相互冲突 (TOKEN+PAGE 同存) → 编译失败。
2. 功能缺失可降级：无 count → 估算 / 无 hasMore → pageSize 推断；无 updatedSince → 客户端过滤或转 UPDATE。
3. 安全项（鉴权/签名）不可降级；失败直接终止 plan。
4. 降级结果与 Warning 记录在 plan 备注，纳入指纹计算（显式区分正常/降级）。

## 5. 一致性检查清单 (Compile Gate)
1. Auth 与端点要求匹配；受限端点不可 NONE。
2. 分页类型唯一且所需路径齐全。
3. 时间窗支持与排序稳定性兼容；无 updatedSince 时必须具备稳定排序字段。
4. Two-Phase 成对配置且 batch 上限合规。
5. Response 三件套 (items/id/updatedAt) 完整；缺 updatedAt 时不能走 HARVEST。
6. 速率/重试参数不超供应商上限；Retry 与 Rate Gate 不冲突。
7. 字典 code 全部可在 sys_dict 中解析；未识别立即失败。
8. 命名空间策略与操作类型一致 (HARVEST→FORWARD, BACKFILL→BACKFILL, UPDATE→CUSTOM)。
9. 签名钩子/凭据引用存在且可解析。

## 6. 指纹与复用
`spec_fingerprint = HASH(sorted(normalized(auth, http, pagination, window, retry, rate, batching, expr, mapping, twoPhase, warnings)))`
- 触发重复且 fingerprint 相同 → 可复用缓存快照（可选优化开关）。
- 任何策略位差异或降级差异都会改变 fingerprint，保证回放一致性判断。

## 7. 验收 (Strategy DoD)
1. 任一来源配置变化不影响已存在 plan/slice 执行结果。
2. 降级路径全可追溯（Warning 列表 + 指纹）。
3. 失败与降级分类 100% 可被再现（无隐藏分支）。
4. 同一来源在多次触发下的快照差异可解释（通过 diff 指纹分解）。
5. 在线再切片触发条件与策略位 (window/pageSize) 调整存在明确记录。

---
后续：Reference 中给出字段到 Spec 的完整映射表；Ops 中给出调优动作与告警信号。
