# Venue Pages Backend API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement backend API endpoints for the venue pages feature (list enhancement, detail with ratings, rating history, publication stats, venue instances, instance publications, venue compare).

**Architecture:** Hexagonal architecture with CQRS read side. Each endpoint follows the flow: Controller → ApiConverter → QueryService → ReadPort → ReadAdapter (infra). All new read models are immutable records in domain layer. Tests use integration tests against real database (H2 for unit tests where possible).

**Tech Stack:** Java 25, Spring Boot 4.0.1, Spring Data JPA, MapStruct, MySQL 8.x, JUnit 5, AssertJ

---

## API Contract Summary

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /venues` | Enhanced | Add filters (jifQuartile, casMajorQuartile, casTopJournal, oaType, collection, warningStatus, researchDirection) + sort options |
| `GET /venues/{id}` | Enhanced | Include latest rating summary (JCR/CAS/Scopus) in response |
| `GET /venues/{id}/ratings` | New | Historical rating records for trend charts |
| `GET /venues/{id}/stats` | New | Annual publication stats (worksCount, citedByCount, oaWorksCount) |
| `GET /venues/{id}/instances` | New | Volume/issue list with year filter |
| `GET /venues/{id}/instances/{instanceId}/publications` | New | Publications within a specific instance |
| `GET /venues/compare` | New | Batch venue details with ratings for comparison |

---

## File Structure

### Domain Layer (`patra-catalog-domain`)

```
domain/model/read/venue/
├── VenueSummaryReadModel.java          (MODIFY - add citeScore as BigDecimal)
├── VenueDetailReadModel.java           (MODIFY - add rating summary)
├── VenueFilter.java                    (MODIFY - add new filter fields)
├── VenueRatingHistoryReadModel.java    (CREATE)
├── VenueStatsReadModel.java            (CREATE)
├── VenueInstanceSummaryReadModel.java  (CREATE)
domain/port/read/
├── VenueReadPort.java                  (MODIFY - add new methods)
```

### Application Layer (`patra-catalog-app`)

```
app/usecase/venue/query/
├── VenueQueryService.java              (MODIFY - add new query methods)
├── dto/VenueListQuery.java             (MODIFY - add filter/sort fields)
├── dto/VenueRatingHistoryQuery.java    (CREATE)
├── dto/VenueInstanceListQuery.java     (CREATE)
├── dto/VenueInstancePublicationsQuery.java (CREATE)
├── dto/VenueCompareQuery.java          (CREATE)
```

### Infrastructure Layer (`patra-catalog-infra`)

```
infra/adapter/read/
├── VenueReadAdapter.java               (MODIFY - implement new port methods)
├── VenueReadModelMapper.java           (MODIFY - add new mapping methods)
infra/persistence/dao/
├── VenueDao.java                       (MODIFY - new query with filters/sort)
├── VenueInstanceDao.java               (MODIFY - add page query)
├── VenuePublicationStatsDao.java       (MODIFY - add findByVenueId)
├── PublicationDao.java                  (MODIFY - add venueInstanceId filter)
```

### Adapter Layer (`patra-catalog-adapter`)

```
adapter/rest/venue/
├── VenueController.java                (MODIFY - add new endpoints)
├── request/VenueListRequest.java       (MODIFY - add filter/sort params)
├── request/VenueInstanceListRequest.java (CREATE)
├── request/InstancePublicationListRequest.java (CREATE)
├── response/VenueItemResponse.java     (MODIFY - add impactFactor, sjr)
├── response/VenueDetailResponse.java   (MODIFY - add rating summary)
├── response/VenueRatingHistoryResponse.java (CREATE)
├── response/VenueStatsResponse.java    (CREATE)
├── response/VenueInstanceItemResponse.java (CREATE)
├── response/VenueCompareItemResponse.java (CREATE)
├── mapper/VenueApiConverter.java       (MODIFY - add new mappings)
```

---

## Task 1: Enhanced Venue List Filters

**Goal:** Add multi-dimensional filtering (JCR quartile, CAS quartile, Top, OA type, collection, research direction, warning status) and sort options to the existing venue list endpoint.

**Files:**
- Modify: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/read/venue/VenueFilter.java`
- Modify: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/read/VenueReadPort.java`
- Modify: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/query/dto/VenueListQuery.java`
- Modify: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/query/VenueQueryService.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/dao/VenueDao.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadAdapter.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/request/VenueListRequest.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/response/VenueItemResponse.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/mapper/VenueApiConverter.java`
- Test: `patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/VenueReadAdapterIT.java`

### Design

The enhanced query needs to JOIN `cat_venue` with the three rating tables. The strategy:
- Use a **single native query** that LEFT JOINs with latest-year rating subqueries
- Filter conditions applied on the joined columns
- Sort options: `impactFactor`, `citeScore`, `hIndex`, `citedByCount` (default)

**New VenueFilter fields:**

```java
@Builder
public record VenueFilter(
    String keyword,
    String countryCode,
    String issnL,
    String nlmId,
    // New filter fields
    String jifQuartile,       // Q1/Q2/Q3/Q4
    String casMajorQuartile,  // 1区/2区/3区/4区
    Boolean casTopJournal,    // true = only top
    String oaType,            // gold/hybrid/bronze
    String collection,        // SCIE/SSCI/AHCI
    String researchDirection, // research direction keyword
    Boolean warningOnly,      // true = only warned journals
    String sortBy             // impactFactor/citeScore/hIndex/citedByCount
) {}
```

**New VenueDao query strategy:**

Rather than making the existing `findJournalPage` more complex, create a new native query method `findFilteredJournalPage` that LEFT JOINs with latest rating subqueries. This keeps the existing simple query intact for internal use.

- [ ] **Step 1: Update VenueFilter with new fields**

Add the new filter/sort fields to the VenueFilter record:

```java
@Builder
public record VenueFilter(
    String keyword,
    String countryCode,
    String issnL,
    String nlmId,
    String jifQuartile,
    String casMajorQuartile,
    Boolean casTopJournal,
    String oaType,
    String collection,
    String researchDirection,
    Boolean warningOnly,
    String sortBy) {}
