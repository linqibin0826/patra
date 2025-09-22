# Ingest · Strategy Reference

导航：Strategy Schema | Guide | Strategy[本页] | Ops | Examples ｜ 总览：`../README.md`

## 1. 策略位映射主表
| 分类 | Strategy Bit | Registry Source | Spec Field(s) | Validation | Degrade Path | Runtime Notes |
|------|--------------|-----------------|---------------|-----------|--------------|---------------|
| 基础 | provenance | reg_provenance / reg_prov_endpoint | spec.provenance.code, endpoint.name | 必填且存在 | - | 选择端点驱动后续能力矩阵 |
| 基础 | operation | reg_prov_operation | spec.operation.code | 必填 HARVEST/BACKFILL/UPDATE/METRICS | - | 影响命名空间/窗口语义 |
| 表达式 | expr.proto | reg_expr_proto_catalog | spec.expression.raw / ast | 语法/AST校验 | BLOCK | 编译成规范 AST hash |
| 表达式 | expr.localization | （内部） | slice.boundaries -> expr localized | slice 分裂后需成功局部化 | RETRY(构建) | 失败则 slice 标记 FAILED |
| 窗口 | window.total | reg_prov_window_policy | window.from,to (ISO) | from<to & within allowed range | SHIFT/LIMIT | 用于首次 plan 切片总范围 |
| 窗口 | window.safetyLag | reg_prov_window_policy | window.safetyLagSeconds | 数值>=0 且 <= maxLag | DEFAULT provider | 影响 run 截止点裁剪 |
| 切片 | slice.strategy | reg_prov_slice_cfg | slice.strategy=TIME/ID_RANGE/... | strategy 在 endpoint 支持集 | FALLBACK TIME | 选择不同局部化分裂算法 |
| 切片 | slice.params | reg_prov_slice_cfg | slice.params.step/zone/budget | 与 strategy 匹配 | FALLBACK provider default | 影响 slice spec fingerprint |
| 分页 | pagination.type | reg_prov_pagination_cfg | pagination.type=TOKEN/PAGE/OFFSET | endpoint 支持 | FALLBACK OFFSET | 驱动 batch 提交模式 |
| 分页 | pagination.pageSize | reg_prov_pagination_cfg | pagination.pageSize | 1..maxPageSize | CLAMP max | 影响速率/预算计算 |
| 分页 | pagination.nextTokenPath | reg_prov_pagination_cfg | pagination.nextTokenPath | TOKEN 类型必填 | FALLBACK END | JSONPath 提取下一令牌 |
| 速率 | rate.baseRps | reg_prov_rate_limit | rate.base | base>0 & <=provider.max | SET provider.max | AIMD 初始速率 |
| 速率 | rate.aimd | reg_prov_rate_limit | rate.aimd.enabled,inc,dec | inc/dec 合法区间 | DISABLE AIMD | 自适应调优 |
| 预算 | budget.daily | reg_prov_budget_cfg | budget.daily | >=0 & <=provider.maxDaily | CLAMP provider.maxDaily | 跨 run 全局预算扣减 |
| 重试 | retry.policy | reg_prov_retry_cfg | retry.maxAttempts,backoff.type | 值域合法 | USE provider default | 影响 run 内失败恢复 |
| 超时 | http.timeout | reg_prov_http_cfg | http.timeoutMs | 50..provider.max | CLAMP | 单请求超时控制 |
| HTTP | http.auth.type | reg_prov_credential_schema | auth.type=KEY/BEARER/BASIC | 需要凭据则 credential 存在 | BLOCK | 驱动 header 注入策略 |
| HTTP | http.headers.merge | reg_prov_http_cfg | http.headers[] | key 合法/不冲突保留 | DROP invalid | 动态合并静态头 |
| 过滤 | filter.changedSince | reg_prov_capability | filter.changedSince.enabled | operation=UPDATE 可用 | IGNORE | 决定窗口截断逻辑 |
| 数据 | dedupe.key | reg_prov_capability | output.dedupe.keyPath | JSONPath 可解析 | DISABLE | 记录幂等范围标识 |
| 数据 | projection.fields | reg_prov_capability | output.projection.fields[] | 字段存在 provider schema | DROP invalid field | 减少传输/解析成本 |
| 安全 | guard.maxErrorsPct | reg_prov_guardrail_cfg | guard.maxErrorsPct | 0..100 | SET default | 触发 run fail-fast |
| 安全 | guard.maxLagSeconds | reg_prov_guardrail_cfg | guard.maxLagSeconds | >=safetyLag | EXTEND | 检测长时间停滞 |

