# 表达式引擎

## 概览

表达式引擎在业务查询概念和特定提供者 API 参数之间提供了一个**抽象层**。表达式存储在 `patra-registry` 中，并通过快照机制被 `patra-ingest` 消费。

**核心特性**：
- **四表架构**：字段字典 → 能力 → 渲染规则 → API 参数映射
- **基于快照**：表达式在 Plan 创建时进行快照（`expr_proto_snapshot`）
- **在切片层本地化**：切片表达式注入时间窗口边界（`expr_snapshot`）
- **基于哈希的标识**：通过 SHA256 哈希进行变更检测
- **两种发射模式**：QUERY（模板渲染）vs PARAMS（结构化 JSON）

---

## Architecture

### Registry Schema (4 Tables)

```
reg_expr_field_dict              - Business field definitions (abstract layer)
    ↓ 1:N
reg_expr_capability              - Operations supported per field (TERM/IN/RANGE/EXISTS/TOKEN)
    ↓ 1:N
reg_prov_expr_render_rule        - Provenance-specific rendering rules (template + emission)
    ↓ 1:N
reg_prov_api_param_mapping       - Final API parameter mappings
```

---

### Table 1: reg_expr_field_dict

**目的**: Define business-level queryable fields (abstract layer).

**Schema**:
```sql
CREATE TABLE reg_expr_field_dict (
  id BIGINT PRIMARY KEY,
  field_key VARCHAR(64) NOT NULL,           -- 'publication_date', 'title', 'author'
  field_name VARCHAR(128),                  -- Display name
  field_type_code VARCHAR(32),              -- DATA_TYPE: DATE/TEXT/NUMBER
  description TEXT,
  UNIQUE KEY uk_field_key(field_key)
);
```

**Example Rows**:
| field_key | field_name | field_type_code |
|-----------|------------|-----------------|
| `publication_date` | Publication Date | DATE |
| `title` | Article Title | TEXT |
| `author` | Author Name | TEXT |
| `journal` | Journal Name | TEXT |

---

### Table 2: reg_expr_capability

**目的**: Define which operations each field supports.

**Schema**:
```sql
CREATE TABLE reg_expr_capability (
  id BIGINT PRIMARY KEY,
  field_id BIGINT NOT NULL,                 -- FK to reg_expr_field_dict
  operation_code VARCHAR(32) NOT NULL,      -- 'TERM', 'IN', 'RANGE', 'EXISTS', 'TOKEN'
  value_mode_code VARCHAR(32),              -- 'ANY', 'PHRASE', 'EXACT'
  description TEXT,
  UNIQUE KEY uk_field_op_mode(field_id, operation_code, value_mode_code)
);
```

**Operation Codes**:

| Operation | Description | Use Case |
|-----------|-------------|----------|
| **TERM** | Single term match | `publication_date:2024-01-01` |
| **IN** | Multiple value match | `journal IN ('Nature', 'Science')` |
| **RANGE** | Range query | `publication_date:[2024-01-01 TO 2024-12-31]` |
| **EXISTS** | Field presence check | `EXISTS(doi)` |
| **TOKEN** | Token-based search | Full-text tokenization |

**Value Modes** (for TERM):
- **ANY**: Any word match
- **PHRASE**: Exact phrase match
- **EXACT**: Exact string match

---

### Table 3: reg_prov_expr_render_rule

**目的**: Provenance-specific rendering templates.

**Schema**:
```sql
CREATE TABLE reg_prov_expr_render_rule (
  id BIGINT PRIMARY KEY,
  provenance_id BIGINT NOT NULL,
  capability_id BIGINT NOT NULL,            -- FK to reg_expr_capability
  template TEXT,                            -- Mustache template: '{{v}}[TIAB]', '"{{v}}"[TIAB]'
  emission_mode_code VARCHAR(32),           -- 'QUERY' or 'PARAMS'
  params JSON,                              -- For PARAMS mode: {"from":"{{from}}", "to":"{{to}}"}
  priority INT,                             -- Rendering priority
  UNIQUE KEY uk_prov_cap(provenance_id, capability_id)
);
```

