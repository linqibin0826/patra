# patra-expr-kernel — 表达式引擎

> **框架无关的表达式引擎**,用于构建可渲染为各种 API 查询语法的布尔查询表达式。

---

## 📌 目的

`patra-expr-kernel` 提供**纯 Java 表达式 AST**,以平台独立的方式建模搜索查询。它支持:

1. **布尔逻辑**: AND、OR、NOT 操作
2. **字段约束**: 词条匹配、IN 查询、范围查询、存在性检查
3. **文本匹配**: 精确、前缀、后缀、包含、通配符
4. **类型安全范围**: 带边界控制的日期、日期时间、数字范围
5. **规范化**: 确定性 JSON + SHA-256 哈希,用于缓存/去重
6. **访问者模式**: 可扩展,用于渲染到不同的 API 语法

**核心用例**: 构建单个逻辑查询表达式,然后将其渲染为 PubMed 语法、EPMC 语法、Elasticsearch DSL 等。

---

## 🏗️ 架构

### 密封接口层次结构

```java
public sealed interface Expr permits And, Or, Not, Const, Atom {
    <R> R accept(ExprVisitor<R> visitor);
}

// 布尔运算符
record And(List<Expr> children) implements Expr { }
record Or(List<Expr> children) implements Expr { }
record Not(Expr child) implements Expr { }

// 常量
enum Const implements Expr { TRUE, FALSE }

// 叶节点(字段约束)
record Atom(String fieldKey, Operator operator, Value value) implements Expr { }
```

**密封的好处**:
- 穷举模式匹配(编译器检查)
- 无运行时意外(所有子类型已知)
- 类型安全的表达式树

---

## 🔑 支持的操作

### 1. 布尔逻辑

| 操作 | 工厂方法 | 示例 |
|-----------|---------------|---------|
| **AND** | `Exprs.and(List<Expr>)` | `and([termA, termB])` → 全部必须匹配 |
| **OR** | `Exprs.or(List<Expr>)` | `or([termA, termB])` → 任一必须匹配 |
| **NOT** | `Exprs.not(Expr)` | `not(term)` → 否定 |
| **CONST** | `Exprs.constTrue()` / `constFalse()` | 始终为真/假 |

**示例**:
```java
// (title:cancer OR abstract:cancer) AND publicationDate:[2023-01-01 TO 2024-01-01]
Expr query = Exprs.and(List.of(
    Exprs.or(List.of(
        Exprs.term("title", "cancer", TextMatch.CONTAINS),
        Exprs.term("abstract", "cancer", TextMatch.CONTAINS)
    )),
    Exprs.rangeDate("publicationDate",
        LocalDate.of(2023, 1, 1),
        LocalDate.of(2024, 1, 1))
));
```

---

### 2. TERM 操作(文本搜索)

**目的**: 在字段中搜索文本,支持各种匹配策略。

**工厂方法**:
```java
Expr Exprs.term(String field, String value, TextMatch match);
Expr Exprs.term(String field, String value, TextMatch match, CaseSensitivity caseSensitivity);
```

**文本匹配类型**:
```java
enum TextMatch {
    EXACT,      // "cancer" 仅匹配 "cancer"
    PREFIX,     // "canc" 匹配 "cancer"
    SUFFIX,     // "ancer" 匹配 "cancer"
    CONTAINS,   // "anc" 匹配 "cancer"
    WILDCARD    // "can*er" 匹配 "cancer"
}
```

**示例**:
```java
// 精确匹配: title = "lung cancer"
Exprs.term("title", "lung cancer", TextMatch.EXACT);

// 前缀匹配(不区分大小写): author 以 "Smith" 开头
Exprs.term("author", "Smith", TextMatch.PREFIX, CaseSensitivity.INSENSITIVE);

// 包含匹配: abstract 包含 "COVID-19"
Exprs.term("abstract", "COVID-19", TextMatch.CONTAINS);

// 通配符: MeSH 词条如 "neoplasm*"
Exprs.term("meshTerm", "neoplasm*", TextMatch.WILDCARD);
```

