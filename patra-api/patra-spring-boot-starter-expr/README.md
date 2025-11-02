# patra-spring-boot-starter-expr

> 表达式引擎集成——自动配置表达式构建器、验证器和规则快照加载器。

## 📌 目的

将 **patra-expr-kernel** 与 Spring Boot 集成:
- 支持规则快照加载的表达式编译器
- 表达式构建器 Bean
- 自动配置的规范化器
- 表达式验证支持
- JSON 编解码器 Bean

---

## 🧭 标准键方法(配置内容 vs 发送内容)

编译器在渲染期间使用与提供商无关的语义键(std_key),并在最后将其桥接到提供商参数。

- 渲染规则仅发出标准键,如 `query`、`from`、`to`、`datetype`、`filter`——从不使用提供商名称。
- 提供商特定的参数映射(来自 `patra-registry`)将 std_key 转换为提供商参数(例如,PubMed `query→term`)。
- MULTI 标准键(例如 `filter`)默认使用连接转换;如果提供商需要,可以通过配置启用重复参数。

为什么这很重要:
- 种子(规则/映射)存储在 registry 中;演进提供商通常不需要代码变更。
- 通过交换种子,一个表达式可以编译为多个提供商。

配置控制的关键行为:

```yaml
# 安全模式
expr:
  strict: true                 # 在缺少函数/转换、不支持的 NOT 时快速失败
  multi:
    repeat-enabled: false      # 默认关闭;首选 LIST_JOIN/FILTER_JOIN

# 编译器防护措施
patra:
  expr:
    compiler:
      query-param-bridge:
        enabled: true          # 通过 std_key=query 桥接聚合的布尔查询
      max-query-length: 5000   # 硬性限制(0 禁用)
      warn-param-count: 50     # 软性警告(0 禁用)
      max-param-count: 100     # 硬性错误(0 禁用)
```

参见文档: `docs/expr/01-overview.md`、`docs/expr/02-architecture.md`、`docs/expr/03-compiler-bridge-internals.md`。

## 🔧 自动配置

### ExprCompiler (基于 Registry)

当 classpath 中存在 `patra-registry-api` 时,自动配置:
- `SnapshotAssembler` - 将 registry DTO 转换为规则快照
- `RuleSnapshotLoader` - 通过 Feign 客户端从 `patra-registry` 加载规则
- `ExprCompiler` - 带规则验证的主表达式编译器

**要求:**
- `ProvenanceClient` 和 `ExprClient` Feign 接口存在(通过 `patra-spring-cloud-starter-feign` 自动发现)
- `patra.expr.compiler.registry-api.enabled=true` (默认)

**配置:**
```yaml
patra:
  expr:
    compiler:
      enabled: true  # 启用编译器
      registry-api:
        enabled: true  # 启用基于 registry 的规则加载
```

### 表达式工具

- `CapabilityChecker` - 验证表达式能力
- `ExprNormalizer` - 规范化表达式
- `ExprRenderer` - 将表达式渲染为各种格式

## 🔗 依赖

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-expr</artifactId>
</dependency>
```

包含: `patra-expr-kernel`、Jackson

## 🚀 用法

### 使用 ExprCompiler

```java
@Component
@RequiredArgsConstructor
public class ExpressionCompilerPortImpl implements ExpressionCompilerPort {

    private final ExprCompiler compiler;  // 自动注入

    @Override
    public CompileResult compile(String exprJson, ProvenanceCode code) {
        return compiler.compile(exprJson, code);
    }
}
```

### 使用表达式工具

```java
@Service
@RequiredArgsConstructor
public class PlanExpressionBuilder {

    public PlanExpressionDescriptor build(PlanTriggerNorm norm, ProvenanceConfigSnapshot config) {
        Expr expr = Exprs.and(List.of(
            Exprs.rangeDate("date", norm.windowFrom(), norm.windowTo())
        ));

        ExprCanonicalSnapshot snapshot = ExprCanonicalizer.canonicalize(expr);
        return new PlanExpressionDescriptor(snapshot.hash(), snapshot.canonicalJson());
    }
}
```

## 🔗 相关文档

- [主 README](../README.md)
- [patra-spring-cloud-starter-feign](../patra-spring-cloud-starter-feign/README.md) - Feign 客户端自动配置
- [patra-expr-kernel](../patra-expr-kernel/README.md) - 表达式引擎核心

---

**最后更新**: 2025-10-14