```

- [ ] **Step 2: Update VenueListQuery with new fields**

```java
@Builder
public record VenueListQuery(
    Integer page,
    Integer pageSize,
    String q,
    String countryCode,
    String issnL,
    String nlmId,
    String jifQuartile,
    String casMajorQuartile,
    Boolean casTopJournal,
    String oaType,
    String collection,
    String researchDirection,
    Boolean warningOnly,
    String sortBy) {}
```

- [ ] **Step 3: Update VenueListRequest with new query params**

```java
public record VenueListRequest(
    Integer page,
    Integer pageSize,
    String q,
    String countryCode,
    String issnL,
    String nlmId,
    String jifQuartile,
    String casMajorQuartile,
    Boolean casTopJournal,
    String oaType,
    String collection,
    String researchDirection,
    Boolean warningOnly,
    String sortBy) {}
```

- [ ] **Step 4: Update VenueItemResponse to include impactFactor and more rating fields**

```java
@Builder
public record VenueItemResponse(
    Long id,
    String title,
    String countryCode,
    String imageObjectKey,
    Integer hIndex,
    BigDecimal impactFactor,
    String jifQuartile,
    String casMajorQuartile,
    Boolean casTopJournal,
    BigDecimal citeScore,
    String citeScoreQuartile,
    Boolean isOa,
    String researchDirection,
    String collection) {}
