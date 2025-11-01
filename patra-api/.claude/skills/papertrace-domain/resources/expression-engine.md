# Expression Engine Deep Dive

## Overview

The Expression Engine is Papertrace's abstraction layer that decouples the business query model from provider-specific API parameters. This enables:

- **Unified Query Interface**: Write queries once, execute across multiple providers
- **Provider Flexibility**: Add new providers without changing query logic
- **Capability Discovery**: Understand what each provider supports
- **Testability**: Mock Expression rendering without hitting real APIs

---

## Architecture

### Three-Layer Model

```
┌──────────────────────────────────────────────────┐
│ Layer 1: Abstract Query (Business Model)        │
│ {                                                │
│   "field": "publicationDate",                    │
│   "capability": "range",                         │
│   "values": {                                    │
│     "start": "2024-01-01",                       │
│     "end": "2024-12-31"                          │
│   }                                              │
│ }                                                │
└──────────────────────────────────────────────────┘
                     │
                     ▼ (Expression Engine)
┌──────────────────────────────────────────────────┐
│ Layer 2: Expression (Mapping Rules)             │
│ {                                                │
│   "provenance": "PUBMED",                        │
│   "exprField": "publicationDate",                │
│   "capability": "range",                         │
│   "renderRule": "mindate={start}&maxdate={end}" │
│ }                                                │
└──────────────────────────────────────────────────┘
                     │
                     ▼ (Render)
┌──────────────────────────────────────────────────┐
│ Layer 3: Provider API Parameters                │
│ "mindate=2024-01-01&maxdate=2024-12-31"         │
└──────────────────────────────────────────────────┘
```

---

## Core Concepts

### 1. ExprField (Abstract Field)

**Definition**: A business-level field that represents a searchable attribute in Papertrace's unified data model.

**Standard Fields**:

| ExprField | Description | Example Values |
|-----------|-------------|----------------|
| `publicationDate` | When article was published | 2024-01-01, 2023-12-31 |
| `author` | Author name | "Smith J", "Garcia M" |
| `title` | Article title | "COVID-19 treatment" |
| `journal` | Journal name | "Nature", "Science" |
| `keyword` | Subject keyword | "cancer", "immunology" |
| `pmid` | PubMed ID | "38123456" |
| `doi` | Digital Object Identifier | "10.1038/s41586-024-..." |
| `affiliation` | Author affiliation | "Harvard Medical School" |
| `language` | Publication language | "eng", "spa" |

**Custom Fields** (provider-specific):
- `mesh`: MeSH terms (PubMed only)
- `grantId`: Funding grant ID (EPMC)
- `funder`: Funding organization (Crossref)

---

### 2. Capability (Query Operation)

**Definition**: The type of query operation supported for a field.

**Standard Capabilities**:

| Capability | Description | Query Example | Use Case |
|------------|-------------|---------------|----------|
| `exact` | Exact match | `author=Smith` | Known author name |
| `range` | Range query (inclusive) | `publicationDate=[2024-01-01,2024-12-31]` | Time windows |
| `wildcard` | Pattern matching | `title=*COVID*` | Partial match |
| `fuzzy` | Fuzzy match (Levenshtein) | `author~Smyth` | Spelling variations |
| `prefix` | Prefix match | `journal=Nat*` | Starts with |
| `in` | Set membership | `pmid in [123, 456, 789]` | Multiple IDs |
| `exists` | Field presence | `doi exists` | Has DOI? |

**Capability Support Matrix**:

|  | PubMed | EPMC | Crossref |
|--|--------|------|----------|
| **publicationDate** | range | range | range |
| **author** | exact, wildcard | exact, fuzzy | exact |
| **title** | wildcard | wildcard, fuzzy | wildcard |
| **journal** | exact | exact, prefix | exact |
| **keyword** | exact | exact, fuzzy | (not supported) |
| **pmid** | exact, in | exact | (not supported) |
| **doi** | exact | exact | exact, prefix |

---

### 3. RenderRule (Template)

**Definition**: A provider-specific template that maps abstract fields and capabilities to API parameters.

**Template Syntax**:

```
renderRule: "param1={placeholder1}&param2={placeholder2}"
```