---

### 3. IN 操作(离散值)

**目的**: 将字段与离散值列表匹配(类似 SQL IN)。

**工厂方法**:
```java
Expr Exprs.in(String field, List<String> values);
Expr Exprs.in(String field, List<String> values, CaseSensitivity caseSensitivity);
```

**示例**:
```java
// 发布类型 IN ("Journal Article", "Review")
Exprs.in("publicationType", List.of("Journal Article", "Review"));

// 国家 IN ("USA", "UK", "Canada") - 不区分大小写
Exprs.in("country", List.of("USA", "UK", "Canada"), CaseSensitivity.INSENSITIVE);
```

---

### 4. RANGE 操作

**目的**: 在值范围内搜索(日期、数字)。

#### 日期范围

```java
Expr Exprs.rangeDate(String field, LocalDate from, LocalDate to);
Expr Exprs.rangeDate(String field, LocalDate from, LocalDate to, boolean includeFrom, boolean includeTo);
```

**示例**:
```java
// 2023-01-01 到 2024-01-01 的出版物(包含)
Exprs.rangeDate("publicationDate",
    LocalDate.of(2023, 1, 1),
    LocalDate.of(2024, 1, 1)
);

// 半开区间: [2023-01-01, 2024-01-01)
Exprs.rangeDate("publicationDate",
    LocalDate.of(2023, 1, 1),
    LocalDate.of(2024, 1, 1),
    true,  // 包含 from
    false  // 排除 to
);
```

#### 日期时间范围

```java
Expr Exprs.rangeDateTime(String field, Instant from, Instant to);
```

**示例**:
```java
// 在两个时刻之间最后更新
Exprs.rangeDateTime("lastModified",
    Instant.parse("2024-01-01T00:00:00Z"),
    Instant.parse("2024-12-31T23:59:59Z")
);
```

#### 数字范围

```java
Expr Exprs.rangeNumber(String field, BigDecimal from, BigDecimal to);
```

**示例**:
```java
// 引用次数在 10 到 100 之间
Exprs.rangeNumber("citationCount",
    new BigDecimal("10"),
    new BigDecimal("100")
);
```

---

### 5. EXISTS 操作

**目的**: 检查字段是否存在(有任何值)或不存在。

**工厂方法**:
```java
Expr Exprs.exists(String field, boolean shouldExist);
```

**示例**:
```java
// 有 DOI
Exprs.exists("doi", true);

// 无作者单位
Exprs.exists("authorAffiliation", false);
```

---

### 6. TOKEN 操作

**目的**: 平台特定的令牌匹配(例如 MeSH 词条、基因符号)。

**工厂方法**:
```java
Expr Exprs.token(String tokenType, String tokenValue);
Expr Exprs.token(String field, String tokenType, String tokenValue);
```

**示例**:
```java
// MeSH 词条(PubMed 特定)
Exprs.token("MeSH", "D002289");  // "Carcinoma" MeSH ID

// 基因符号(NCBI 特定)
Exprs.token("gene", "GeneSymbol", "BRCA1");
```

---

## 🔄 访问者模式

**目的**: 遍历表达式树并执行操作(渲染、验证、转换)。

**接口**:
```java
public interface ExprVisitor<R> {
    R visitAnd(And and);
    R visitOr(Or or);
    R visitNot(Not not);
    R visitConst(Const constant);
    R visitAtom(Atom atom);
}
```

**示例: 渲染为字符串**:
```java
public class ExprStringRenderer implements ExprVisitor<String> {

    @Override
    public String visitAnd(And and) {
        return and.children().stream()
            .map(child -> child.accept(this))
            .collect(Collectors.joining(" AND ", "(", ")"));
    }

    @Override
    public String visitOr(Or or) {
        return or.children().stream()
            .map(child -> child.accept(this))
            .collect(Collectors.joining(" OR ", "(", ")"));
    }

    @Override
    public String visitAtom(Atom atom) {
        return atom.fieldKey() + ":" + renderValue(atom.value());
    }

    // ... 其他方法
}

// 用法
Expr query = Exprs.and(List.of(...));
String rendered = query.accept(new ExprStringRenderer());
```

