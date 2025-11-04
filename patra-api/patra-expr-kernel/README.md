# patra-expr-kernel

## 概述

`patra-expr-kernel` 是一个**框架无关的表达式引擎**,用于构建可渲染为各种 API 查询语法的布尔查询表达式。它提供纯 Java 表达式 AST,以平台独立的方式建模搜索查询,是 Patra 医学文献数据平台的核心查询抽象层。

**核心价值**: 构建单个逻辑查询表达式,然后将其渲染为 PubMed 语法、EPMC 语法、Elasticsearch DSL 等不同平台的查询语法。这种抽象使得数据采集层可以统一处理查询逻辑,而不必关心底层 API 的差异。

## 核心功能

- **布尔逻辑**: 支持 AND、OR、NOT 组合操作
- **字段约束**: 词条匹配、IN 查询、范围查询、存在性检查
- **文本匹配**: 精确、前缀、后缀、包含、通配符等多种匹配策略
- **类型安全范围**: 日期、日期时间、数字范围查询,支持边界控制
- **规范化与哈希**: 生成确定性 JSON 和 SHA-256 哈希,用于缓存和去重
- **访问者模式**: 可扩展架构,支持将表达式渲染为不同 API 的查询语法

## 模块结构

```
com.patra.expr
├── Expr.java              # 密封接口,表达式树根节点
├── And.java               # AND 逻辑运算符
├── Or.java                # OR 逻辑运算符
├── Not.java               # NOT 逻辑运算符
├── Const.java             # 常量表达式(TRUE/FALSE)
├── Atom.java              # 叶节点(字段约束)
├── Exprs.java             # 表达式工厂类
├── ExprVisitor.java       # 访问者接口
├── Operator.java          # 操作符枚举
├── Value.java             # 值类型接口
├── TextMatch.java         # 文本匹配策略
├── CaseSensitivity.java   # 大小写敏感性
├── canonical/
│   ├── ExprCanonicalizer.java      # 表达式规范化工具
│   └── ExprCanonicalSnapshot.java  # 规范化快照
└── json/
    └── ExprJsonCodec.java           # JSON 序列化/反序列化
```

## 主要组件

### 1. 密封接口 Expr

**设计模式**: 密封接口(Sealed Interface)

```java
public sealed interface Expr permits And, Or, Not, Const, Atom {
    <R> R accept(ExprVisitor<R> visitor);
}
```

**实现类型**:
- `And`: AND 逻辑运算符,包含子表达式列表
- `Or`: OR 逻辑运算符,包含子表达式列表
- `Not`: NOT 逻辑运算符,包含单个子表达式
- `Const`: 常量表达式(TRUE/FALSE)
- `Atom`: 叶节点,表示字段约束(fieldKey + operator + value)

**密封的好处**:
- 穷举模式匹配,编译器检查所有情况
- 无运行时意外,所有子类型在编译期已知
- 类型安全的表达式树

### 2. 表达式工厂 Exprs

提供静态工厂方法,用于构建各种表达式:

```java
// 布尔逻辑
Exprs.and(List.of(expr1, expr2))
Exprs.or(List.of(expr1, expr2))
Exprs.not(expr)
Exprs.constTrue() / constFalse()

// 文本搜索
Exprs.term(field, value, TextMatch.CONTAINS)
Exprs.term(field, value, TextMatch.EXACT, CaseSensitivity.INSENSITIVE)

// 离散值匹配
Exprs.in(field, List.of(value1, value2))

// 范围查询
Exprs.rangeDate(field, from, to)
Exprs.rangeDateTime(field, from, to)
Exprs.rangeNumber(field, from, to)

// 存在性检查
Exprs.exists(field, true)

// 平台特定令牌
Exprs.token(tokenType, tokenValue)
```

### 3. 访问者模式 ExprVisitor

**设计模式**: 访问者模式(Visitor Pattern)

**接口定义**:
```java
public interface ExprVisitor<R> {
    R visitAnd(And and);
    R visitOr(Or or);
    R visitNot(Not not);
    R visitConst(Const constant);
    R visitAtom(Atom atom);
}
```

**用途**: 将遍历逻辑与节点结构分离,支持多种操作(渲染、验证、转换)而无需修改表达式类。

**典型实现**:
- PubMed 查询渲染器: 将表达式树渲染为 PubMed API 查询字符串
- EPMC 查询渲染器: 将表达式树渲染为 EPMC API 查询字符串
- 验证访问者: 验证表达式的语义正确性
- 字段提取访问者: 提取表达式中使用的所有字段

### 4. 规范化器 ExprCanonicalizer

**功能**: 生成表达式的确定性 JSON 表示和 SHA-256 哈希。

**过程**:
1. 将表达式序列化为 JSON
2. 规范化: 排序对象键、去重数组、修剪空白
3. 哈希: 对规范 JSON 计算 SHA-256

**输出**: `ExprCanonicalSnapshot` 包含:
- `expr()`: 原始表达式
- `canonicalJson()`: 确定性 JSON 字符串
- `hash()`: SHA-256 哈希(64 位十六进制字符串)

