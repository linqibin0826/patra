# patra-expr-kernel 模块详解

> 内容迁移自历史 README，保留 AST、序列化、规范化与扩展策略等深度说明。

## 模块：patra-expr-kernel

面向 Papertrace 平台的“统一表达式内核（Expression Kernel）”。提供一个**确定性、可序列化、可规范化并支持扩展的布尔过滤表达式 AST**，用于：

1. 跨服务传递结构化查询/过滤条件（避免 DSL 字符串拼接）
2. 产生稳定的“语义指纹”（canonical JSON + SHA-256）用于缓存键 / 幂等锁 / 查询去重
3. 为后续“表达式下推”（解析到 ES / SQL / 自研索引）提供统一抽象层
4. 支撑审计与可观测（可记录标准化 JSON 快照，而非不稳定用户输入）

> 设计哲学：以最小表达能力覆盖 80% 常见过滤场景，其余通过外围“表达式编译器”转化为内核 AST；内核自身保持**无框架依赖**与**不可变（immutable）**。

---

## 1. 设计目标

| 目标 | 说明 | 约束策略 |
|------|------|---------|
| 不可变 | 节点均为 record/enum | 构造后即安全复用 / 线程安全 |
| 可组合 | AND / OR 树 + NOT + 常量 | 逻辑算子最小集，降低解析复杂度 |
| 严格类型 | Atom.Operator ↔ Value 显式约束 | 编译期防错，避免运行期兼容判断 |
| 可序列化 | 自定义 Jackson Codec | 稳定 JSON Schema，兼容老版本字段 |
| 可规范化 | Canonicalizer 产出确定性 JSON | 字段排序 / 空裁剪 / 数值归一 / 文本空白折叠 |
| 可哈希 | 内置 SHA-256 指纹 | 作为缓存键、重复检测 |
| 可访问 | Visitor 模式 | 各存储后端实现翻译器 |
| 渐进扩展 | 通过外层编译阶段扩展语义 | 内核不直接膨胀算子种类 |

## 2. AST 总览

根接口：`Expr` (sealed) 允许以下节点：

| 节点 | 说明 | 重要字段 |
|------|------|----------|
| And(List<Expr>) | 逻辑与（≥1 子节点） | children 已复制为不可变 List |
| Or(List<Expr>) | 逻辑或（≥1 子节点） | 同上 |
| Not(Expr) | 逻辑非 | child |
| Const(TRUE / FALSE) | 常量短路 | 枚举值 |
| Atom(fieldKey, operator, value) | 叶子约束 | operator 与 value 类型强匹配 |

Atom 支持的 `Operator` 与对应 `Value`（sealed hierarchy）：

| Operator | Value 类型 | 语义 |
|----------|-----------|------|
| TERM | TermValue(text, match, caseSensitivity) | 单值匹配（等值/前缀/模糊等取决于 TextMatch） |
| IN | InValues(List<String>, caseSensitivity) | 多枚举值匹配（非空列表） |
| RANGE | DateRange / DateTimeRange / NumberRange | 区间匹配（支持开闭边界） |
| EXISTS | ExistsFlag(shouldExist) | 字段存在性 |
| TOKEN | TokenValue(tokenType, tokenValue) | 平台自定义语义 token（如安全标签） |

补充枚举：`CaseSensitivity (SENSITIVE/INSENSITIVE)`、`TextMatch`（如 EXACT / PREFIX / SUBSTRING / REGEX 等）。

所有构造器中执行参数合法性与类型兼容校验；失败抛出 `IllegalArgumentException`。

## 3. JSON 序列化协议（ExprJsonCodec）

Codec 通过静态工厂 `ExprJsonCodec.mapper()` 暴露预配置 `ObjectMapper`。核心规则：

1. 节点统一含有 `type` 字段：AND / OR / NOT / CONST / ATOM
2. 逻辑节点字段：
   - AND/OR: `{ "type":"AND", "children":[ ... ] }`
   - NOT: `{ "type":"NOT", "child": { ... } }`