---

## 🔐 规范化

**目的**: 为以下用途生成**确定性** JSON 和哈希:
- 缓存查询结果
- 查询去重
- 审计跟踪

**类**: `ExprCanonicalizer`

**过程**:
1. 将表达式序列化为 JSON
2. **规范化**: 排序对象键、去重数组、修剪空白
3. **哈希**: 规范 JSON 的 SHA-256

**示例**:
```java
Expr query = Exprs.and(List.of(
    Exprs.term("title", "cancer", TextMatch.CONTAINS),
    Exprs.rangeDate("date", from, to)
));

ExprCanonicalSnapshot snapshot = ExprCanonicalizer.canonicalize(query);

snapshot.expr();           // 原始表达式
snapshot.canonicalJson();  // 确定性 JSON 字符串
snapshot.hash();           // SHA-256 哈希(例如 "a3f2c8b...")
```

**用例**:
- **缓存键**: 使用 `hash` 作为查询结果的 Redis 键
- **去重**: 即使 JSON 字段顺序不同也能检测相同查询
- **审计**: 在计划元数据中存储 `canonicalJson` + `hash`

---

## 📦 依赖

**最小依赖以保证可移植性**:

```xml
<dependencies>
    <dependency>
        <groupId>com.papertrace</groupId>
        <artifactId>patra-common</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

**无 Spring,无 MyBatis** — 纯 Java 库。

---

## 🔌 与 Papertrace 的集成

### 在 patra-ingest 中

**用例**: 从触发参数和数据源配置构建计划表达式。

**组件**: `PlanExpressionBuilder`(在 `patra-ingest-app` 中)

**示例**:
```java
@Component
public class PlanExpressionBuilder {

    public PlanExpressionDescriptor build(PlanTriggerNorm norm, ProvenanceConfigSnapshot config) {
        // 根据操作类型构建表达式
        Expr expr = switch (norm.operationCode()) {
            case HARVEST -> buildHarvestExpression(norm, config);
            case UPDATE -> buildUpdateExpression(norm, config);
            case COMPENSATION -> buildCompensationExpression(norm, config);
        };

        // 规范化以供存储
        ExprCanonicalSnapshot snapshot = ExprCanonicalizer.canonicalize(expr);

        return new PlanExpressionDescriptor(
            snapshot.hash(),
            snapshot.canonicalJson()
        );
    }

    private Expr buildHarvestExpression(PlanTriggerNorm norm, ProvenanceConfigSnapshot config) {
        return Exprs.and(List.of(
            Exprs.rangeDate("publicationDate", norm.windowFrom(), norm.windowTo()),
            Exprs.in("publicationType", config.publicationTypes())
        ));
    }
}
```

### 在 patra-registry 中

**用例**: 存储表达式能力和渲染规则。

**表**:
- `reg_prov_expr_capability`: 定义每个数据源支持的操作
- `reg_prov_expr_render_rule`: 将表达式渲染为 API 特定语法的规则

---

## 🛠️ 扩展

### 添加新运算符

**示例**: 为模糊文本匹配添加 `FUZZY` 运算符。

#### 步骤 1: 添加运算符枚举

```java
public enum Operator {
    TERM(TermValue.class),
    IN(InValues.class),
    RANGE(RangeValue.class),
    EXISTS(ExistsFlag.class),
    TOKEN(TokenValue.class),
    FUZZY(FuzzyValue.class);  // 新增
    // ...
}
```

#### 步骤 2: 定义值类型

```java
public record FuzzyValue(String text, int maxEdits) implements Value {
    public FuzzyValue {
        Objects.requireNonNull(text, "text");
        if (maxEdits < 0 || maxEdits > 2) {
            throw new IllegalArgumentException("maxEdits must be 0-2");
        }
    }
}
```

#### 步骤 3: 添加工厂方法

```java
public static Expr fuzzy(String field, String value, int maxEdits) {
    return new Atom(field, Operator.FUZZY, new FuzzyValue(value, maxEdits));
}
```

#### 步骤 4: 更新访问者

```java
// 在您的自定义访问者中
@Override
public String visitAtom(Atom atom) {
    return switch (atom.operator()) {
        case TERM -> renderTerm(atom);
        case IN -> renderIn(atom);
        case FUZZY -> renderFuzzy(atom);  // 新增
        // ...
    };
}
```

---

## 🧪 测试

### 单元测试

```java
@Test
void testAndExpression() {
    // Given
    Expr termA = Exprs.term("title", "cancer", TextMatch.EXACT);
    Expr termB = Exprs.term("abstract", "tumor", TextMatch.CONTAINS);

    // When
    Expr and = Exprs.and(List.of(termA, termB));

    // Then
    assertTrue(and instanceof And);
    assertEquals(2, ((And) and).children().size());
}

