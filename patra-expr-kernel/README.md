# patra-expr-kernel — Expression Engine

> **Framework-agnostic expression engine** for building boolean query expressions that can be rendered to various API query syntaxes.

---

## 📌 Purpose

`patra-expr-kernel` provides a **pure Java expression AST** for modeling search queries in a platform-independent way. It supports:

1. **Boolean Logic**: AND, OR, NOT operations
2. **Field Constraints**: Term matching, IN queries, range queries, existence checks
3. **Text Matching**: Exact, prefix, suffix, contains, wildcard
4. **Type-Safe Ranges**: Date, DateTime, Number ranges with boundary control
5. **Canonicalization**: Deterministic JSON + SHA-256 hashing for caching/deduplication
6. **Visitor Pattern**: Extensible for rendering to different API syntaxes

**Key Use Case**: Build a single logical query expression, then render it to PubMed syntax, EPMC syntax, Elasticsearch DSL, etc.

---

## 🏗️ Architecture

### Sealed Interface Hierarchy

```java
public sealed interface Expr permits And, Or, Not, Const, Atom {
    <R> R accept(ExprVisitor<R> visitor);
}

// Boolean operators
record And(List<Expr> children) implements Expr { }
record Or(List<Expr> children) implements Expr { }
record Not(Expr child) implements Expr { }

// Constants
enum Const implements Expr { TRUE, FALSE }

// Leaf node (field constraints)
record Atom(String fieldKey, Operator operator, Value value) implements Expr { }
```

**Benefits of Sealed**:
- Exhaustive pattern matching (compiler-checked)
- No runtime surprises (all subtypes known)
- Type-safe expression trees

---

## 🔑 Supported Operations

### 1. Boolean Logic

| Operation | Factory Method | Example |
|-----------|---------------|---------|
| **AND** | `Exprs.and(List<Expr>)` | `and([termA, termB])` → all must match |
| **OR** | `Exprs.or(List<Expr>)` | `or([termA, termB])` → any must match |
| **NOT** | `Exprs.not(Expr)` | `not(term)` → negation |
| **CONST** | `Exprs.constTrue()` / `constFalse()` | Always true/false |

**Example**:
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

### 2. TERM Operation (Text Search)

**Purpose**: Search for text in a field with various matching strategies.

**Factory Method**:
```java
Expr Exprs.term(String field, String value, TextMatch match);
Expr Exprs.term(String field, String value, TextMatch match, CaseSensitivity caseSensitivity);
```

**Text Match Types**:
```java
enum TextMatch {
    EXACT,      // "cancer" matches "cancer" only
    PREFIX,     // "canc" matches "cancer"
    SUFFIX,     // "ancer" matches "cancer"
    CONTAINS,   // "anc" matches "cancer"
    WILDCARD    // "can*er" matches "cancer"
}
```

**Examples**:
```java
// Exact match: title = "lung cancer"
Exprs.term("title", "lung cancer", TextMatch.EXACT);

// Prefix match (case-insensitive): author starts with "Smith"
Exprs.term("author", "Smith", TextMatch.PREFIX, CaseSensitivity.INSENSITIVE);

// Contains match: abstract contains "COVID-19"
Exprs.term("abstract", "COVID-19", TextMatch.CONTAINS);

// Wildcard: MeSH term like "neoplasm*"
Exprs.term("meshTerm", "neoplasm*", TextMatch.WILDCARD);
```

---

### 3. IN Operation (Discrete Values)

**Purpose**: Match field against a list of discrete values (like SQL IN).

**Factory Method**:
```java
Expr Exprs.in(String field, List<String> values);
Expr Exprs.in(String field, List<String> values, CaseSensitivity caseSensitivity);
```

**Examples**:
```java
// Publication type IN ("Journal Article", "Review")
Exprs.in("publicationType", List.of("Journal Article", "Review"));

// Country IN ("USA", "UK", "Canada") - case-insensitive
Exprs.in("country", List.of("USA", "UK", "Canada"), CaseSensitivity.INSENSITIVE);
```

---

### 4. RANGE Operations

**Purpose**: Search within value ranges (dates, numbers).

#### Date Range