**Placeholders**: Surrounded by `{}`, replaced at runtime with actual values.

**Examples**:

**PubMed publicationDate range**:
```
renderRule: "mindate={start}&maxdate={end}"

Values: {start: "2024-01-01", end: "2024-12-31"}
Rendered: "mindate=2024-01-01&maxdate=2024-12-31"
```

**EPMC publicationDate range (Lucene syntax)**:
```
renderRule: "PUB_YEAR:[{startYear} TO {endYear}]"

Values: {startYear: "2024", endYear: "2024"}
Rendered: "PUB_YEAR:[2024 TO 2024]"
```

**Crossref publicationDate range**:
```
renderRule: "from-pub-date={start}&until-pub-date={end}"

Values: {start: "2024-01-01", end: "2024-12-31"}
Rendered: "from-pub-date=2024-01-01&until-pub-date=2024-12-31"
```

---

## Expression Domain Model

### Expression Entity

```java
// patra-expr-kernel/src/main/java/com/patra/expr/domain/model/
public class Expression {
    private ExpressionId id;
    private ProvenanceCode provenanceCode;
    private String exprField;
    private String capability;
    private String renderRule;
    private boolean isActive;
    private int priority;  // For conflict resolution
    private Instant effectiveFrom;
    private Instant effectiveTo;

    // Business logic
    public String render(Map<String, Object> values) {
        String result = renderRule;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue().toString();
            result = result.replace(placeholder, value);
        }
        return result;
    }

    public boolean supports(String field, String cap) {
        return this.exprField.equals(field) &&
               this.capability.equals(cap) &&
               this.isActive;
    }
}
```

### Expression Repository Port

```java
public interface ExpressionPort {
    List<Expression> findByProvenance(ProvenanceCode provenanceCode);

    Optional<Expression> findByProvenanceAndFieldAndCapability(
        ProvenanceCode provenanceCode,
        String exprField,
        String capability
    );

    void save(Expression expression);
}
```

---

## Expression Rendering Process

### Step-by-Step Example

**Input Query**:
```json
{
  "provenance": "PUBMED",
  "filters": [
    {
      "field": "publicationDate",
      "capability": "range",
      "values": {
        "start": "2024-01-01",
        "end": "2024-12-31"
      }
    },
    {
      "field": "author",
      "capability": "exact",
      "values": {
        "name": "Smith J"
      }
    }
  ]
}
```

**Step 1: Load Expressions**

```java
List<Expression> expressions = expressionRepository.findByProvenance(
    ProvenanceCode.PUBMED
);

// Result:
// Expression 1: publicationDate + range → "mindate={start}&maxdate={end}"
// Expression 2: author + exact → "author={name}"
```

**Step 2: Match Filters to Expressions**

```java
for (Filter filter : query.filters()) {
    Optional<Expression> expr = expressions.stream()
        .filter(e -> e.supports(filter.field(), filter.capability()))
        .findFirst();

    if (expr.isEmpty()) {
        throw new ExpressionNotFoundException(
            "No expression for " + filter.field() + ":" + filter.capability()
        );
    }

    renderedParams.add(expr.get().render(filter.values()));
}
```

**Step 3: Render Each Expression**

```java
// Filter 1: publicationDate + range
Expression dateExpr = expressions[0];
String rendered1 = dateExpr.render(Map.of(
    "start", "2024-01-01",
    "end", "2024-12-31"
));
// Result: "mindate=2024-01-01&maxdate=2024-12-31"

// Filter 2: author + exact
Expression authorExpr = expressions[1];
String rendered2 = authorExpr.render(Map.of(
    "name", "Smith J"
));
// Result: "author=Smith J"
```

**Step 4: Combine Parameters**

```java
String finalUrl = baseUrl + "/esearch.fcgi?db=pubmed&" +
    String.join("&", renderedParams);

// Result:
// https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?
//   db=pubmed&mindate=2024-01-01&maxdate=2024-12-31&author=Smith J
```

---

## Advanced Features

### 1. Multi-Value Rendering

**Use Case**: Searching for multiple PMIDs