**Emission Modes**:

| Mode | Description | Output Target |
|------|-------------|---------------|
| **QUERY** | Render to query string | Appended to URL query params |
| **PARAMS** | Emit structured params | Merged into request body/params |

**Template Syntax** (Mustache-style):
- `{{v}}`: Single value placeholder
- `{{from}}`, `{{to}}`: Range boundaries
- `{{datetype}}`: Date type parameter (PubMed-specific)

---

### Table 4: reg_prov_api_param_mapping

**目的**: Map abstract fields to provider API parameter names.

**Schema**:
```sql
CREATE TABLE reg_prov_api_param_mapping (
  id BIGINT PRIMARY KEY,
  provenance_id BIGINT NOT NULL,
  field_id BIGINT NOT NULL,
  api_param_key VARCHAR(128),               -- Provider API param name: 'tiab', 'pdat', 'auth'
  UNIQUE KEY uk_prov_field(provenance_id, field_id)
);
```

**Example** (PubMed):
| field_key (abstract) | api_param_key (PubMed-specific) |
|----------------------|---------------------------------|
| `title` | `tiab` (Title/Abstract) |
| `publication_date` | `pdat` (Publication Date) |
| `author` | `auth` (Author) |

---

## Expression Snapshot Flow

### 1. Registry: ExprSnapshot Creation

**Repository Method**:
```java
ExprSnapshot loadSnapshot(
    String provenanceCode,
    String operationType,
    String endpointName,
    Instant at
)
```

**Returns**:
```java
record ExprSnapshot(
    List<ExprField> fields,              // Field dictionary entries
    List<ExprCapability> capabilities,   // Supported operations
    List<ExprRenderRule> renderRules,    // Rendering templates
    List<ApiParamMapping> apiParamMappings  // API param mappings
)
```

---

### 2. Registry API: REST Endpoint

**Endpoint**:
```
GET /_internal/expr/snapshot?
    provenanceCode=PUBMED&
    operationType=HARVEST&
    endpointName=null&
    at=2024-11-02T12:00:00Z
```

**Response** (ExprSnapshotResp DTO):
```json
{
  "fields": [
    {"fieldKey": "publication_date", "fieldName": "Publication Date", "fieldTypeCode": "DATE"},
    {"fieldKey": "title", "fieldName": "Title", "fieldTypeCode": "TEXT"}
  ],
  "capabilities": [
    {"fieldKey": "publication_date", "operationCode": "RANGE", "valueModeCode": null},
    {"fieldKey": "title", "operationCode": "TERM", "valueModeCode": "ANY"}
  ],
  "renderRules": [
    {"fieldKey": "publication_date", "operationCode": "RANGE", "emissionModeCode": "PARAMS",
     "params": {"from":"{{from}}", "to":"{{to}}", "datetype":"{{datetype}}"}},
    {"fieldKey": "title", "operationCode": "TERM", "template": "{{v}}[TIAB]", "emissionModeCode": "QUERY"}
  ],
  "apiParamMappings": [
    {"fieldKey": "publication_date", "apiParamKey": "pdat"},
    {"fieldKey": "title", "apiParamKey": "tiab"}
  ]
}
```

---

### 3. Ingest: Plan Expression (Prototype)

**Plan Creation** (PlanAssemblerImpl):
```java
// Fetch expression snapshot from registry
ExprSnapshotResp exprSnapshot = exprClient.getSnapshot(
    provenanceCode, operationType, null, Instant.now()
);

// Serialize to JSON
JsonNode exprProtoSnapshotJson = objectMapper.valueToTree(exprSnapshot);

// Hash for change detection
String exprProtoHash = hashCalculator.sha256(exprProtoSnapshotJson);

// Store in Plan
PlanAggregate plan = PlanAggregate.create(
    ...,
    exprProtoHash,           // SHA256 hash
    exprProtoSnapshotJson,   // Full snapshot JSON
    ...
);
```