## 2. 分页模式细分
| pagination.type | 触发条件 | before_token 定义 | next 获取方式 | 终止条件 | 适用窗口 | 备注 |
|-----------------|-----------|-------------------|---------------|-----------|----------|------|
| TOKEN | endpoint 支持 token scroll | 上批 after_token | JSONPath(nextTokenPath) | nextTokenPath 缺失/空 | 时间/ID | 与窗口组合裁剪 |
| PAGE | 明确 pageNo 模式 | page_no=n | page_no+1 | page_no*size >= total | 时间/ID | total 需可获取 |
| OFFSET | 仅支持 offset+limit | offset=n*size | offset+size | 返回记录 < size | 时间/ID | 深分页性能风险 |
| HYBRID | TOKEN+PAGE fallback | token 优先 | token 或 page | 两者均失效 | 时间/ID | 平滑降级 |

## 3. 窗口与 SafetyLag 映射
| 字段 | 来源策略 | 语义 | 验证 | 回退 | 影响阶段 |
|------|----------|------|------|------|---------|
| window.from | window.total | 起点(含) | < window.to | BLOCK | 初次切片 |
| window.to | window.total | 终点(不含) | > window.from | BLOCK | 初次切片 |
| safetyLagSeconds | window.safetyLag | 晚到缓冲 | >=0 & <=maxLag | DEFAULT | run 截止裁剪 |
| effectiveTo | (内部裁剪) | 实际截止点 | min(window.to, now-safetyLag) | - | runtime 计算 |

## 4. Rate & Budget 归并
| 策略 | 字段 | 单位 | 优先级(高→低) | 限制 | 降级 | 影响 |
|------|------|------|--------------|------|------|------|
| Rate | rate.base | req/s | spec>provider.default>global | >0 | set provider.default | AIMD 初始值 |
| Rate | rate.aimd.inc | Δreq/s | spec>provider.default | 0<inc<=maxInc | set maxInc | 自适应加速 |
| Rate | rate.aimd.dec | 百分比 | spec>provider.default | 0<dec<=100 | set default | 自适应回退 |
| Budget | budget.daily | requests | spec<=provider.maxDaily | >=0 | clamp | 跨 run 扣减 |

## 5. 校验规则引用
详见 schema-design 第 5 节 “验证关卡”。此处不重复。

## 6. 幂等键构成公式（复述便于就近查阅）
- Task: SHA256(slice_signature_hash + expr_hash + operation_code + trigger + normalized(params))
- Batch: SHA256(run_id + before_token|page_no)
- CursorEvent: SHA256(source,op,key,ns_scope,ns_key,prev->new,window,run_id,...)

## 7. 降级策略一览
| 场景 | 触发条件 | 降级路径 | 可观测指标 | 用户提示 |
|------|----------|----------|------------|----------|
| 不支持的 slice.strategy | endpoint 未声明支持 | 切换 TIME + step=provider.default | strategy.degrade.count | WARN: degrade->TIME |
| TOKEN 分页失效 | 连续 3 次 nextToken 缺失 | 切换 OFFSET | pagination.degrade.count | WARN: token->offset |
| 超出 pageSize | pageSize>max | CLAMP 到 max | pagination.clamp.count | INFO: clamp pageSize |
| AIMD 配置非法 | inc/dec 非法 | 禁用 AIMD | rate.aimd.disable.count | WARN: aimd disabled |
| 安全阈值缺失 | guard 未配置 | 应用 provider.default | guard.default.apply.count | INFO: guard defaults |

## 8. 运行期注入参数优先级
spec > registry provider override > global default；为空取下层；最终值写入 plan/slice 快照以保证重放可复现。