```

- [ ] **Step 5: Update VenueSummaryReadModel to match**

Add `impactFactor` (BigDecimal) and `collection` (String) fields, change `citeScore` from `Double` to `BigDecimal`.

- [ ] **Step 6: Add new native query to VenueDao**

Create `findFilteredJournalPage` that LEFT JOINs with latest rating subqueries and supports dynamic filtering/sorting:

```java
@Query(
    value = """
    SELECT v.*,
           jcr.impact_factor AS jcr_if,
           jcr.jif_quartile AS jcr_quartile,
           jcr.collection AS jcr_collection,
           jcr.research_direction AS jcr_direction,
           cas.major_quartile AS cas_quartile,
           cas.is_top_journal AS cas_top,
           scopus.cite_score AS scopus_cs,
           scopus.quartile AS scopus_quartile
    FROM cat_venue v
    LEFT JOIN LATERAL (
        SELECT * FROM cat_venue_jcr_rating r
        WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1
    ) jcr ON TRUE
    LEFT JOIN LATERAL (
        SELECT * FROM cat_venue_cas_rating r
        WHERE r.venue_id = v.id ORDER BY r.year DESC, r.edition ASC LIMIT 1
    ) cas ON TRUE
    LEFT JOIN LATERAL (
        SELECT * FROM cat_venue_scopus_rating r
        WHERE r.venue_id = v.id ORDER BY r.year DESC LIMIT 1
    ) scopus ON TRUE
    WHERE v.venue_type = 'JOURNAL'
      AND (:keyword IS NULL OR v.title LIKE CONCAT(:keyword, '%') ESCAPE '!')
      AND (:countryCode IS NULL OR v.country_code = :countryCode)
      AND (:issnL IS NULL OR v.issn_l = :issnL)
      AND (:nlmId IS NULL OR v.nlm_id = :nlmId)
      AND (:jifQuartile IS NULL OR jcr.jif_quartile = :jifQuartile)
      AND (:casMajorQuartile IS NULL OR cas.major_quartile = :casMajorQuartile)
      AND (:casTopJournal IS NULL OR cas.is_top_journal = :casTopJournal)
      AND (:collection IS NULL OR jcr.collection = :collection)
      AND (:researchDirection IS NULL OR jcr.research_direction LIKE CONCAT('%', :researchDirection, '%') ESCAPE '!')
      AND (:oaType IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(v.open_access, '$.oaType')) = :oaType)
    ORDER BY
      CASE :sortBy
        WHEN 'impactFactor' THEN COALESCE(jcr.impact_factor, 0)
        WHEN 'citeScore' THEN COALESCE(scopus.cite_score, 0)
        WHEN 'hIndex' THEN COALESCE(JSON_EXTRACT(v.citation_metrics, '$.hIndex'), 0)
        ELSE COALESCE(v.cited_by_count, 0)
      END DESC,
      v.id DESC
    """,
    nativeQuery = true)
Page<Object[]> findFilteredJournalPage(
    @Param("keyword") String keyword,
    @Param("countryCode") String countryCode,
    @Param("issnL") String issnL,
    @Param("nlmId") String nlmId,
    @Param("jifQuartile") String jifQuartile,
    @Param("casMajorQuartile") String casMajorQuartile,
    @Param("casTopJournal") Boolean casTopJournal,
    @Param("collection") String collection,
    @Param("researchDirection") String researchDirection,
    @Param("oaType") String oaType,
    @Param("sortBy") String sortBy,
    Pageable pageable);
```

**Note:** The warning filter requires a separate subquery check against `cat_venue_cas_warning`. If `:warningOnly` is true, add `AND EXISTS (SELECT 1 FROM cat_venue_cas_warning w WHERE w.venue_id = v.id AND w.in_warning_list = TRUE)`. This should be handled in the query.

- [ ] **Step 7: Update VenueReadAdapter to use new query**

Update `findVenuePage` to delegate to the new DAO method when advanced filters are used, and map `Object[]` results to `VenueSummaryReadModel`. If no advanced filters are present, fall back to the simple existing query for better performance.

- [ ] **Step 8: Update VenueQueryService to pass new filter fields**

Update the `listVenues` method to populate all new VenueFilter fields from VenueListQuery.

- [ ] **Step 9: Update VenueApiConverter mappings**

Add mappings for new fields in `toQuery`, `toItemResponse` methods.

- [ ] **Step 10: Write integration test for filtered list**

Test scenarios:
- Filter by jifQuartile = "Q1" returns only Q1 journals
- Filter by casTopJournal = true returns only Top journals
- Sort by impactFactor DESC returns highest IF first
- Combined filters (Q1 + Top + SCIE) narrow results correctly
- Warning filter returns only warned journals

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat(catalog): enhance venue list with multi-dimensional filters and sort options"
```

