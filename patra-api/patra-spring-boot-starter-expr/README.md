## 模块：patra-spring-boot-starter-expr

面向 Spring Boot 的“表达式编译流水线” Starter。基于 `patra-expr-kernel` 的不可变 AST，结合注册中心规则快照（字段/能力/渲染/参数映射）完成：

1. 规范化（Normalization）
2. 能力与合规校验（Capability Checking）
3. 渲染（Rendering）→ 生成外部检索/请求所需的查询字符串片段与参数映射
4. 长度/严格模式约束 & 诊断（ValidationReport + RenderTrace）

保持“无状态 + 可替换”设计，每个阶段都可以通过自定义 Bean 替换。

---

## 1. 依赖引入

```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-spring-boot-starter-expr</artifactId>
</dependency>
```

运行期需要（通常由其他 starter 提供）：
- `ProvenanceClient`、`ExprClient`（来自 `patra-registry-api`）以获取规则快照。

---

## 2. 自动配置与属性

YAML 示例：
```yaml
patra:
  expr:
    compiler:
      enabled: true            # 关闭后不装配编译相关 Bean
      registry-api:
        enabled: true          # 是否通过注册中心拉取规则
        operation-default: SEARCH
```

属性映射到 `CompilerProperties`：

| 属性 | 默认值 | 说明 |
|------|--------|------|
| patra.expr.compiler.enabled | true | 是否启用表达式编译流水线 |
| patra.expr.compiler.registry-api.enabled | true | 启用注册中心规则加载 |
| patra.expr.compiler.registry-api.operation-default | SEARCH | 无显式操作代码时的默认值 |

自动装配（满足条件且未自定义 Bean 时）：

| Bean 类型 | 默认实现 | 条件 |
|-----------|----------|------|
| RuleSnapshotLoader | RegistryRuleSnapshotLoader | 存在 ProvenanceClient & ExprClient & 属性开启 |
| CapabilityChecker | DefaultCapabilityChecker | 缺省实现未被覆盖 |
| ExprNormalizer | DefaultExprNormalizer | 同上 |
| ExprRenderer | DefaultExprRenderer | 同上 |
| ExprCompiler | DefaultExprCompiler | 以上依赖均存在且 enabled |

---

## 3. 编译阶段流水线

顺序（`DefaultExprCompiler#compile`）：
1. 加载规则快照：`RuleSnapshotLoader.load(provenance, taskType, operationCode)` → `ProvenanceSnapshot`
2. 规范化：`ExprNormalizer.normalize(expr)`（去空/折叠/布尔化简：AND/OR 展开、NOT 双重抵消、IN 去重转 TERM、空集 → FALSE）
3. 能力校验：`CapabilityChecker.check(normalized, snapshot, strict)` → Issues（分级：WARNING / ERROR）
4. 若存在 ERROR → 返回空查询 + 报告
5. 渲染：`ExprRenderer.render(normalized, snapshot, traceEnabled)` 生成 query 片段 + params + 渲染警告
6. 合并警告；长度预算检查（`maxQueryLength`）→ 超出加入 ERROR
7. 返回 `CompileResult(query, params, normalized, ValidationReport, SnapshotRef, RenderTrace?)`

复杂度关注点：
- 正常情况下 O(N) 遍历（N 为节点数）；渲染规则筛选使用 stream 线性过滤，可按需后续优化为索引。

---

## 4. 核心类型职责

| 类 | 职责 | 说明 |
|----|------|------|
| ExprCompiler / DefaultExprCompiler | 编译协调器 | 串联各阶段并施加长度/trace 逻辑 |
| RuleSnapshotLoader | 加载规则快照 | 默认从注册中心（表集合）拼装 | 
| ProvenanceSnapshot | 字段/能力/渲染/参数映射聚合 | 提供渲染选择所需数据 |
| ExprNormalizer / DefaultExprNormalizer | AST 规范化与简化 | 去冗余 / 常量折叠 / IN 简化 |
| CapabilityChecker / DefaultCapabilityChecker | 规则/能力校验 | 生成 Issue 列表（错误/警告）|
| ExprRenderer / DefaultExprRenderer | 查询与参数渲染 | 依据 RenderRule + 占位符替换 |
| CompileRequest / Builder | 输入载体 | expression + provenance + task/op + options |
| CompileOptions | 行为开关 | strict / maxQueryLength / timezone / trace |
| CompileResult | 输出 | query / params / normalized / report / snapshotRef / trace |
| ValidationReport | 诊断聚合 | warnings + errors |
| Issue | 单条诊断 | code + message + context + severity |
| RenderTrace | 规则命中轨迹 | 仅 traceEnabled=true 时提供 |