**Query**:
```json
{
  "field": "pmid",
  "capability": "in",
  "values": {
    "ids": ["38123456", "38234567", "38345678"]
  }
}
```

**Expression**:
```java
Expression pmidInExpr = new Expression(
    ...,
    ProvenanceCode.PUBMED,
    "pmid",
    "in",
    "id={ids|join(',')}", // Custom function: join with comma
    ...
);
```

**Rendered**:
```
"id=38123456,38234567,38345678"
```

**Implementation**:
```java
public String render(Map<String, Object> values) {
    String result = renderRule;

    for (Map.Entry<String, Object> entry : values.entrySet()) {
        String placeholder = "{" + entry.getKey() + "}";

        // Check for custom functions
        if (renderRule.contains(placeholder + "|join")) {
            List<String> list = (List<String>) entry.getValue();
            String separator = extractSeparator(renderRule, entry.getKey());
            String joined = String.join(separator, list);
            result = result.replaceAll("\\{" + entry.getKey() + "\\|join\\([^)]+\\)\\}", joined);
        } else {
            result = result.replace(placeholder, entry.getValue().toString());
        }
    }

    return result;
}
```

---

### 2. Conditional Rendering

**Use Case**: Different formats for different date precisions

**Query 1** (day precision):
```json
{
  "field": "publicationDate",
  "capability": "range",
  "values": {
    "start": "2024-01-15",
    "end": "2024-01-20",
    "precision": "day"
  }
}
```

**Query 2** (year precision):
```json
{
  "field": "publicationDate",
  "capability": "range",
  "values": {
    "start": "2024",
    "end": "2024",
    "precision": "year"
  }
}
```

**Expression with Conditional**:
```java
// renderRule with conditional syntax
"{{if precision=='day'}}mindate={start}&maxdate={end}{{else}}PUB_YEAR:{start}{{endif}}"
```

**Rendered**:
```
Query 1: "mindate=2024-01-15&maxdate=2024-01-20"
Query 2: "PUB_YEAR:2024"
```

---

### 3. URL Encoding

**Problem**: Special characters in values need URL encoding.

**Query**:
```json
{
  "field": "title",
  "capability": "wildcard",
  "values": {
    "pattern": "COVID-19 & Long COVID"
  }
}
```

**Naive Rendering**:
```
"title=COVID-19 & Long COVID"  ❌ (& breaks URL)
```

**Correct Rendering** (with URL encoding):
```
"title=COVID-19%20%26%20Long%20COVID"  ✅
```

**Implementation**:
```java
public String render(Map<String, Object> values) {
    String result = renderRule;

    for (Map.Entry<String, Object> entry : values.entrySet()) {
        String placeholder = "{" + entry.getKey() + "}";
        String value = URLEncoder.encode(
            entry.getValue().toString(),
            StandardCharsets.UTF_8
        );
        result = result.replace(placeholder, value);
    }

    return result;
}
```

---

### 4. Priority-Based Expression Selection

**Problem**: Multiple Expressions for same field + capability.

**Scenario**: PubMed has two ways to query by date:
1. `mindate/maxdate` (E-utilities)
2. `datetype=pdat&mindate/maxdate` (more precise)

**Expressions**:
```java
Expression basic = new Expression(
    ...,
    "publicationDate",
    "range",
    "mindate={start}&maxdate={end}",
    priority=1  // Lower priority
);

Expression precise = new Expression(
    ...,
    "publicationDate",
    "range",
    "datetype=pdat&mindate={start}&maxdate={end}",
    priority=10  // Higher priority (preferred)
);
```

**Selection Logic**:
```java
Optional<Expression> findBestExpression(
    String field,
    String capability,
    List<Expression> candidates
) {
    return candidates.stream()
        .filter(e -> e.supports(field, capability))
        .max(Comparator.comparingInt(Expression::getPriority));
}
```

**Result**: `precise` Expression selected (priority 10 > 1).

---

## Real-World Provider Examples

### PubMed (E-utilities)

**API Documentation**: https://www.ncbi.nlm.nih.gov/books/NBK25499/

**Common Expressions**:

