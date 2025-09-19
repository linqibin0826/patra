# patra-spring-boot-starter-expr · 使用文档

本模块提供“表达式编译能力”的 Spring Boot Starter，配合 patra-expr-kernel 的 AST 建模接口，对上层提供：
- 表达式建模：使用 com.patra.expr.* 构造与组合查询表达式（不可变、线程安全）。
- 能力校验 + 渲染：将 AST 基于“供应商能力与渲染规则快照”编译为 query 片段与 params 参数。
- 时间切片重写：按时间窗口将大范围查询切分为多个等价的小查询（便于并行与控制时延）。
- 自动装配：开箱即用的编译门面 ExprCompiler，默认接入 patra-registry-api 提供的规则快照。

---

## 1. 依赖与前置

Maven 依赖（父 POM 已聚合，无需额外版本号）：

```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-spring-boot-starter-expr</artifactId>
</dependency>
```

前置（用于默认快照加载）：
- 引入 patra-registry-api，确保容器中存在 Feign Client：com.patra.registry.api.rpc.client.ProvenanceClient。
- 若你不希望依赖 Registry，可自定义一个 RuleSnapshotLoader Bean 覆盖默认实现。

---

## 2. 自动配置与开关

配置项（application.yml）：

```yaml
patra:
  expr:
    compiler:
      enabled: true
      registry-api:
        enabled: true         # 使用 Feign 到 patra-registry 加载规则快照
        operation-default: search  # 默认 operation（未显式传入时采用）
```

装配的 Bean（未被用户自定义覆盖时）：
- RuleSnapshotLoader: 默认 RegistryRuleSnapshotLoader（依赖 LiteratureProvenanceClient）。
- CapabilityChecker: DefaultCapabilityChecker。
- ExprRenderer: DefaultExprRenderer。
- ExprSlicer: DefaultExprSlicer。
- ExprNormalizer: DefaultExprNormalizer（当前直通）。
- ExprCompiler: DefaultExprCompiler（门面，仅当上述依赖齐备时装配）。

覆盖方式：定义同名/同类型 Bean 即可替换默认实现（例如自定义 ExprRenderer）。

---

## 3. 核心对外 API（使用方）

入口门面：com.patra.starter.expr.compiler.ExprCompiler

- hasAtom(Expr expr): Boolean
  - 判断表达式树中是否存在 Atom 叶子。

- compile(Expr expr, ProvenanceCode provenance, String operation, CompileOptions options): CompileResult
  - 完整编译：normalize + validate + render。
  - 返回 CompileResult，包含 query、params、校验报告、规则快照引用、可选渲染轨迹。

- sliceAndRewrite(Expr expr, ProvenanceCode provenance, String operation, SliceOptions options): SliceResult
  - 按时间窗重写表达式（当前仅支持“顶层日期范围按月切片”）。

关键 DTO：
- CompileOptions(strict, maxQueryLength, timezone, traceEnabled)
- CompileResult(query: String, params: Map<String,String>, report: ValidationReport, snapshot: SnapshotRef, trace: RenderTrace|null)
- ValidationReport(warnings: List<Issue>, errors: List<Issue>)，Issue(severity: INFO/WARN/ERROR, code, message, ctx)
- SnapshotRef(provenanceId, provenanceCode, operation, version, updatedAt)
- RenderTrace(hits: List<Hit>)，Hit(fieldKey, op, priority, templateId)
- SliceOptions(primaryDateField, alignTo, targetWindowSize, overlap, maxWindowCount, boundStyle, respectGranularity, strict)
- SliceResult(windows: List<TimeWindow>, rewrittenExprs: List<Expr>, warnings: List<Issue>, snapshot: SnapshotRef)
- TimeWindow(startDate: LocalDate, endDate: LocalDate, boundStyle, datetype)

---

## 4. 表达式建模（patra-expr-kernel）

使用工厂类 com.patra.expr.Exprs 构造 AST：
- and(List<Expr>), or(List<Expr>), not(Expr)
- term(field, value, TextMatch[, caseSensitive])
- in(field, List<String>[, caseSensitive])
- rangeDate(field, LocalDate from, LocalDate to[, includeFrom, includeTo])
- rangeDateTime(field, Instant from, Instant to[, includeFrom, includeTo])
- rangeNumber(field, BigDecimal from, BigDecimal to[, includeFrom, includeTo])
- exists(field, boolean)
- token(kind, value)  // 特殊记号
- constTrue(), constFalse()

所有节点为不可变对象（record/enum），线程安全；渲染与转义不在 AST 层完成。

---

## 5. 快速上手示例

1) 构建表达式

```java
import com.patra.expr.*;
import java.time.LocalDate;

Expr expr = Exprs.and(java.util.List.of(
    Exprs.term("title", "deep learning", TextMatch.PHRASE),
    Exprs.in("lang", java.util.List.of("en", "zh")),
    Exprs.rangeDate("year", LocalDate.of(2018,1,1), LocalDate.of(2020,12,31))
));
```

2) 注入并调用编译

