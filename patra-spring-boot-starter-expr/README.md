# patra-spring-boot-starter-expr

## 概述

表达式引擎集成 Starter,将 patra-expr-kernel 与 Spring Boot 生态无缝集成,提供表达式编译、验证、规范化和渲染能力。

本 Starter 自动配置表达式编译器,支持从 patra-registry 加载规则快照,使用标准键方法实现跨数据源的表达式编译。

## 核心功能

- **表达式编译器**: 将领域表达式编译为数据源特定的查询参数
- **规则快照加载**: 从 patra-registry 动态加载数据源规则和映射
- **标准键方法**: 使用与数据源无关的语义键(std_key),支持跨数据源表达式复用
- **表达式工具**: 能力检查、规范化、渲染
- **配置防护**: 支持查询长度限制、参数数量警告和错误

## 自动配置内容

### 自动配置类

**ExprCompilerAutoConfiguration** 自动配置:

| Bean 名称 | 类型 | 描述 |
|-----------|------|------|
| `exprCompiler` | `ExprCompiler` | 主表达式编译器 |
| `ruleSnapshotLoader` | `RuleSnapshotLoader` | 规则快照加载器(从 registry) |
| `capabilityChecker` | `CapabilityChecker` | 表达式能力检查器 |
| `exprNormalizer` | `ExprNormalizer` | 表达式规范化器 |
| `exprRenderer` | `ExprRenderer` | 表达式渲染器 |
| `exprMetrics` | `ExprMetrics` | 指标记录器(可选) |

**ExprFunctionAutoConfiguration** 自动配置:

| Bean 名称 | 类型 | 描述 |
|-----------|------|------|
| `functionRegistry` | `FunctionRegistry` | 渲染函数注册表 |
| `transformRegistry` | `TransformRegistry` | 值转换注册表 |

### 启用条件

- 配置属性 `patra.expr.compiler.enabled=true` (默认启用)
- 配置属性 `patra.expr.compiler.registry-api.enabled=true` (默认启用)
- 需要 `ProvenanceEndpoint` 和 `ExprEndpoint` HTTP Interface 代理存在

## 主要组件

### ExprCompiler

表达式编译器,将领域表达式编译为数据源特定的查询参数:

```java
CompileResult compile(CompileRequest request);
CompileResult compile(Expr expression, ProvenanceCode provenance);
CompileResult compile(Expr expression, ProvenanceCode provenance, String operationType);
```

### 标准键方法

编译器使用标准键(std_key)而非数据源特定参数名:

- 规则仅发出标准键: `query`、`from`、`to`、`datetype`、`filter`
- 数据源映射(来自 registry)将标准键转换为数据源参数,如 PubMed `query→term`
- MULTI 标准键(如 `filter`)默认使用连接转换

**优势**:

- 规则存储在 registry 中,演进数据源通常无需代码变更
- 通过交换规则快照,一个表达式可编译为多个数据源

### 表达式工具

- `CapabilityChecker`: 验证表达式能力(如 NOT 支持)
- `ExprNormalizer`: 规范化表达式结构
- `ExprRenderer`: 将表达式渲染为各种格式

## 配置属性

配置前缀: `patra.expr.compiler`

### 编译器配置

```yaml
patra:
  expr:
    compiler:
      enabled: true  # 启用编译器(默认 true)
      registry-api:
        enabled: true  # 启用基于 registry 的规则加载(默认 true)
        operation-default: "SEARCH"  # 默认操作类型
      query-param-bridge:
        enabled: true  # 启用查询参数桥接(默认 true)
      max-query-length: 5000  # 查询长度限制(0 = 禁用)
      warn-param-count: 50    # 参数数量警告阈值(0 = 禁用)
      max-param-count: 100    # 参数数量错误阈值(0 = 禁用)
```

### 表达式模式配置

```yaml
patra:
  expr:
    mode:
      strict: true  # 严格模式: 缺少函数/转换时快速失败
      multi:
        repeat-enabled: false  # 禁用重复参数,首选 LIST_JOIN/FILTER_JOIN
```

## 使用方式

### Maven 依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-expr</artifactId>
</dependency>
```

### 配置示例

```yaml
patra:
  expr:
    compiler:
      enabled: true
      max-query-length: 5000
      warn-param-count: 50
```

### 代码示例

#### 使用 ExprCompiler

```java
@Component
@RequiredArgsConstructor
public class ExpressionCompilerAdapter implements ExpressionCompilerPort {

    private final ExprCompiler compiler;

    @Override
    public CompileResult compile(Expr expression, ProvenanceCode provenance) {
        // 编译为数据源特定参数
        CompileResult result = compiler.compile(expression, provenance);

        if (!result.isSuccess()) {
            log.warn("表达式编译失败: {}", result.issues());
            throw new CompilationException(result.issues());
        }

        return result;
    }
}
```

#### 构建表达式

```java
@Service
@RequiredArgsConstructor
public class PlanExpressionBuilder {

    public Expr buildDateRangeExpression(Instant from, Instant to) {
        // 构建日期范围表达式
        return Exprs.rangeDate("date", from, to);
    }

    public Expr buildComplexExpression(PlanTriggerNorm norm) {
        // 构建复杂的布尔表达式
        return Exprs.and(List.of(
            Exprs.rangeDate("date", norm.windowFrom(), norm.windowTo()),
            Exprs.term("keyword", norm.searchTerm())
        ));
    }
}
```

## 扩展点

### 自定义函数

实现 `RenderFunction` 接口并注册到 `FunctionRegistry`:

```java
@Component
public class CustomFunction implements RenderFunction {

    @Override
    public String name() {
        return "custom_func";
    }

    @Override
    public JsonNode apply(JsonNode input, RenderContext context) {
        // 自定义逻辑
        return input;
    }
}
```

### 自定义值转换

实现 `ValueTransform` 接口并注册到 `TransformRegistry`:

```java
@Component
public class CustomTransform implements ValueTransform {

    @Override
    public String transformCode() {
        return "CUSTOM_TRANSFORM";
    }

    @Override
    public JsonNode transform(JsonNode value, TransformContext context) {
        // 自定义转换逻辑
        return value;
    }
}
```

## 技术栈

- patra-expr-kernel
- patra-registry-api
- Spring Boot 4.0.1
- Jackson
- Micrometer (可选)