**Storage**:
- **ing_plan.expr_proto_hash**: `SHA256(expr_proto_snapshot)` (for change detection)
- **ing_plan.expr_proto_snapshot**: `JsonNode` (immutable prototype)

---

### 4. Ingest: Slice Expression (Localized)

**Slice Creation** (SlicePlanner):
```java
// Copy prototype from Plan
JsonNode planExprProto = plan.getExprProtoSnapshotJson();

// Localize: inject window boundaries into expression
JsonNode localizedExpr = expressionLocalizer.localize(
    planExprProto,
    sliceWindow  // WindowSpec.Time(from, to)
);

// Hash localized expression
String exprHash = hashCalculator.sha256(localizedExpr);

// Create Slice with localized expression
PlanSliceAggregate slice = PlanSliceAggregate.create(
    ...,
    exprHash,           // SHA256 of localized expression
    localizedExpr,      // JSON with window boundaries
    ...
);
```

**Storage**:
- **ing_plan_slice.expr_hash**: `SHA256(expr_snapshot)` (localized hash)
- **ing_plan_slice.expr_snapshot_json**: `TEXT` (localized expression with boundaries)

---

## Expression Rendering

### Rendering Context

**At Task Execution**:
```java
// Load slice expression
String exprSnapshotJson = slice.getExprSnapshotJson();
ExprSnapshot expr = objectMapper.readValue(exprSnapshotJson, ExprSnapshot.class);

// Pass to rendering engine (patra-expr-kernel)
QueryRenderResult result = exprRenderer.render(expr, userQuery);
```

### Two Emission Modes

**1. QUERY Mode** (URL Query String):
```
Template: "{{v}}[TIAB]"
Input: {v: "cancer"}
Output: "cancer[TIAB]"

Final URL: https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?term=cancer[TIAB]
```

**2. PARAMS Mode** (Structured Parameters):
```
Params: {"from":"{{from}}", "to":"{{to}}", "datetype":"{{datetype}}"}
Input: {from: "2024-01-01", to: "2024-12-31", datetype: "edat"}
Output: {"from": "2024-01-01", "to": "2024-12-31", "datetype": "edat"}

Final Request Body: {"from": "2024-01-01", "to": "2024-12-31", "datetype": "edat"}
```

---

## Real-World Example: PubMed Publication Date Range

### Registry Data

**Field Dictionary**:
```
field_key: 'publication_date'
field_name: 'Publication Date'
field_type_code: 'DATE'
```

**Capability**:
```
field_key: 'publication_date'
operation_code: 'RANGE'
value_mode_code: null
```

**Render Rule** (PubMed):
```
provenance_code: 'PUBMED'
capability_id: (publication_date, RANGE)
emission_mode_code: 'PARAMS'
params: {"from":"{{from}}", "to":"{{to}}", "datetype":"{{datetype}}"}
```

**API Param Mapping**:
```
field_key: 'publication_date'
api_param_key: 'pdat'  (PubMed-specific)
```

### Expression Flow

**Step 1: Plan Creation**
```
ExprSnapshot loaded from registry:
  - Field: publication_date (DATE)
  - Capability: RANGE
  - RenderRule: PARAMS mode with {"from":"{{from}}", "to":"{{to}}"}
  - ApiParamMapping: pdat

Stored in ing_plan.expr_proto_snapshot (no boundaries yet)
```

**Step 2: Slice Creation**
```
Slice window: 2024-01-01 to 2024-01-31

Localize expression:
  - Inject from=2024-01-01, to=2024-01-31
  - Store in ing_plan_slice.expr_snapshot_json
```

**Step 3: Task Execution**
```
Load expr_snapshot_json from Slice
Render with params:
  from: "2024-01-01"
  to: "2024-01-31"
  datetype: "edat"  (EntrezDate)

Final API params:
  pdat: {"from": "2024-01-01", "to": "2024-01-31", "datetype": "edat"}
```

---

## Expression Hashing (Change Detection)