```java
import com.patra.common.enums.ProvenanceCode;
import com.patra.starter.expr.compiler.ExprCompiler;

@Autowired ExprCompiler compiler;

var res = compiler.compile(
    expr,
    ProvenanceCode.PUBMED,        // 数据来源枚举
    "search",                    // operation，可为 null 使用默认
    ExprCompiler.CompileOptions.DEFAULT
);

if (!res.report().ok()) {
    // 存在 ERROR：不返回 query/params；请检查 res.report().errors()
}
String query = res.query();               // 渲染后的 query 片段（聚合 AND）
Map<String,String> params = res.params(); // 供应商参数映射
var snapshot = res.snapshot();            // 使用的规则快照版本（用于审计/幂等）
var trace = res.trace();                  // 若启用 traceEnabled，将包含命中规则摘要
```

3) 时间切片重写（按月）

```java
var slice = compiler.sliceAndRewrite(
    expr,
    ProvenanceCode.PUBMED,
    "search",
    ExprCompiler.SliceOptions.DEFAULT
);
for (int i = 0; i < slice.windows().size(); i++) {
    var w = slice.windows().get(i);
    var subExpr = slice.rewrittenExprs().get(i);
    // 针对每个子窗口 subExpr 再调用 compile() 执行并行检索
}
```

---

## 6. 返回内容与错误处理

- ValidationReport
  - ok() 为 true 表示无 ERROR；存在 WARN 不影响渲染结果（仅提示）。
  - 常见错误码：
    - E-FIELD-NOT-FOUND：字段在当前来源不可用。
    - E-OP-NOT-ALLOWED：字段不允许该操作符（TERM/IN/RANGE/EXISTS/TOKEN）。
    - E-TERM-LEN-MAX/MIN、E-IN-SIZE、E-RANGE-OPEN、E-DATE/TIME 边界超限等。
    - E-QUERY-LEN-MAX：渲染后 query 超长度上限（由 options.maxQueryLength 触发）。
- CompileResult
  - 当存在 ERROR 时，query 为空字符串，params 为空 Map；同时附带 report.errors。
  - traceEnabled=true 时，trace.hits 列出每个命中的渲染规则（字段、操作、优先级、模板 ID）。
- SliceResult
  - windows 与 rewrittenExprs 一一对应；warnings 提示粒度上调、窗口截断等问题。

---

## 7. 能力与限制（当前版本）

- 渲染器 DefaultExprRenderer：
  - 支持 Atom(TERM) 与 Atom(IN)；其余操作符暂不渲染（WARN 跳过）。
  - 仅聚合 AND，遇到 OR/NOT 给出 WARN 并跳过该分支。
  - 渲染规则匹配：fieldKey 全等 + op 相同；TERM 会尝试按 matchType 选择模板。
- 切片器 DefaultExprSlicer：
  - 仅当“顶层为 DateRange Atom 且为主日期字段”时切片；粒度目前只支持 P1M（月）。
  - SliceOptions.strict=true：端点缺失/非法将抛 IllegalArgumentException。
- 快照加载：
  - 默认使用 RegistryRuleSnapshotLoader 通过 Feign 拉取，并按优先级选择渲染模板。
- 线程安全：
  - AST 与返回 DTO 均不可变；编译器内部无共享可变状态（除外部 Loader）。

---

## 8. 自定义扩展

- 替换快照加载：实现 RuleSnapshotLoader 并注册为 Bean。
- 自定义能力校验：实现 CapabilityChecker（决定哪些字段/操作/范围合法）。
- 自定义渲染器：实现 ExprRenderer（可扩展 RANGE/EXISTS/TOKEN、OR/NOT 等）。
- 自定义切片器：实现 ExprSlicer（支持复杂布尔结构、不同时间粒度与对齐策略）。

---

## 9. 常见问题（FAQ）

- Q: 编译返回 WARN，但 query/params 为空？
  - A: 若 report.ok() 为 true，仍应返回渲染结果。若为空，通常是未匹配到渲染规则（W-RULE-NOT-FOUND）。请检查规则快照的 fieldKey/op/matchType 是否与 AST 一致。
- Q: 如何限制渲染后的 query 长度？
  - A: 传入 CompileOptions.maxQueryLength>0，超限会产生 ERROR E-QUERY-LEN-MAX 并短路返回。
- Q: 不想依赖 Registry，如何使用？
  - A: 配置 patra.expr.compiler.registry-api.enabled=false，并自行提供 RuleSnapshotLoader Bean 从本地或其他服务加载规则。

---

## 10. 关联模块（patra-expr-kernel）

- com.patra.expr.Expr：AST 根接口，受限密封 permits And/Or/Not/Const/Atom。
- com.patra.expr.Atom：叶子节点（字段 + 操作符 + 值），支持 TERM/IN/RANGE/EXISTS/TOKEN。
- com.patra.expr.TextMatch：PHRASE/EXACT/ANY 文本匹配策略。
- com.patra.expr.Exprs：工厂方法集合，便于快速、安全地构建表达式树。

以上 API 即使用方的主要建模与调用入口。