---

## Task 2: Enhanced Venue Detail with Rating Summary

**Goal:** Include latest JCR/CAS/Scopus rating data in the venue detail response, so the detail page header can display core metrics without a separate API call.

**Files:**
- Modify: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/read/venue/VenueDetailReadModel.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadAdapter.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadModelMapper.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/response/VenueDetailResponse.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/mapper/VenueApiConverter.java`
- Create: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/read/venue/VenueLatestRating.java`
- Test: `patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/VenueReadAdapterIT.java`

### Design

Add a `VenueLatestRating` nested record to `VenueDetailReadModel`:

```java
/// 期刊最新评级摘要（JCR/CAS/Scopus 各取最新年份数据）。
public record VenueLatestRating(
    // JCR
    Short jcrYear,
    BigDecimal impactFactor,
    String jifQuartile,
    String jifRank,
    BigDecimal jifPercentile,
    String wosOverallQuartile,
    String collection,
    BigDecimal selfCitationRate,
    String researchDirection,
    BigDecimal jciValue,
    String jciQuartile,
    // CAS
    Short casYear,
    String casEdition,
    String majorCategory,
    String majorQuartile,
    String minorSubject,
    String minorQuartile,
    Boolean isTopJournal,
    Boolean isReviewJournal,
    // Scopus
    Short scopusYear,
    BigDecimal citeScore,
    BigDecimal sjr,
    BigDecimal snip,
    String citeScoreQuartile,
    BigDecimal citeScorePercentile,
    // Warning
    Boolean inWarningList,
    String warningLevel
) {}
```

- [ ] **Step 1: Create VenueLatestRating record in domain layer**

File: `domain/model/read/venue/VenueLatestRating.java`

- [ ] **Step 2: Add latestRating field to VenueDetailReadModel**

Add `VenueLatestRating latestRating` field (nullable) to the existing record.

- [ ] **Step 3: Update VenueReadAdapter.findVenueDetail**

After fetching the VenueEntity, also fetch latest JCR/CAS/Scopus/Warning records by venueId and pass them to the mapper.

- [ ] **Step 4: Update VenueReadModelMapper to map rating entities to VenueLatestRating**

Add method: `toLatestRating(JcrRatingEntity, CasRatingEntity, ScopusRatingEntity, CasWarningEntity) → VenueLatestRating`

- [ ] **Step 5: Update VenueDetailResponse with rating section**

Add nested `LatestRatingDto` record in the response.

- [ ] **Step 6: Update VenueApiConverter to map latestRating**

- [ ] **Step 7: Write integration test**

Test: fetch detail for a venue with JCR/CAS/Scopus ratings → verify latestRating fields populated correctly.

- [ ] **Step 8: Commit**

```bash
git commit -m "feat(catalog): include latest rating summary in venue detail response"
```

---

## Task 3: Venue Rating History Endpoint

**Goal:** Provide historical rating data for all years, enabling frontend trend charts (IF/CiteScore/SJR trends, quartile changes).

