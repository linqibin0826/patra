# patra-ingest-expr

## 能力

ingest 的表达式引擎：框架无关的表达式 AST（`Expr`、`And` / `Or` / `Not`、`Atom`、`Exprs` 工厂、规范化与 JSON 编解码，
原 expr-kernel）加编译器（原 starter-expr）。编译器把领域表达式按 registry 下发的规则快照编译成各数据源的查询参数，
用与数据源无关的标准键（std_key）跨数据源复用。自动配置 `ExprCompilerAutoConfiguration` 注册
`exprCompiler`、`ruleSnapshotLoader`、能力检查、规范化与渲染器，`ExprFunctionAutoConfiguration` 注册内置函数。

## 配置（前缀 `patra.ingest.expr`）

配置类 `CompilerProperties`（`patra.ingest.expr.compiler`）与 `ExprModeProperties`（`patra.ingest.expr.mode`），都是可变类。

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `compiler.enabled` | boolean | `true` | 编译器总开关 |
| `compiler.registry-api.enabled` | boolean | `true` | 是否从 registry 加载规则快照 |
| `compiler.registry-api.operation-default` | String | `SEARCH` | 没给操作类型时的默认操作 |
| `compiler.query-param-bridge.enabled` | boolean | `true` | 是否把编译结果桥接成查询参数 |
| `compiler.max-query-length` | int | `0` | 查询串长度上限，`0` 不限 |
| `compiler.warn-param-count` | int | `0` | 参数数量超过多少打 WARN，`0` 不查 |
| `compiler.max-param-count` | int | `0` | 参数数量上限，`0` 不限 |
| `mode.strict` | boolean | `false` | 严格模式：遇到不支持的表达式直接报错 |
| `mode.multi.repeat-enabled` | boolean | `false` | 多值参数是否允许重复键 |

## 怎么覆盖

```yaml
patra:
  ingest:
    expr:
      compiler:
        max-query-length: 4000
      mode:
        strict: true
```

## 出哪些默认值

无。