```java
// publicationDate range
new Expression(
    ProvenanceCode.PUBMED,
    "publicationDate",
    "range",
    "mindate={start}&maxdate={end}"
);

// author exact
new Expression(
    ProvenanceCode.PUBMED,
    "author",
    "exact",
    "author={name}"
);

// title wildcard (PubMed uses [Title] field tag)
new Expression(
    ProvenanceCode.PUBMED,
    "title",
    "wildcard",
    "term={pattern}[Title]"
);

// pmid exact
new Expression(
    ProvenanceCode.PUBMED,
    "pmid",
    "exact",
    "id={pmid}"
);

// MeSH term (PubMed-specific)
new Expression(
    ProvenanceCode.PUBMED,
    "mesh",
    "exact",
    "term={meshTerm}[MeSH Terms]"
);
```

**Example Rendered URL**:
```
https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?
  db=pubmed&
  mindate=2024-01-01&
  maxdate=2024-12-31&
  author=Smith J&
  retstart=0&
  retmax=1000
```

---

### Europe PMC

**API Documentation**: https://europepmc.org/RestfulWebService

**Common Expressions**:

```java
// publicationDate range (Lucene syntax)
new Expression(
    ProvenanceCode.EPMC,
    "publicationDate",
    "range",
    "PUB_YEAR:[{startYear} TO {endYear}]"
);

// author exact
new Expression(
    ProvenanceCode.EPMC,
    "author",
    "exact",
    "AUTH:{name}"
);

// title fuzzy (EPMC supports fuzzy~)
new Expression(
    ProvenanceCode.EPMC,
    "title",
    "fuzzy",
    "TITLE:{pattern}~"
);

// grantId (EPMC-specific)
new Expression(
    ProvenanceCode.EPMC,
    "grantId",
    "exact",
    "GRANT_ID:{grantId}"
);
```

**Example Rendered URL**:
```
https://www.ebi.ac.uk/europepmc/webservices/rest/search?
  query=PUB_YEAR:[2024 TO 2024] AND AUTH:Smith%20J&
  pageSize=1000&
  cursorMark=*
```

---

### Crossref

**API Documentation**: https://api.crossref.org/

**Common Expressions**:

```java
// publicationDate range
new Expression(
    ProvenanceCode.CROSSREF,
    "publicationDate",
    "range",
    "filter=from-pub-date:{start},until-pub-date:{end}"
);

// author exact (structured query)
new Expression(
    ProvenanceCode.CROSSREF,
    "author",
    "exact",
    "query.author={name}"
);

// doi exact
new Expression(
    ProvenanceCode.CROSSREF,
    "doi",
    "exact",
    "filter=doi:{doi}"
);

// funder (Crossref-specific)
new Expression(
    ProvenanceCode.CROSSREF,
    "funder",
    "exact",
    "filter=funder:{funderId}"
);
```

**Example Rendered URL**:
```
https://api.crossref.org/works?
  filter=from-pub-date:2024-01-01,until-pub-date:2024-12-31&
  query.author=Smith J&
  rows=1000&
  offset=0
```

---

## Testing Strategies

### 1. Unit Testing Expression Rendering

```java
@Test
void testRenderPublicationDateRange() {
    Expression expr = new Expression(
        ExpressionId.generate(),
        ProvenanceCode.PUBMED,
        "publicationDate",
        "range",
        "mindate={start}&maxdate={end}",
        true,
        1,
        Instant.now(),
        null
    );

    Map<String, Object> values = Map.of(
        "start", "2024-01-01",
        "end", "2024-12-31"
    );

    String rendered = expr.render(values);

    assertThat(rendered).isEqualTo("mindate=2024-01-01&maxdate=2024-12-31");
}
```

### 2. Integration Testing with Mock API

```java
@Test
void testEndToEndQueryRendering() {
    // 1. Setup: Load Expressions from repository
    List<Expression> expressions = expressionRepository.findByProvenance(
        ProvenanceCode.PUBMED
    );

    // 2. Create abstract query
    Query query = new Query(
        ProvenanceCode.PUBMED,
        List.of(
            new Filter("publicationDate", "range", Map.of("start", "2024-01-01", "end", "2024-12-31")),
            new Filter("author", "exact", Map.of("name", "Smith J"))
        )
    );

    // 3. Render to API URL
    String url = queryRenderer.render(query, expressions);

    // 4. Assert
    assertThat(url).contains("mindate=2024-01-01");
    assertThat(url).contains("maxdate=2024-12-31");
    assertThat(url).contains("author=Smith J");
}
```