**Files:**
- Create: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/read/venue/VenueRatingHistoryReadModel.java`
- Modify: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/read/VenueReadPort.java`
- Create: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/query/dto/VenueRatingHistoryQuery.java`
- Modify: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/query/VenueQueryService.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadAdapter.java`
- Create: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/response/VenueRatingHistoryResponse.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/VenueController.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/mapper/VenueApiConverter.java`
- Test: `patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/VenueReadAdapterIT.java`

### Design

Return all historical rating records grouped by source. This is a non-paginated endpoint (rating history per venue is bounded: ~25 years max × 3 sources).

**Response structure:**

```json
{
  "jcr": [
    { "year": 2024, "impactFactor": 82.9, "jifQuartile": "Q1", "jifRank": "1/136", "jifPercentile": 99.63, "selfCitationRate": 2.1, "collection": "SCIE" },
    { "year": 2023, "impactFactor": 58.7, ... }
  ],
  "cas": [
    { "year": 2024, "edition": "升级版", "majorCategory": "医学", "majorQuartile": "1区", "minorSubject": "MEDICINE, GENERAL & INTERNAL", "minorQuartile": "1区", "isTopJournal": true }
  ],
  "scopus": [
    { "year": 2024, "citeScore": 53.2, "sjr": 14.89, "snip": 12.43, "quartile": "Q1", "percentile": 99.0 }
  ],
  "warnings": [
    { "publishedYear": 2025, "editionLabel": "2025版", "inWarningList": false, "warningLevel": null }
  ]
}
```

- [ ] **Step 1: Create VenueRatingHistoryReadModel**

```java
/// 期刊评级历史读模型。
///
/// 包含 JCR/CAS/Scopus/Warning 的完整历史记录，用于前端趋势图。
@Builder
public record VenueRatingHistoryReadModel(
    List<JcrRecord> jcr,
    List<CasRecord> cas,
    List<ScopusRecord> scopus,
    List<WarningRecord> warnings) {

  public VenueRatingHistoryReadModel {
    jcr = jcr != null ? List.copyOf(jcr) : List.of();
    cas = cas != null ? List.copyOf(cas) : List.of();
    scopus = scopus != null ? List.copyOf(scopus) : List.of();
    warnings = warnings != null ? List.copyOf(warnings) : List.of();
  }

  public record JcrRecord(short year, BigDecimal impactFactor, String jifQuartile,
      String jifRank, BigDecimal jifPercentile, BigDecimal selfCitationRate, String collection) {}

  public record CasRecord(short year, String edition, String majorCategory,
      String majorQuartile, String minorSubject, String minorQuartile,
      boolean isTopJournal, boolean isReviewJournal) {}

  public record ScopusRecord(short year, BigDecimal citeScore, BigDecimal sjr,
      BigDecimal snip, String quartile, BigDecimal percentile,
      Integer documentCount, Integer citationCount) {}

  public record WarningRecord(short publishedYear, String editionLabel,
      boolean inWarningList, String warningLevel) {}
}
```

- [ ] **Step 2: Add method to VenueReadPort**

```java
VenueRatingHistoryReadModel findVenueRatingHistory(Long venueId);
```

- [ ] **Step 3: Create VenueRatingHistoryQuery**

```java
public record VenueRatingHistoryQuery(Long id) {
  public static VenueRatingHistoryQuery of(Long id) {
    return new VenueRatingHistoryQuery(id);
  }
}
```

- [ ] **Step 4: Add getVenueRatingHistory to VenueQueryService**

Validates venue exists, delegates to readPort.

- [ ] **Step 5: Implement in VenueReadAdapter**

Fetch all records from JcrRatingDao, CasRatingDao, ScopusRatingDao, CasWarningDao by venueId. Map to read model using VenueReadModelMapper.

- [ ] **Step 6: Create VenueRatingHistoryResponse and nested DTOs**

Mirror the read model structure.

- [ ] **Step 7: Add controller endpoint**

```java
@GetMapping("/{id}/ratings")
public VenueRatingHistoryResponse getVenueRatingHistory(@PathVariable Long id) {
  var query = VenueRatingHistoryQuery.of(id);
  return venueApiConverter.toRatingHistoryResponse(
      venueQueryService.getVenueRatingHistory(query));
}
```

- [ ] **Step 8: Write integration test**

- [ ] **Step 9: Commit**

```bash
git commit -m "feat(catalog): add venue rating history endpoint for trend charts"
```

---

## Task 4: Venue Publication Stats Endpoint

**Goal:** Provide annual publication statistics (works count, citation count, OA works count) for trend charts.

**Files:**
- Create: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/read/venue/VenueStatsReadModel.java`
- Modify: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/read/VenueReadPort.java`
- Modify: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/query/VenueQueryService.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadAdapter.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/dao/VenuePublicationStatsDao.java`
- Create: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/response/VenueStatsResponse.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/VenueController.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/mapper/VenueApiConverter.java`
- Test: `patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/VenueReadAdapterIT.java`

### Design

Non-paginated endpoint (stats per venue bounded: ~50 years max).

**Response:**

```json
{
  "stats": [
    { "year": 2024, "worksCount": 423, "citedByCount": 18500, "oaWorksCount": 312 },
    { "year": 2023, "worksCount": 398, "citedByCount": 22100, "oaWorksCount": 280 }
  ]
}
```

- [ ] **Step 1: Create VenueStatsReadModel**

```java
public record VenueStatsReadModel(List<YearStats> stats) {
  public VenueStatsReadModel {
    stats = stats != null ? List.copyOf(stats) : List.of();
  }
  public record YearStats(short year, int worksCount, int citedByCount, Integer oaWorksCount) {}
}
```

- [ ] **Step 2: Add findVenueStats to VenueReadPort**

```java
VenueStatsReadModel findVenueStats(Long venueId);
```

- [ ] **Step 3: Add getVenueStats to VenueQueryService**

- [ ] **Step 4: Implement in VenueReadAdapter**

Use existing `VenuePublicationStatsDao.findByVenueId()` → map entities to read model, sort by year DESC.

- [ ] **Step 5: Create response DTO + controller endpoint**

```java
@GetMapping("/{id}/stats")
public VenueStatsResponse getVenueStats(@PathVariable Long id) { ... }
```

- [ ] **Step 6: Write integration test**

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(catalog): add venue publication stats endpoint for trend charts"
```

