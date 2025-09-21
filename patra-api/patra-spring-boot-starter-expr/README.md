# patra-spring-boot-starter-expr

Spring Boot starter providing the Papertrace expression compilation pipeline. It combines the immutable AST from `patra-expr-kernel` with registry backed capability metadata and renders provider specific query fragments + parameter maps.

## Modules at a Glance

- `patra-expr-kernel`: sealed AST (`Expr`, `Atom`, `And`, `Or`, `Not`, `Const`) and factories in `Exprs`.
- `patra-spring-boot-starter-expr`: normalisation, capability validation, rendering, rule snapshot integration and Spring Boot auto configuration.

## Dependency

```xml
<dependency>
  <groupId>com.papertrace</groupId>
  <artifactId>patra-spring-boot-starter-expr</artifactId>
</dependency>
```

The starter expects Feign clients for `ProvenanceClient` and `ExprClient` (supplied by `patra-registry-api`).

## Auto Configuration

```yaml
patra:
  expr:
    compiler:
      enabled: true
      registry-api:
        enabled: true          # use Registry to fetch render/capability snapshots
        operation-default: SEARCH
```

Beans provided when not overridden by the user:

- `RuleSnapshotLoader` → `RegistryRuleSnapshotLoader`
- `CapabilityChecker` → `DefaultCapabilityChecker`
- `ExprNormalizer` → `DefaultExprNormalizer`
- `ExprRenderer` → `DefaultExprRenderer`
- `ExprCompiler` → `DefaultExprCompiler`

Override any of the above by registering your own bean of the same type.

## Core API

### 基础用法

```java
@Autowired
ExprCompiler compiler;

// 最简单的使用方式：使用默认配置
CompileResult result = compiler.compile(expr, ProvenanceCode.PUBMED);

// 指定任务类型
CompileResult result = compiler.compile(expr, ProvenanceCode.PUBMED, TaskTypes.UPDATE);

// 指定任务类型和操作
CompileResult result = compiler.compile(expr, ProvenanceCode.PUBMED, TaskTypes.UPDATE, OperationCodes.SEARCH);
```

### 新增的便捷方法 (v0.2.0+)

为了简化常见场景的使用，我们新增了以下便捷方法：

```java
@Autowired
ExprCompiler compiler;

// 使用默认配置编译（推荐用于简单场景）
CompileResult result = compiler.compileWithDefaults(expr);

// 预设的场景化方法
CompileResult result1 = compiler.compileForContentFilter(expr);
CompileResult result2 = compiler.compileForAccessControl(expr);
CompileResult result3 = compiler.compileForDataValidation(expr);

// 仅验证表达式而不生成执行计划
CompileResult result = compiler.validateOnly(expr);
```

### 高级用法

```java
// 使用 Builder 进行复杂配置
var request = CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
    .forTask(TaskTypes.UPDATE)
    .forOperation(OperationCodes.SEARCH)
    .withStrict(false)
    .withTraceEnabled(true)
    .withMaxQueryLength(1000)
    .build();

CompileResult result = compiler.compile(request);

// 新增的 CompileRequestBuilder（简化版本）
CompileRequest request = CompileRequestBuilder.builder()
    .expression(expr)
    .operationCode(CommonOperationCodes.CONTENT_SEARCH)
    .taskType(CommonTaskTypes.REAL_TIME_QUERY)
    .enableOptimization(true)
    .build();

CompileResult result = compiler.compile(request);

// 传统方式（仍然支持）
var request = new CompileRequest(
    expr,                      // com.patra.expr.Expr
    ProvenanceCode.PUBMED,     // registry provenance
    TaskTypes.UPDATE,          // taskType (optional)
    OperationCodes.SEARCH,     // operation code (normalised to upper case)
    CompileOptions.defaults().withTraceEnabled(true)
);
CompileResult result = compiler.compile(request);
```

### 常用常量

```java
// 操作类型常量
OperationCodes.SEARCH  // 搜索操作（默认）
OperationCodes.DETAIL  // 详情获取
OperationCodes.LIST    // 列表查询
OperationCodes.COUNT   // 计数查询
OperationCodes.FETCH   // 获取操作
OperationCodes.EXPORT  // 导出操作

// 任务类型常量
TaskTypes.UPDATE    // 增量更新（最常用）
TaskTypes.FULL      // 全量同步
TaskTypes.SEARCH    // 交互式搜索
TaskTypes.MONITOR   // 监控检查
TaskTypes.VALIDATE  // 数据验证
TaskTypes.EXPORT    // 数据导出

// 新增的便捷常量类
CommonOperationCodes.CONTENT_SEARCH    // 内容搜索
CommonOperationCodes.CONTENT_FILTER    // 内容过滤
CommonOperationCodes.ACCESS_CONTROL    // 访问控制
CommonOperationCodes.DATA_VALIDATION   // 数据验证

CommonTaskTypes.REAL_TIME_QUERY     // 实时查询
CommonTaskTypes.BATCH_PROCESSING    // 批处理
CommonTaskTypes.BATCH_VALIDATION    // 批量验证
CommonTaskTypes.STREAM_PROCESSING   // 流处理
```