```java
Expr Exprs.rangeDate(String field, LocalDate from, LocalDate to);
Expr Exprs.rangeDate(String field, LocalDate from, LocalDate to, boolean includeFrom, boolean includeTo);
```

**Example**:
```java
// Publications from 2023-01-01 to 2024-01-01 (inclusive)
Exprs.rangeDate("publicationDate",
    LocalDate.of(2023, 1, 1),
    LocalDate.of(2024, 1, 1)
);

// Half-open range: [2023-01-01, 2024-01-01)
Exprs.rangeDate("publicationDate",
    LocalDate.of(2023, 1, 1),
    LocalDate.of(2024, 1, 1),
    true,  // include from
    false  // exclude to
);
```

#### DateTime Range

```java
Expr Exprs.rangeDateTime(String field, Instant from, Instant to);
```

**Example**:
```java
// Last updated between two instants
Exprs.rangeDateTime("lastModified",
    Instant.parse("2024-01-01T00:00:00Z"),
    Instant.parse("2024-12-31T23:59:59Z")
);
```

#### Number Range

```java
Expr Exprs.rangeNumber(String field, BigDecimal from, BigDecimal to);
```

**Example**:
```java
// Citation count between 10 and 100
Exprs.rangeNumber("citationCount",
    new BigDecimal("10"),
    new BigDecimal("100")
);
```

---

### 5. EXISTS Operation

**Purpose**: Check if field exists (has any value) or is absent.

**Factory Method**:
```java
Expr Exprs.exists(String field, boolean shouldExist);
```

**Examples**:
```java
// Has DOI
Exprs.exists("doi", true);

// No author affiliation
Exprs.exists("authorAffiliation", false);
```

---

### 6. TOKEN Operation

**Purpose**: Platform-specific token matching (e.g., MeSH terms, gene symbols).

**Factory Method**:
```java
Expr Exprs.token(String tokenType, String tokenValue);
Expr Exprs.token(String field, String tokenType, String tokenValue);
```

**Examples**:
```java
// MeSH term (PubMed-specific)
Exprs.token("MeSH", "D002289");  // "Carcinoma" MeSH ID

// Gene symbol (NCBI-specific)
Exprs.token("gene", "GeneSymbol", "BRCA1");
```

---

## 🔄 Visitor Pattern

**Purpose**: Traverse expression trees and perform operations (rendering, validation, transformation).

**Interface**:
```java
public interface ExprVisitor<R> {
    R visitAnd(And and);
    R visitOr(Or or);
    R visitNot(Not not);
    R visitConst(Const constant);
    R visitAtom(Atom atom);
}
```

**Example: Rendering to String**:
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

    // ... other methods
}

// Usage
Expr query = Exprs.and(List.of(...));
String rendered = query.accept(new ExprStringRenderer());
```

---

## 🔐 Canonicalization

**Purpose**: Generate **deterministic** JSON and hashes for:
- Caching query results
- Deduplicating queries
- Audit trails

**Class**: `ExprCanonicalizer`

**Process**:
1. Serialize expression to JSON
2. **Normalize**: Sort object keys, deduplicate arrays, trim whitespace
3. **Hash**: SHA-256 of canonical JSON

**Example**:
```java
Expr query = Exprs.and(List.of(
    Exprs.term("title", "cancer", TextMatch.CONTAINS),
    Exprs.rangeDate("date", from, to)
));

ExprCanonicalSnapshot snapshot = ExprCanonicalizer.canonicalize(query);

snapshot.expr();           // Original expression
snapshot.canonicalJson();  // Deterministic JSON string
snapshot.hash();           // SHA-256 hash (e.g., "a3f2c8b...")
```

**Use Cases**:
- **Cache key**: Use `hash` as Redis key for query results
- **Deduplication**: Detect identical queries even if JSON field order differs
- **Audit**: Store `canonicalJson` + `hash` in plan metadata

---

## 📦 Dependencies

**Minimal dependencies for portability**:

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

**NO Spring, NO MyBatis** — pure Java library.

---

## 🔌 Integration with Papertrace

### In patra-ingest

**Use Case**: Build plan expressions from trigger parameters and provenance config.

**Component**: `PlanExpressionBuilder` (in `patra-ingest-app`)

**Example**:
```java
@Component
public class PlanExpressionBuilder {