---

## Task 5: Venue Instances Endpoint

**Goal:** List VenueInstance (volumes/issues) for a venue, supporting year filter and pagination.

**Files:**
- Create: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/read/venue/VenueInstanceSummaryReadModel.java`
- Modify: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/read/VenueReadPort.java`
- Create: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/query/dto/VenueInstanceListQuery.java`
- Modify: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/query/VenueQueryService.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/dao/VenueInstanceDao.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadAdapter.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadModelMapper.java`
- Create: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/request/VenueInstanceListRequest.java`
- Create: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/response/VenueInstanceItemResponse.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/VenueController.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/mapper/VenueApiConverter.java`
- Test: `patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/VenueReadAdapterIT.java`

### Design

Paginated list with optional year filter. Sorted by publicationYear DESC, volume DESC, issue DESC.

**Response item:**

```json
{
  "id": 123456,
  "volume": "30",
  "issue": "3",
  "publicationYear": 2024,
  "publicationMonth": 3,
  "publicationDay": 15,
  "publicationCount": 42
}
```

`publicationCount` is derived from `COUNT(publications) WHERE venue_instance_id = ?`. This can be computed via a subquery in the DAO or as a separate batch count query.

- [ ] **Step 1: Create VenueInstanceSummaryReadModel**

```java
public record VenueInstanceSummaryReadModel(
    Long id,
    String volume,
    String issue,
    Integer publicationYear,
    Integer publicationMonth,
    Integer publicationDay,
    long publicationCount) {

  public VenueInstanceSummaryReadModel {
    Assert.notNull(id, "Instance ID 不能为空");
  }
}
```

- [ ] **Step 2: Create VenueInstanceListQuery**

```java
@Builder
public record VenueInstanceListQuery(
    Long venueId,
    Integer year,
    Integer page,
    Integer pageSize) {}
```

- [ ] **Step 3: Add findVenueInstances to VenueReadPort**

```java
PageResult<VenueInstanceSummaryReadModel> findVenueInstances(
    Long venueId, PagingParams paging, Integer year);
```

- [ ] **Step 4: Add page query to VenueInstanceDao**

```java
@Query(value = """
    SELECT vi.*, (SELECT COUNT(*) FROM cat_publication p WHERE p.venue_instance_id = vi.id) AS pub_count
    FROM cat_venue_instance vi
    WHERE vi.venue_id = :venueId
      AND (:year IS NULL OR vi.publication_year = :year)
    ORDER BY vi.publication_year DESC, vi.volume DESC, vi.issue DESC
    """, nativeQuery = true)
Page<Object[]> findByVenueIdPage(
    @Param("venueId") Long venueId,
    @Param("year") Integer year,
    Pageable pageable);
```