---

## 5. 能力校验 (DefaultCapabilityChecker)

校验要点：
- 字段存在性（E-FIELD-NOT-FOUND / E-CAPABILITY-MISSING）
- 算子允许列表（E-OP-NOT-ALLOWED）
- NOT 支持与否（E-NOT-UNSUPPORTED / E-NOT-OP-UNSUPPORTED）
- TERM：空值 / 长度范围 / Pattern / 大小写敏感策略 / 匹配策略集合
- IN：元素数量 / case 规则；空 → ERROR
- RANGE：类型匹配（DATE/DATETIME/NUMBER）、边界至少一个、日期上下界、范围类别错误（E-RANGE-KIND）
- EXISTS：支持标记
- TOKEN：类型集合 / value 正则 / 严格模式下空值

Severity：ERROR 阻断渲染；WARNING 不阻断但写入报告。

---

## 6. 规范化 (DefaultExprNormalizer) 规则

| 场景 | 规则 |
|------|------|
| TERM | 去左右空白；若无变化返回原节点 |
| IN | 去空白/空值/重复（按 case 策略）；1 项 → TERM(PHRASE)；0 项 → Const.FALSE |
| AND/OR | 展平嵌套同类；常量折叠（AND含 FALSE→FALSE / 全 TRUE→TRUE；OR含 TRUE→TRUE / 全 FALSE→FALSE）；单子提升 |
| NOT | 双重 NOT 消除；对 Const 取反 |
| 结果稳定性 | 使用 LinkedHashSet 保持首次出现顺序去重 |

---

## 7. 渲染 (DefaultExprRenderer)

选择规则维度：`fieldKey + operator + emitType(QUERY|PARAMS) + negation + matchTypeCode + valueType`；优先级取 `priority` 最大值。

支持：
- QUERY: 当前已实现 TERM / IN；RANGE / EXISTS / TOKEN 若无规则 → W-RENDER-RULE-MISSING
- IN 渲染：itemTemplate → joiner → 可包裹括号；支持参数与查询规则分离
- PARAMS: 遍历 rule.params 标准键 → `apiParameterMap` 映射到 provider 参数；缺失映射 → W-PARAM-MAP-MISSING
- OR / NOT 暂未渲染（发出 W-BOOL-OR-UNSUPPORTED / W-BOOL-NOT-UNSUPPORTED）

Trace：记录命中规则（field + operator + priority + ruleId），以及参数规则命中。

---

## 8. 编译选项 (CompileOptions)

| 选项 | 默认 | 作用 |
|------|------|------|
| strict | true | 严格模式下 TOKEN 空值等被视为错误 |
| maxQueryLength | 0 | >0 时对最终 query 长度做硬限制 |
| timezone | UTC | 预留时区上下文（渲染/后续扩展） |
| traceEnabled | false | 启用渲染轨迹（性能略降） |

---

## 9. 使用示例

```java
@Autowired
ExprCompiler compiler;

Expr expr = Exprs.and(List.of(
    Exprs.term("title", "machine learning", TextMatch.PHRASE),
    Exprs.in("lang", List.of("en", "zh"))
));

// 基础编译（默认 SEARCH 操作 / strict=true）
CompileResult r1 = compiler.compile(expr, ProvenanceCode.PUBMED);

// 指定任务类型
CompileResult r2 = compiler.compile(expr, ProvenanceCode.PUBMED, TaskTypes.UPDATE);

// 指定任务 + 操作类型
CompileResult r3 = compiler.compile(expr, ProvenanceCode.PUBMED, TaskTypes.UPDATE, OperationCodes.SEARCH);

// 自定义选项
CompileRequest req = CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
    .forTask(TaskTypes.UPDATE)
    .forOperation(OperationCodes.SEARCH)
    .withStrict(false)
    .withTraceEnabled(true)
    .withMaxQueryLength(2000)
    .build();
CompileResult r4 = compiler.compile(req);

if (!r4.report().errors().isEmpty()) {
    // 处理错误
}
```

---

## 10. CompileResult 字段说明

| 字段 | 说明 |
|------|------|
| query | 渲染后的查询片段（多个片段用 AND 拼接；可能为空） |
| params | 提取出的 provider 参数键值对（已按标准键映射） |
| normalized | 规范化后的表达式（可用于缓存） |
| report | ValidationReport（warnings + errors） |
| snapshot | SnapshotRef（provenanceId + code + operation + version + capturedAt） |
| trace | 可选渲染轨迹（traceEnabled=true） |