---

## Common Pitfalls and Solutions

### Pitfall 1: Missing URL Encoding

**Problem**:
```java
// values = {name: "O'Brien"}
renderRule = "author={name}"
rendered = "author=O'Brien"  // ❌ Single quote breaks URL
```

**Solution**: Always URL-encode values.

---

### Pitfall 2: Ambiguous Placeholder Names

**Problem**:
```java
renderRule = "from-date={date}&to-date={date}"  // ❌ Ambiguous
values = {date: "???"}  // Which date?
```

**Solution**: Use distinct placeholder names.
```java
renderRule = "from-date={startDate}&to-date={endDate}"  // ✅
values = {startDate: "2024-01-01", endDate: "2024-12-31"}
```

---

### Pitfall 3: Missing Expression for Provider

**Problem**: User queries with `fuzzy` capability, but provider doesn't support it.

**Detection**:
```java
Optional<Expression> expr = findExpression("author", "fuzzy", ProvenanceCode.CROSSREF);
if (expr.isEmpty()) {
    throw new UnsupportedCapabilityException(
        "Crossref does not support fuzzy search for author"
    );
}
```

**User-Friendly Error**:
```
"Fuzzy search is not supported for author in Crossref.
Supported capabilities: exact, wildcard.
Try using exact match or wildcard search instead."
```

---

## Best Practices

### 1. Version Expressions with effectiveFrom/effectiveTo

**Why**: APIs evolve. PubMed might change parameter names.

**Example**:
```java
// Old Expression (expires 2024-12-31)
Expression oldExpr = new Expression(
    ...,
    "publicationDate",
    "range",
    "mindate={start}&maxdate={end}",
    effectiveTo=Instant.parse("2024-12-31T23:59:59Z")
);

// New Expression (effective 2025-01-01)
Expression newExpr = new Expression(
    ...,
    "publicationDate",
    "range",
    "pub-date-start={start}&pub-date-end={end}",  // API changed
    effectiveFrom=Instant.parse("2025-01-01T00:00:00Z")
);
```

### 2. Test Expressions with Real APIs

**Why**: Provider docs may be outdated or incomplete.

**Strategy**:
```java
@Test
void testPubMedDateRangeWithRealAPI() {
    String url = renderExpression(...);
    HttpResponse<String> response = httpClient.send(GET(url));
    assertThat(response.statusCode()).isEqualTo(200);
    // Verify response structure
}
```

### 3. Document Provider Quirks

**Example**:
```java
/**
 * PubMed Quirk: mindate/maxdate require YYYY/MM/DD format, NOT ISO-8601.
 *
 * ✅ Correct: mindate=2024/01/01
 * ❌ Wrong: mindate=2024-01-01 (will return 0 results)
 */
new Expression(
    ...,
    "publicationDate",
    "range",
    "mindate={start}&maxdate={end}",
    ...
);

// In rendering logic:
String formattedDate = date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
```

---

## Summary

**Expression Engine Benefits**:
- ✅ **Abstraction**: Business logic independent of provider APIs
- ✅ **Flexibility**: Easy to add new providers
- ✅ **Testability**: Mock rendering without API calls
- ✅ **Versioning**: Handle API changes gracefully

**Key Components**:
- **ExprField**: Abstract business field (e.g., `publicationDate`)
- **Capability**: Query operation type (e.g., `range`, `exact`)
- **RenderRule**: Provider-specific template (e.g., `mindate={start}&maxdate={end}`)

**Workflow**:
1. User submits abstract query
2. Engine loads Expressions for target Provenance
3. Match each filter to Expression by field + capability
4. Render templates with actual values
5. Combine into final API URL

**See Also**:
- [business-concepts.md](business-concepts.md) for Expression definition
- [provenance-config-system.md](provenance-config-system.md) for Expression versioning