`CompileResult` exposes the rendered query string, provider parameters, the aggregated `ValidationReport` (warnings + errors), and a `SnapshotRef` describing the provenance snapshot used. When trace mode is enabled the `RenderTrace` lists the rules that participated in rendering.

### Validation & Errors

`DefaultCapabilityChecker` walks the AST and enforces the capability profile coming from the registry snapshot. Typical error codes:

- `E-FIELD-NOT-FOUND` – field not registered for the provenance.
- `E-OP-NOT-ALLOWED` – operator not allowed for the field.
- `E-TERM-LEN-*` / `E-TERM-PATTERN` – term violations.
- `E-IN-*` – invalid `IN` clauses.
- `E-RANGE-*` – range kind / boundary issues.
- `E-QUERY-LEN-MAX` – rendered query exceeds the configured budget.

Warnings are emitted for skipped branches (e.g. unsupported `OR`/`NOT`) or missing render rules/parameter mappings.

### Rendering

The renderer matches `ProvenanceSnapshot.RenderRule` entries using `fieldKey`, `Atom.Operator`, match type, negation qualifier and value type. TERM and IN are rendered today; RANGE/EXISTS/TOKEN result in validation only until registry templates are available. Parameter rules rely on `reg_prov_api_param_map` mappings; missing entries surface as warnings.

## Snapshot Model

`RegistryRuleSnapshotLoader` materialises the following view from the registry:

- Field dictionary (`reg_expr_field_dict`): `FieldDefinition`
- Capability matrix (`reg_prov_expr_capability`): `Capability`
- Render rules (`reg_prov_expr_render_rule`): `RenderRule`
- Parameter mapping (`reg_prov_api_param_map`): `ApiParameter`

The loader lazily hydrates standard key mappings and tolerates missing definitions (reported as warnings during compilation). Custom loaders can implement `RuleSnapshotLoader` to fetch from other sources (files, caches, etc.).

## AST Construction Helpers

`patra-expr-kernel` exposes `Exprs` with strongly typed factory methods:

```java
Expr expr = Exprs.and(List.of(
    Exprs.term("title", "deep learning", TextMatch.PHRASE),
    Exprs.in("lang", List.of("en", "zh")),
    Exprs.rangeDate("year", LocalDate.of(2019, 1, 1), LocalDate.of(2020, 12, 31))
));
```

Records ensure immutability; the visitor API on `Expr` enables reflective-free traversal in user code.

## Extending the Pipeline

- Swap snapshots: implement `RuleSnapshotLoader` and register it as a bean.
- Adjust validation: provide your own `CapabilityChecker`.
- Expand rendering: implement `ExprRenderer` (e.g. to support `RANGE` or advanced templating).
- Modify normalisation: replace `ExprNormalizer` to inject custom simplification rules.

All components are stateless and thread-safe; feel free to scope custom beans as singletons.

## 最佳实践与迁移指南

### 选择合适的 API

1. **简单场景**：使用 `compileWithDefaults()` 或预设方法
   ```java
   // ✅ 推荐：简单直接
   CompileResult result = compiler.compileWithDefaults(expr);
   ```

2. **需要特定配置**：使用新的 CompileRequestBuilder
   ```java
   // ✅ 推荐：清晰的配置
   CompileRequest request = CompileRequestBuilder.builder()
       .expression(expr)
       .operationCode(CommonOperationCodes.CONTENT_SEARCH)
       .taskType(CommonTaskTypes.REAL_TIME_QUERY)
       .build();
   ```

3. **复杂的高级配置**：使用原有的 CompileRequestBuilder.of()
   ```java
   // ✅ 适用于高级场景
   var request = CompileRequestBuilder.of(expr, ProvenanceCode.PUBMED)
       .withTraceEnabled(true)
       .withMaxQueryLength(1000)
       .build();
   ```

### 常量的使用

使用预定义常量而非魔法字符串：

```java
// ✅ 推荐
.operationCode(CommonOperationCodes.CONTENT_SEARCH)
.taskType(CommonTaskTypes.REAL_TIME_QUERY)

// ❌ 不推荐
.operationCode("SEARCH")
.taskType("REAL_TIME")
```

### 错误处理模式

```java
CompileResult result = compiler.compile(request);

if (result.isSuccess()) {
    // 处理成功情况
    String executionPlan = result.executionPlan();
    
    // 检查警告
    ValidationReport report = result.validationReport();
    if (!report.warnings().isEmpty()) {
        log.warn("编译警告: {}", report.warnings());
    }
} else {
    // 处理失败情况
    ValidationReport report = result.validationReport();
    throw new ExpressionCompilationException(report.errors());
}
```

### 从旧 API 迁移

如果你之前使用的是复杂的 CompileRequest 构造：

```java
// 旧方式
var request = new CompileRequest();
request.setExpression(expr);
request.setOperationCode("SEARCH");
request.setTaskType("REAL_TIME");

// 新方式（推荐）
CompileRequest request = CompileRequestBuilder.builder()
    .expression(expr)
    .operationCode(CommonOperationCodes.CONTENT_SEARCH)
    .taskType(CommonTaskTypes.REAL_TIME_QUERY)
    .build();
```