- [ ] **Step 5: Implement in VenueReadAdapter**

Map Object[] results to VenueInstanceSummaryReadModel.

- [ ] **Step 6: Add listVenueInstances to VenueQueryService**

- [ ] **Step 7: Create request/response DTOs and controller endpoint**

```java
@GetMapping("/{id}/instances")
public PageResult<VenueInstanceItemResponse> listVenueInstances(
    @PathVariable Long id, VenueInstanceListRequest request) { ... }
```

- [ ] **Step 8: Write integration test**

- [ ] **Step 9: Commit**

```bash
git commit -m "feat(catalog): add venue instances list endpoint with year filter"
```

---

## Task 6: Instance Publications Endpoint

**Goal:** List publications within a specific VenueInstance, with pagination and sort options.

**Files:**
- Modify: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/publication/query/PublicationQueryService.java`
- Modify: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/publication/query/dto/PublicationListQuery.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/dao/PublicationDao.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/PublicationReadAdapter.java`
- Create: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/request/InstancePublicationListRequest.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/VenueController.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/mapper/VenueApiConverter.java`
- Test: `patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/PublicationReadAdapterIT.java`

### Design

Reuse existing `PublicationSummaryReadModel` and `PublicationItemResponse`. The change is adding `venueInstanceId` as a filter parameter throughout the query chain.

Sort options: default (page order from source), citedByCount DESC.

- [ ] **Step 1: Add venueInstanceId to PublicationListQuery**

```java
// Add field:
Long venueInstanceId
```

- [ ] **Step 2: Add venueInstanceId filter to PublicationDao.findPublicationPage**

Add parameter to the native query:
```sql
AND (:venueInstanceId IS NULL OR p.venue_instance_id = :venueInstanceId)
```

- [ ] **Step 3: Update PublicationReadAdapter to pass venueInstanceId filter**

- [ ] **Step 4: Add controller endpoint on VenueController**

```java
@GetMapping("/{venueId}/instances/{instanceId}/publications")
public PageResult<PublicationItemResponse> listInstancePublications(
    @PathVariable Long venueId,
    @PathVariable Long instanceId,
    InstancePublicationListRequest request) {
  // Delegate to PublicationQueryService with venueInstanceId filter
  ...
}
```

Note: This endpoint lives on VenueController but delegates to PublicationQueryService. The VenueController needs to inject PublicationQueryService and PublicationApiConverter (or reuse a shared one).

- [ ] **Step 5: Create InstancePublicationListRequest**

```java
public record InstancePublicationListRequest(
    Integer page,
    Integer pageSize,
    String sortBy) {}  // default / citedByCount
```

- [ ] **Step 6: Write integration test**

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(catalog): add instance publications endpoint with venueInstanceId filter"
```

---

## Task 7: Venue Compare Endpoint

**Goal:** Batch fetch venue details with latest ratings for side-by-side comparison (2-5 venues).