**用例**:
- 缓存键: 使用 hash 作为查询结果的缓存标识
- 去重: 检测逻辑相同的查询(即使 JSON 字段顺序不同)
- 审计: 在计划元数据中存储 canonicalJson + hash

## 表达式语法

### 操作符类型

| 操作符 | 说明 | 工厂方法 |
|--------|------|----------|
| **TERM** | 文本搜索,支持多种匹配策略 | `Exprs.term(field, value, textMatch)` |
| **IN** | 离散值列表匹配(类似 SQL IN) | `Exprs.in(field, values)` |
| **RANGE** | 范围查询(日期、数字) | `Exprs.rangeDate/rangeNumber(field, from, to)` |
| **EXISTS** | 字段存在性检查 | `Exprs.exists(field, shouldExist)` |
| **TOKEN** | 平台特定令牌匹配(如 MeSH 词条) | `Exprs.token(tokenType, tokenValue)` |

### 文本匹配策略

```java
enum TextMatch {
    EXACT,      // 精确匹配: "cancer" 仅匹配 "cancer"
    PREFIX,     // 前缀匹配: "canc" 匹配 "cancer"
    SUFFIX,     // 后缀匹配: "ancer" 匹配 "cancer"
    CONTAINS,   // 包含匹配: "anc" 匹配 "cancer"
    WILDCARD    // 通配符匹配: "can*er" 匹配 "cancer"
}
```

### 大小写敏感性

```java
enum CaseSensitivity {
    SENSITIVE,    // 区分大小写
    INSENSITIVE   // 不区分大小写
}
```

## 使用示例

### 基本查询构建

```java
// 示例 1: 简单 AND 查询
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

// 示例 2: 复杂组合查询
// (publicationType IN ["Journal Article", "Review"])
// AND (country IN ["USA", "UK"])
// AND (citationCount >= 10)
// AND hasAuthorAffiliation
Expr complexQuery = Exprs.and(List.of(
    Exprs.in("publicationType", List.of("Journal Article", "Review")),
    Exprs.in("country", List.of("USA", "UK"), CaseSensitivity.INSENSITIVE),
    Exprs.rangeNumber("citationCount", new BigDecimal("10"), new BigDecimal("999999")),
    Exprs.exists("authorAffiliation", true)
));
```

### 规范化与哈希

```java
Expr query = Exprs.and(List.of(
    Exprs.term("title", "cancer", TextMatch.CONTAINS),
    Exprs.rangeDate("date", LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1))
));

// 规范化
ExprCanonicalSnapshot snapshot = ExprCanonicalizer.canonicalize(query);

// 使用
String cacheKey = snapshot.hash();  // 用作 Redis 缓存键
String auditJson = snapshot.canonicalJson();  // 存储到数据库用于审计
```

### 使用访问者模式渲染

```java
// 定义自定义访问者
public class SimpleStringRenderer implements ExprVisitor<String> {
    @Override
    public String visitAnd(And and) {
        return and.children().stream()
            .map(child -> child.accept(this))
            .collect(Collectors.joining(" AND ", "(", ")"));
    }

    @Override
    public String visitAtom(Atom atom) {
        return atom.fieldKey() + ":" + renderValue(atom.value());
    }
    // ... 其他方法实现
}

// 使用访问者渲染
Expr query = Exprs.term("title", "cancer", TextMatch.EXACT);
String rendered = query.accept(new SimpleStringRenderer());
```

## 设计决策

### 为什么选择密封接口?

**问题**: 表达式树可能在运行时被不安全地扩展。

**解决方案**: 使用 Java 17 密封接口限制子类型,确保所有表达式节点类型在编译期已知。

**好处**:
- 编译器强制穷举检查,在 switch 表达式中必须处理所有情况
- 无运行时意外,避免未知的表达式类型
- 清晰的领域模型,表达式类型一目了然

### 为什么选择访问者模式?

**问题**: 需要支持多种操作(PubMed 渲染、EPMC 渲染、验证等),如果在表达式类中直接添加方法,会导致类膨胀且违反开闭原则。

**解决方案**: 使用访问者模式将遍历逻辑与节点结构分离。

**好处**:
- 无需修改表达式类即可添加新操作
- 多个渲染器(PubMed、EPMC、Elasticsearch)可以共存
- 遵循开闭原则(对扩展开放,对修改关闭)
- 每个访问者职责单一,易于测试和维护

### 为什么需要规范化?

**问题**: 相同的逻辑查询可能有不同的 JSON 表示(字段顺序不同、空白不同),导致无法有效缓存和去重。

**解决方案**: 规范化 JSON 表示 + SHA-256 哈希,确保逻辑相同的查询产生相同的哈希值。

**好处**:
- 可靠地缓存查询结果,即使查询构建顺序不同
- 检测重复查询,避免重复执行
- 提供一致的审计指纹,便于追踪和调试

## 技术栈

**核心依赖**:
- **patra-common-core**: 提供通用工具类(如 HashUtils)
- **jackson-databind**: JSON 序列化/反序列化

**测试依赖**:
- **JUnit 5**: 单元测试框架
- **AssertJ**: 流畅的断言库

**特点**: 无 Spring 依赖,无 MyBatis 依赖,纯 Java 库,可在任何 Java 环境中使用。

---

**最后更新**: 2025-11-03