### Two-Level Hashing

**1. Plan Expression Hash** (`expr_proto_hash`):
```
Input: ExprSnapshot JSON (no boundaries)
Algorithm: SHA256
Purpose: Detect expression definition changes
```

**2. Slice Expression Hash** (`expr_hash`):
```
Input: Localized ExprSnapshot JSON (with boundaries)
Algorithm: SHA256
Purpose: Idempotency for slice expression
```

**Use Case**:
```
If exprProtoHash changes → Expression definition updated in registry
If exprHash changes → Slice window boundaries changed
```

---

## Integration (Registry → Ingest)

### API Contract

**Request**:
```
GET /_internal/expr/snapshot?provenanceCode={code}&operationType={op}&at={timestamp}
```

**Response**:
```java
record ExprSnapshotResp(
    List<ExprFieldDto> fields,
    List<ExprCapabilityDto> capabilities,
    List<ExprRenderRuleDto> renderRules,
    List<ApiParamMappingDto> apiParamMappings
)
```

### Ingest Client

**PatraRegistryPort** (domain interface):
```java
public interface PatraRegistryPort {
    ExprSnapshot fetchExprSnapshot(
        String provenanceCode,
        String operationType,
        Instant at
    );
}
```

**Implementation** (ExprClientAdapter):
```java
@Override
public ExprSnapshot fetchExprSnapshot(String provenance, String operation, Instant at) {
    ExprSnapshotResp resp = exprClient.getSnapshot(provenance, operation, null, at);
    return exprSnapshotConverter.convert(resp);
}
```

---

## Best Practices

### 1. Separate Concerns

**Good**:
- Abstract fields in `reg_expr_field_dict` (business layer)
- Provider-specific rules in `reg_prov_expr_render_rule` (adapter layer)

**Bad**:
- Hardcoding PubMed-specific field names in business logic

### 2. Use PARAMS Mode for Structured Data

**Good** (RANGE with PARAMS):
```json
{"from": "{{from}}", "to": "{{to}}", "datetype": "edat"}
```

**Bad** (RANGE with QUERY template):
```
"pdat:[{{from}} TO {{to}}]"  // Brittle, hard to validate
```

### 3. Version Expressions via Temporal Validity

Although not shown in current schema, expressions can be versioned by adding `effective_from`/`effective_to` to render rules.

---

## Troubleshooting

### Issue: "Expression not found"

**Diagnosis**:
1. Check if field exists: `SELECT * FROM reg_expr_field_dict WHERE field_key='publication_date'`
2. Check capability: `SELECT * FROM reg_expr_capability WHERE field_id=...`
3. Check render rule: `SELECT * FROM reg_prov_expr_render_rule WHERE provenance_id=... AND capability_id=...`

**Fix**: Seed missing expression data.

---

### Issue: "Wrong API parameter generated"

**Diagnosis**:
1. Check template: Verify `template` or `params` JSON in render rule
2. Check emission mode: QUERY vs PARAMS
3. Check API param mapping: `api_param_key` in `reg_prov_api_param_mapping`

**Fix**: Update render rule or param mapping.

---

## Summary

**Key Architecture**:
- ✅ 4-table registry structure (field dict → capability → render rule → api mapping)
- ✅ ExprSnapshot as immutable snapshot
- ✅ Two-level hashing (plan proto vs slice localized)
- ✅ Two emission modes (QUERY template vs PARAMS structured)

**Expression Lifecycle**:
```
1. Define in Registry (4 tables)
2. Load as ExprSnapshot (via REST API)
3. Store in Plan (expr_proto_snapshot)
4. Localize in Slice (inject boundaries → expr_snapshot)
5. Render at Task execution (patra-expr-kernel)
```

**No**:
- ❌ Single `Expression` entity with `render()` method
- ❌ Placeholder syntax `{placeholder}` (use `{{placeholder}}`)
- ❌ Capability names like `exact`, `fuzzy` (use TERM, RANGE, IN, EXISTS, TOKEN)