---

## 11. 常见错误与警告代码

| 代码前缀 | 范畴 |
|----------|------|
| E-FIELD-* | 字段相关错误 |
| E-OP-* | 算子支持性错误 |
| E-TERM-* | Term 相关（长度/模式/策略） |
| E-IN-* | IN 相关（数量等） |
| E-RANGE-* | Range 类型/边界错误 |
| E-TOKEN-* | Token 类型/值错误 |
| E-QUERY-LEN-MAX | 查询长度超限 |
| W-BOOL-* | 布尔逻辑未渲染（OR/NOT） |
| W-RENDER-RULE-MISSING | 缺少查询渲染规则 |
| W-PARAM-MAP-MISSING | 标准参数键缺少映射 |
| W-CONST-FALSE | 表达式恒假 |

---

## 12. 扩展点（SPI 风格）

| 扩展 | 接口 | 典型场景 |
|------|------|----------|
| 快照来源 | RuleSnapshotLoader | 自定义缓存 / 本地文件 / 聚合多来源 |
| 校验策略 | CapabilityChecker | 引入额外业务约束（例如权限）|
| 规范化 | ExprNormalizer | 新的语法糖折叠（XOR → (A OR B) AND NOT (A AND B)）|
| 渲染 | ExprRenderer | 支持新的 MATCH 策略 / RANGE / EXISTS / TOKEN 渲染 |

自定义 Bean 同类型覆盖默认实现，无需排除自动配置。

---

## 13. 性能建议

| 问题 | 建议 |
|------|------|
| 重复编译同一表达式 | 先对表达式做 canonical/hash（来自 kernel）做缓存层 | 
| 大 IN 列表去重成本 | 构建前先清洗列表；超过阈值分批渲染 |
| 渲染规则线性匹配 | 后续可在自定义 Renderer 内构建多级索引 (field + op + emitType) |
| Trace 额外开销 | 仅调试时开启 traceEnabled |

---

## 14. 迁移指引

| 旧做法 | 新建议 |
|--------|--------|
| 手写字符串拼接查询 | 使用 AST + 编译器，便于验证/重写 |
| 自己校验字段合法性 | 迁移到 CapabilityChecker / ProvenanceSnapshot | 
| 重复使用原始表达式对象 | 先规范化再缓存 normalized 版本 |

---

## 15. Roadmap

| 优先级 | 项目 | 描述 |
|--------|------|------|
| High | RANGE 渲染模板 | 支持 DATE/DATETIME/NUMBER 规则化渲染 |
| High | EXISTS/TOKEN 渲染 | 补齐差异场景模板 |
| Mid | OR/NOT 渲染 | 提供可选布尔扩展（DeMorgan 展开或原生模板） |
| Mid | 渲染规则索引优化 | 降低大规模规则表线性扫描开销 |
| Low | 规范化更多模式 | A AND A → A, (A AND TRUE) → A 已有 → 扩展互斥剪枝 |
| Low | CompileResult 工具 | 快速判断 success / toProblemDetail 辅助方法 |

---

## 16. FAQ

| 问题 | 回答 |
|------|------|
| 为什么 OR / NOT 只产生警告不渲染? | 先行交付 MVP，等待规则模型稳定后再引入布尔组合渲染策略 |
| 能否新增 compileWithDefaults 等快捷 API? | 当前接口已足够精简；如需别名可在业务层封装门面服务 |
| 如何判断编译成功? | `CompileResult.report().errors()` 为空即可；是否有警告不影响执行 |
| 是否需要手动并发控制? | 编译器无状态且线程安全，可直接被多线程共享 |

---

## 17. 参考代码位置

| 主题 | 文件 |
|------|------|
| 自动配置 | `compiler/boot/ExprCompilerAutoConfiguration.java` |
| 属性定义 | `compiler/boot/CompilerProperties.java` |
| 编译调度 | `compiler/DefaultExprCompiler.java` |
| 校验 | `compiler/check/DefaultCapabilityChecker.java` |
| 规范化 | `compiler/normalize/DefaultExprNormalizer.java` |
| 渲染 | `compiler/render/DefaultExprRenderer.java` |
| 请求构建 | `compiler/model/CompileRequestBuilder.java` |
| 选项 | `compiler/model/CompileOptions.java` |
| 输出 | `compiler/model/CompileResult.java` |

---

如需扩展/遇到问题，请在提交 Issue 时附：provenance、operationCode、taskType、表达式 JSON、normalized 结果、警告/错误列表、期望 query。