    public PlanExpressionDescriptor build(PlanTriggerNorm norm, ProvenanceConfigSnapshot config) {
        // Build expression based on operation type
        Expr expr = switch (norm.operationCode()) {
            case HARVEST -> buildHarvestExpression(norm, config);
            case UPDATE -> buildUpdateExpression(norm, config);
            case COMPENSATION -> buildCompensationExpression(norm, config);
        };

        // Canonicalize for storage
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

### In patra-registry

**Use Case**: Store expression capabilities and render rules.

**Tables**:
- `reg_prov_expr_capability`: Defines what operations each provenance supports
- `reg_prov_expr_render_rule`: Rules for rendering expressions to API-specific syntax

---

## 🛠️ Extending

### Adding a New Operator

**Example**: Add `FUZZY` operator for fuzzy text matching.

#### Step 1: Add Operator Enum

```java
public enum Operator {
    TERM(TermValue.class),
    IN(InValues.class),
    RANGE(RangeValue.class),
    EXISTS(ExistsFlag.class),
    TOKEN(TokenValue.class),
    FUZZY(FuzzyValue.class);  // NEW
    // ...
}
```

#### Step 2: Define Value Type

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

#### Step 3: Add Factory Method

```java
public static Expr fuzzy(String field, String value, int maxEdits) {
    return new Atom(field, Operator.FUZZY, new FuzzyValue(value, maxEdits));
}
```

#### Step 4: Update Visitor

```java
// In your custom visitor
@Override
public String visitAtom(Atom atom) {
    return switch (atom.operator()) {
        case TERM -> renderTerm(atom);
        case IN -> renderIn(atom);
        case FUZZY -> renderFuzzy(atom);  // NEW
        // ...
    };
}
```

---

## 🧪 Testing

### Unit Tests

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
    assertEquals(64, snapshot.hash().length());  // SHA-256 hex length
    assertTrue(snapshot.canonicalJson().contains("\"fieldKey\":\"title\""));
}

@Test
void testCanonicalDeduplication() {
    // Given: Same expression with different JSON field orders
    Expr expr1 = Exprs.and(List.of(termA, termB));
    Expr expr2 = Exprs.and(List.of(termB, termA));  // Different order

    // When
    String hash1 = ExprCanonicalizer.canonicalize(expr1).hash();
    String hash2 = ExprCanonicalizer.canonicalize(expr2).hash();

    // Then: Hashes are identical (order-independent)
    assertEquals(hash1, hash2);
}
```

---

## 📊 Design Decisions

### Why Sealed Interfaces?

**Problem**: Expression trees could be extended unsafely at runtime.

**Solution**: Sealed interfaces restrict subtypes → exhaustive pattern matching.

**Benefits**:
- Compiler ensures all cases handled in `switch`
- No runtime surprises
- Clear domain model

### Why Visitor Pattern?

**Problem**: Adding new operations (rendering, validation) requires modifying expression classes.

**Solution**: Visitor pattern separates traversal logic from node structure.

**Benefits**:
- Add new operations without changing expression classes
- Multiple renderers (PubMed, EPMC, Elasticsearch) coexist
- Follows Open/Closed Principle

### Why Canonicalization?

**Problem**: Same logical query can have different JSON representations (field order, whitespace).

**Solution**: Normalize + hash for deterministic identity.

**Benefits**:
- Cache query results reliably
- Detect duplicate queries
- Audit trail with consistent fingerprints

---

## 📈 Performance Considerations

**Immutability**: All expression nodes are immutable → safe to share across threads.

**No Lazy Evaluation**: Expressions are eagerly constructed (not lazy ASTs).

**Small Memory Footprint**: Records use compact representation.

**Canonicalization Cost**: O(n log n) for sorting, but only done once per plan creation.

---

## 🔗 Related Documentation

- [Main README](../README.md)
- [patra-ingest README](../patra-ingest/README.md) — Where expressions are built
- [patra-registry README](../patra-registry/README.md) — Where expression metadata is stored
- [patra-common README](../patra-common/README.md) — Shared utilities (HashUtils)

---

**Last Updated**: 2025-01-12