3. CONST: `{ "type":"CONST", "value": true|false }`
4. ATOM: `{ "type":"ATOM", "field":"f", "op":"TERM", "value": { ... } }`
5. Value 子结构：
   - TERM: `{ "kind":"TERM", "text":"abc", "match":"EXACT", "case":"INSENSITIVE" }`
   - IN: `{ "kind":"IN", "values":["a","b"], "case":"SENSITIVE" }`
   - RANGE(Date): `{ "kind":"RANGE", "rangeType":"DATE", "from":"2024-01-01", "to":"2024-02-01", "fromBoundary":"CLOSED", "toBoundary":"OPEN" }`
   - RANGE(DateTime): `rangeType=DATETIME`, 时间戳采用 ISO-8601 Instant
   - RANGE(Number): `rangeType=NUMBER`, 数字以字符串解析为 BigDecimal
   - EXISTS: `{ "kind":"EXISTS", "shouldExist": true }`
   - TOKEN: `{ "kind":"TOKEN", "tokenType":"X", "tokenValue":"Y" }`
6. 允许未知字段（反序列化时忽略），为前向兼容预留
7. `null` / 空白字符串会在 Canonical 化阶段剔除

### 3.1 版本兼容策略

| 变更类型 | 策略 | 示例 |
|----------|------|------|
| 新增 Value 字段 | 仅写出；读时设置默认值 | 新增 `locale` 时旧 JSON 可缺失 |
| 新增 kind / operator | 需外围“编译器”先行降级/转译 | 新增 GEO_BOX 先转为多个 RANGE/TERM |
| 删除字段 | 通过 Canonical 化淡化影响 | 删除可冗余空字段 |
| 语义变更 | bump 次版本 + 文档说明 | TextMatch 规则调整 |

## 4. 规范化（Canonicalization）

类：`ExprCanonicalizer` + 快照：`ExprCanonicalSnapshot(expr, canonicalJson, hash)`。

流程：Expr → 逻辑 JSON → 递归规范化 → 稳定排序/裁剪 → 序列化字符串 → SHA-256。

规则明细：
1. Object 字段名排序（自然序）
2. 递归处理后剔除“空”节点：null / missing / 空对象 / 空数组 / 空字符串
3. Array：
   - 子元素先各自规范化
   - 生成 `(typeTag|serialized)` 唯一键去重（保持首次出现）
   - 依据 `(typeTag, serialized)` 排序：typeTag：Null=0, Boolean=1, Number=2, Text=3, Object=4, Array=5, Other=9
4. 文本：trim + 折叠连续空白为单空格
5. 数字：`stripTrailingZeros()`；若 scale < 0 则设为 0；保证 1 与 1.0 规范后一致
6. 产出 JSON 使用紧凑 writer
7. Hash：`sha256Hex(canonicalJson UTF-8)`（复用 `patra-common` 的 `HashUtils`）

使用场景：

| 场景 | 描述 |
|------|------|
| 缓存 Key | canonicalJson 或 hash 作为查询缓存主键 |
| 幂等控制 | hash 作为请求签名，防止重复提交 |
| 结果复用 | 多用户相同语义表达式共享一次解析/计划 |
| 审计追溯 | 记录 canonicalJson，减少敏感冗余 |

复杂度：O(N log N)（主要来自字段排序与数组去重）。

## 5. 工厂与使用示例（Exprs）

`Exprs` 提供静态便捷方法：`and(List<Expr>)`、`or(...)`、`not(expr)`、`term(field, text, match)`、`in(field, List<String>)`、`range(field, from, to)` 等。

```java
import static com.patra.expr.Exprs.*;
import com.patra.expr.*;

Expr expr = and(
    term("title", "AI", TextMatch.SUBSTRING),
    or(
        exists("publisher"),
        term("journal", "Nature", TextMatch.EXACT)
    ),
    rangeDate("publishDate", LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"))
);

String json = Exprs.toJson(expr);
Expr parsed = Exprs.fromJson(json);
var snapshot = ExprCanonicalizer.canonicalize(expr);
String canonicalJson = snapshot.canonicalJson();
String hash = snapshot.hash();
```

## 6. Visitor 模式

接口：`ExprVisitor<R>`；便捷抽象：`ExprVisitor.NoReturn`。

实现一个转译器示例：