@Test
void testCanonicalization() {
    // Given
    Expr expr = Exprs.term("title", "cancer", TextMatch.EXACT);

    // When
    ExprCanonicalSnapshot snapshot = ExprCanonicalizer.canonicalize(expr);

    // Then
    assertNotNull(snapshot.hash());
    assertEquals(64, snapshot.hash().length());  // SHA-256 十六进制长度
    assertTrue(snapshot.canonicalJson().contains("\"fieldKey\":\"title\""));
}

@Test
void testCanonicalDeduplication() {
    // Given: 不同 JSON 字段顺序的相同表达式
    Expr expr1 = Exprs.and(List.of(termA, termB));
    Expr expr2 = Exprs.and(List.of(termB, termA));  // 不同顺序

    // When
    String hash1 = ExprCanonicalizer.canonicalize(expr1).hash();
    String hash2 = ExprCanonicalizer.canonicalize(expr2).hash();

    // Then: 哈希相同(与顺序无关)
    assertEquals(hash1, hash2);
}
```

---

## 📊 设计决策

### 为什么选择密封接口?

**问题**: 表达式树可能在运行时被不安全地扩展。

**解决方案**: 密封接口限制子类型 → 穷举模式匹配。

**好处**:
- 编译器确保在 `switch` 中处理所有情况
- 无运行时意外
- 清晰的领域模型

### 为什么选择访问者模式?

**问题**: 添加新操作(渲染、验证)需要修改表达式类。

**解决方案**: 访问者模式将遍历逻辑与节点结构分离。

**好处**:
- 无需更改表达式类即可添加新操作
- 多个渲染器(PubMed、EPMC、Elasticsearch)共存
- 遵循开闭原则

### 为什么需要规范化?

**问题**: 相同的逻辑查询可能有不同的 JSON 表示(字段顺序、空白)。

**解决方案**: 规范化 + 哈希以获得确定性标识。

**好处**:
- 可靠地缓存查询结果
- 检测重复查询
- 具有一致指纹的审计跟踪

---

## 📈 性能考虑

**不可变性**: 所有表达式节点都是不可变的 → 可安全地跨线程共享。

**无惰性求值**: 表达式被急切构造(不是惰性 AST)。

**小内存占用**: Record 使用紧凑表示。

**规范化成本**: 排序为 O(n log n),但每次计划创建仅执行一次。

---

## 🔗 相关文档

- [主 README](../README.md)
- [patra-ingest README](../patra-ingest/README.md) — 构建表达式的地方
- [patra-registry README](../patra-registry/README.md) — 存储表达式元数据的地方
- [patra-common README](../patra-common/README.md) — 共享工具(HashUtils)

---

**最后更新**: 2025-01-12