**Files:**
- Create: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/query/dto/VenueCompareQuery.java`
- Modify: `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/venue/query/VenueQueryService.java`
- Modify: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/read/VenueReadPort.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadAdapter.java`
- Create: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/response/VenueCompareItemResponse.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/VenueController.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/mapper/VenueApiConverter.java`
- Test: `patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/VenueReadAdapterIT.java`

### Design

**Request:** `GET /venues/compare?ids=1,2,3,4,5` (max 5 IDs)

**Response:** List of venue compare items, each containing basic info + latest ratings + recent stats.

```java
public record VenueCompareItemResponse(
    Long id,
    String title,
    String countryCode,
    String imageObjectKey,
    // Latest ratings (same structure as detail header)
    LatestRatingDto latestRating,
    // Publisher info
    String publisher,
    // OA
    Boolean isOa,
    String oaType,
    Integer apcUsd
) {}
```

- [ ] **Step 1: Create VenueCompareQuery**

```java
public record VenueCompareQuery(List<Long> ids) {
  public VenueCompareQuery {
    Assert.notEmpty(ids, "对比期刊 ID 列表不能为空");
    Assert.isTrue(ids.size() <= 5, "最多对比 5 本期刊");
    ids = List.copyOf(ids);
  }
  public static VenueCompareQuery of(List<Long> ids) {
    return new VenueCompareQuery(ids);
  }
}
```

- [ ] **Step 2: Add findVenuesForCompare to VenueReadPort**

```java
List<VenueDetailReadModel> findVenuesForCompare(List<Long> ids);
```

- [ ] **Step 3: Implement in VenueReadAdapter**

Use `venueDao.findByIdIn(ids)` → batch load latest ratings for all ids → assemble detail read models with ratings.

- [ ] **Step 4: Add compareVenues to VenueQueryService**

Validate ids size, delegate to readPort, throw if any id not found (or just return found ones).

- [ ] **Step 5: Create response DTO + controller endpoint**

```java
@GetMapping("/compare")
public List<VenueCompareItemResponse> compareVenues(@RequestParam List<Long> ids) {
  var query = VenueCompareQuery.of(ids);
  return venueQueryService.compareVenues(query).stream()
      .map(venueApiConverter::toCompareItemResponse)
      .toList();
}
```

- [ ] **Step 6: Write integration test**

Test: compare 3 venues with different rating data → verify all returned with correct ratings.

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(catalog): add venue compare endpoint for side-by-side comparison"
```

---

## Task 8: Venue Detail - Related Data (MeSH, Relations, Indexing History)

**Goal:** Expose MeSH headings, venue relations, and indexing history in the detail response for Tab 1 (Overview) and Tab 3 (Indexing).

**Files:**
- Modify: `patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/read/venue/VenueDetailReadModel.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadAdapter.java`
- Modify: `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadModelMapper.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/response/VenueDetailResponse.java`
- Modify: `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/mapper/VenueApiConverter.java`
- Test: `patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/VenueReadAdapterIT.java`

### Design

Add to VenueDetailReadModel:

```java
List<MeshHeading> meshHeadings,
List<VenueRelationItem> relations,
List<IndexingHistoryItem> indexingHistory
```

With inner records:

```java
public record MeshHeading(String descriptorName, String descriptorUi,
    boolean isMajorTopic, String qualifierName, String qualifierUi) {}

public record VenueRelationItem(Long relatedVenueId, String relatedTitle,
    String relationType, LocalDate effectiveDate) {}

public record IndexingHistoryItem(String indexingSource, boolean currentlyIndexed,
    String indexingTreatment, Integer startYear, Integer endYear) {}
```

- [ ] **Step 1: Add nested record types and fields to VenueDetailReadModel**

- [ ] **Step 2: Update VenueReadAdapter.findVenueDetail**

After fetching VenueEntity, also query:
- `VenueMeshDao.findByVenueId(id)`
- `VenueRelationDao.findByVenueId(id)`
- `VenueIndexingHistoryDao.findByVenueId(id)`

Pass all to mapper.

- [ ] **Step 3: Update VenueReadModelMapper**

Add `@AfterMapping` or multi-source mapping to populate mesh/relations/indexing.

- [ ] **Step 4: Update VenueDetailResponse with corresponding DTOs**

- [ ] **Step 5: Update VenueApiConverter**

- [ ] **Step 6: Write integration test**

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(catalog): include MeSH, relations, and indexing history in venue detail"
```

---

## Execution Order & Dependencies

```
Task 1 (Enhanced List) ─── independent, can start first
Task 2 (Detail + Ratings) ─── independent
Task 3 (Rating History) ─── depends on Task 2 (shares VenueLatestRating pattern)
Task 4 (Stats) ─── independent
Task 5 (Instances) ─── independent
Task 6 (Instance Pubs) ─── depends on Task 5 (needs instance data)
Task 7 (Compare) ─── depends on Task 2 (reuses detail read model)
Task 8 (MeSH/Relations) ─── depends on Task 2 (extends detail model)
```

**Recommended execution order:** 2 → 1 → 8 → 3 → 4 → 5 → 6 → 7

Start with Task 2 because it establishes `VenueLatestRating` which Tasks 3, 7, 8 build upon.