```java
class SqlTranslator implements ExprVisitor<String> {
  public String visitAnd(And andNode) {
    return andNode.children().stream().map(e -> e.accept(this))
        .collect(Collectors.joining(" AND ", "(", ")"));
  }
  public String visitOr(Or orNode) { /* ... */ }
  public String visitNot(Not notNode) { return "NOT " + notNode.child().accept(this); }
  public String visitConst(Const c) { return c.value() ? "1=1" : "1=0"; }
  public String visitAtom(Atom atom) { /* 根据 operator/value 生成条件 */ }
}
```

## 7. 性能与内存

| 项目 | 影响因素 | 建议 |
|------|----------|------|
| 构造 | 大量小节点 | 尽量复用不可变子表达式（常量池） |
| 序列化 | 频繁 toJson | 缓存 ObjectMapper；避免重复 canonicalize 同一实例 |
| 规范化 | 去重 + 排序 | 大数组（>1k 条）可在构建阶段先做去重排序 |
| 哈希 | 大表达式 | 若表达式稳定可缓存 snapshot（弱引用 Map） |

## 8. 错误与边界

| 场景 | 行为 |
|------|------|
| fieldKey 为空/空白 | 构造器抛出 IllegalArgumentException |
| IN 空列表 | 抛出 IllegalArgumentException |
| Operator 与 Value 不匹配 | 抛出 IllegalArgumentException |
| 规范化 JSON 解析异常 | 抛出 IllegalStateException（上层可包装为业务错误） |

## 9. 扩展策略

| 需求 | 推荐做法 |
|------|----------|
| 新算子（如 BETWEEN_EXCLUSIVE） | 在上层 DSL 编译为 RANGE + Boundary.OPEN |
| 复合逻辑（如 A XOR B） | 编译期重写为 `(A OR B) AND NOT (A AND B)` |
| 模糊权重/评分 | 另行扩展权重模型，不纳入内核 AST |
| 聚合/排序/分页 | 归属上层 QueryDTO，不放入 Expr 树 |

## 10. Roadmap

| 优先级 | 项目 | 描述 |
|--------|------|------|
| High | JSON Schema 明文化 | 发布 machine-readable schema (JSON Schema Draft) |
| High | 表达式缓存接口 | 提供可插拔 snapshot 缓存 SPI |
| Mid | 更多 TextMatch | 支持正则 / 通配符 / 语音学匹配 |
| Mid | 统计工具 | 节点计数 / 深度 / 复杂度分级 |
| Low | Canonical 优化 | 大数组分块 + 并行排序（需基准） |
| Low | 运算重写优化 | 常见模式 `(A AND A) -> A`、`(A AND TRUE) -> A` |

## 11. FAQ

| 问题 | 回答 |
|------|------|
| 为什么不直接使用字符串 DSL? | 难以做结构化分析、重写与安全过滤；AST 提供静态保障 |
| 为什么不支持 NOT IN? | 通过 NOT + IN 组合表达即可；保持算子集最小 |
| Hash 与 JSON 哪个作为缓存键? | 建议使用 hash 主键 + canonicalJson 旁路存储便于调试 |
| 是否需要稳定字段顺序? | 是，Canonical 化后保持，避免 hash 震荡 |

## 12. 快速引用

| 目标 | 代码 |
|------|------|
| 构建表达式 | `Expr expr = Exprs.term("field", "v", TextMatch.EXACT);` |
| 序列化 | `String json = Exprs.toJson(expr);` |
| 反序列化 | `Expr expr = Exprs.fromJson(json);` |
| 规范化+哈希 | `ExprCanonicalizer.canonicalize(expr)` |
| 自定义 Visitor | 实现 `ExprVisitor<R>` |

## 13. 贡献指南

1. 新增节点/算子前先评估是否可由现有组合表达
2. 保持 record / enum 不可变语义
3. 绝不在内核引入框架依赖（Spring / MyBatis 等）
4. 变更 JSON 协议需更新文档与 Roadmap
5. 添加/修改规则后补充序列化、规范化、Visitor 单元测试

## 14. 参考

| 主题 | 位置 |
|------|------|
| Canonical 实现 | `canonical/ExprCanonicalizer.java`
| JSON Codec | `json/ExprJsonCodec.java`
| 工厂 | `Exprs.java`
| AST 定义 | `Expr.java` 及各节点 record
| 快照 | `canonical/ExprCanonicalSnapshot.java`

---

如需扩展或遇到表达式翻译问题，请在主仓库讨论区提出（附 canonicalJson 与期望方言示例）。
