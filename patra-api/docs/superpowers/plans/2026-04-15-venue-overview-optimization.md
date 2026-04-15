# 期刊概览页定向优化 · 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 spec `2026-04-15-venue-overview-optimization-design.md` 落地期刊详情页概览 Tab 的定向优化——Header 瘦身、3 屏骨架、MeSH 分组展示（前端假数据）、最新一期卡、Top 5 高被引（新端点）、外链行。

**Architecture:**
- 后端 1 个新端点（`GET /catalog/venues/{id}/top-publications`）—— 普通 `ORDER BY` + `LIMIT`，不涉聚合。
- 前端重写 `VenueTabOverview.vue`，新增 4 个子组件 + 1 个 composable + 1 个 mock 工具。
- 双仓库：patra-api（后端）+ patra-web（前端），通过现有 `/patra-catalog/*` 网关路径互通。

**Tech Stack:**
- 后端：Java 25 · Spring Boot 4.0.1 · Spring Data JPA · MapStruct · JUnit 5 · Spring Batch
- 前端：Nuxt 4.3.1 · Vue 3 Composition API · TypeScript strict · @nuxt/ui 4.4.0 · Tailwind CSS 4 · chart.js + vue-chartjs · @nuxtjs/i18n
- 规范：`.claude/rules/code-style.md`、`patra-web/DESIGN.md`、`patra-web/GEMINI.md`

**执行目录：**
- 后端 Tasks（1–3）：在 `/Users/linqibin/Desktop/Patra/patra-api` 执行
- 前端 Tasks（4–12）：在 `/Users/linqibin/Desktop/Patra/patra-web` 执行

---

## Task 1：后端 · DAO 新增 findTopByVenue 方法（TDD）

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/dao/PublicationDao.java`
- Test: `patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/persistence/dao/PublicationDaoTopByVenueIT.java` (new)

**依赖：** 无

- [ ] **Step 1：写失败集成测试**

路径：`patra-catalog-infra/src/test/java/com/patra/catalog/infra/persistence/dao/PublicationDaoTopByVenueIT.java`

```java
package com.patra.catalog.infra.persistence.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.entity.PublicationEntity;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// DAO 级集成测试，验证 `findTopByVenue` 按 citationCount 降序返回 Top N 文献。
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("PublicationDao#findTopByVenue")
class PublicationDaoTopByVenueIT {

  @Autowired private PublicationDao publicationDao;
  @Autowired private JdbcTemplate jdbc;

  private Long venueId;

  @BeforeEach
  void setUp() {
    // 插入 venue 与 10 篇 publication，citationCount 从 10→1 递减
    venueId = 1001L;
    jdbc.update(
        "INSERT INTO cat_venue (id, venue_type, title, version, created_at, updated_at) "
            + "VALUES (?, 'JOURNAL', 'Test Journal', 0, NOW(), NOW())",
        venueId);
    for (int i = 0; i < 10; i++) {
      jdbc.update(
          "INSERT INTO cat_publication (id, venue_id, title, citation_count, publication_year,"
              + " version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 0, NOW(), NOW())",
          2000L + i,
          venueId,
          "Paper " + i,
          10 - i,
          2020 + (i % 5));
    }
  }

  @Test
  @DisplayName("无 since 参数时按 citation_count 降序返回 top 5")
  void findTopByVenue_withoutSince_returnsTop5ByCitationDesc() {
    List<PublicationEntity> result = publicationDao.findTopByVenue(venueId, null, 5);

    assertThat(result).hasSize(5);
    assertThat(result.get(0).getCitationCount()).isEqualTo(10);
    assertThat(result.get(4).getCitationCount()).isEqualTo(6);
  }

  @Test
  @DisplayName("传 since 过滤 publicationYear")
  void findTopByVenue_withSince_filtersByYear() {
    List<PublicationEntity> result = publicationDao.findTopByVenue(venueId, 2023, 10);

    assertThat(result).allMatch(p -> p.getPublicationYear() >= 2023);
  }

  @Test
  @DisplayName("limit 控制返回条数")
  void findTopByVenue_withLimit_respectsLimit() {
    List<PublicationEntity> result = publicationDao.findTopByVenue(venueId, null, 3);

    assertThat(result).hasSize(3);
  }
}
```

- [ ] **Step 2：运行测试确认失败**

```bash
cd /Users/linqibin/Desktop/Patra/patra-api
./gradlew :patra-catalog:patra-catalog-infra:test \
  --tests "com.patra.catalog.infra.persistence.dao.PublicationDaoTopByVenueIT"
```

期望：编译失败，`publicationDao.findTopByVenue` 方法不存在。

- [ ] **Step 3：在 `PublicationDao.java` 增加方法**

在现有 `PublicationDao` 接口尾部追加：

```java
  /// 按 venue 查询 Top N 高被引文献，按 citation_count 降序、publication_year 降序排序。
  ///
  /// @param venueId 期刊 ID
  /// @param since   发表年下限（可为 null，不过滤）
  /// @param limit   返回条数上限
  /// @return 文献实体列表（已按 citation_count DESC, publication_year DESC 排序）
  @Query(
      value =
          """
          SELECT * FROM cat_publication
          WHERE venue_id = :venueId
            AND (:since IS NULL OR publication_year >= :since)
            AND deleted_at IS NULL
          ORDER BY citation_count DESC NULLS LAST, publication_year DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<PublicationEntity> findTopByVenue(
      @Param("venueId") Long venueId,
      @Param("since") Integer since,
      @Param("limit") int limit);
```

并确保已有 import：`java.util.List`、`org.springframework.data.jpa.repository.Query`、`org.springframework.data.repository.query.Param`。

> 注：MySQL 8 不支持 `NULLS LAST` —— 如现有数据库为 MySQL，改写为 `ORDER BY citation_count IS NULL, citation_count DESC, publication_year DESC`。实施时先确认数据库版本（当前项目声明 MySQL 8.x，所以使用后者）。

最终版本：

```java
      value =
          """
          SELECT * FROM cat_publication
          WHERE venue_id = :venueId
            AND (:since IS NULL OR publication_year >= :since)
            AND deleted_at IS NULL
          ORDER BY citation_count IS NULL, citation_count DESC, publication_year DESC
          LIMIT :limit
          """,
```

- [ ] **Step 4：运行测试确认通过**

```bash
./gradlew :patra-catalog:patra-catalog-infra:test \
  --tests "com.patra.catalog.infra.persistence.dao.PublicationDaoTopByVenueIT"
```

期望：3 个测试全绿。

- [ ] **Step 5：确认索引（不改 schema，只输出诊断）**

```bash
./gradlew :patra-catalog:patra-catalog-infra:test \
  --tests "com.patra.catalog.infra.persistence.dao.PublicationDaoTopByVenueIT" \
  -PshowSql=true 2>&1 | grep -A 2 "ORDER BY citation_count"
```

若执行计划提示 full table scan，**不在本 Task 加索引**，作为 follow-up issue 记录（见 Task 12 校验清单）。

- [ ] **Step 6：提交**

```bash
git add \
  patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/dao/PublicationDao.java \
  patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/persistence/dao/PublicationDaoTopByVenueIT.java

git commit -m "$(cat <<'EOF'
feat(catalog): PublicationDao 新增 findTopByVenue 查询

支持按 venue 查询 Top N 高被引文献，为刊级"高被引"端点提供数据访问能力。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2：后端 · PublicationReadPort 增加 findTopByVenue + Adapter 实现（TDD）

**Files:**
- Modify: `patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/read/PublicationReadPort.java`
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/PublicationReadAdapter.java`
- Test: `patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/PublicationReadAdapterTopByVenueIT.java` (new)

**依赖：** Task 1

- [ ] **Step 1：写失败集成测试**

路径：`patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/PublicationReadAdapterTopByVenueIT.java`

```java
package com.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// 验证 PublicationReadAdapter#findTopByVenue 返回 PublicationSummaryReadModel 列表。
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PublicationReadAdapter.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("PublicationReadAdapter#findTopByVenue")
class PublicationReadAdapterTopByVenueIT {

  @Autowired private PublicationReadAdapter readAdapter;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc.update(
        "INSERT INTO cat_venue (id, venue_type, title, version, created_at, updated_at)"
            + " VALUES (2001, 'JOURNAL', 'Top Venue', 0, NOW(), NOW())");
    jdbc.update(
        "INSERT INTO cat_publication (id, venue_id, title, citation_count, publication_year,"
            + " version, created_at, updated_at) VALUES (3001, 2001, 'A', 500, 2023, 0, NOW(),"
            + " NOW())");
    jdbc.update(
        "INSERT INTO cat_publication (id, venue_id, title, citation_count, publication_year,"
            + " version, created_at, updated_at) VALUES (3002, 2001, 'B', 300, 2022, 0, NOW(),"
            + " NOW())");
  }

  @Test
  @DisplayName("返回 ReadModel 列表，字段映射正确")
  void findTopByVenue_returnsReadModelList() {
    List<PublicationSummaryReadModel> result = readAdapter.findTopByVenue(2001L, null, 5);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo(3001L);
    assertThat(result.get(0).title()).isEqualTo("A");
    assertThat(result.get(0).citationCount()).isEqualTo(500);
    assertThat(result.get(0).publicationYear()).isEqualTo(2023);
  }
}
```

- [ ] **Step 2：运行测试确认失败**

```bash
./gradlew :patra-catalog:patra-catalog-infra:test \
  --tests "com.patra.catalog.infra.adapter.read.PublicationReadAdapterTopByVenueIT"
```

期望：编译失败，`readAdapter.findTopByVenue` 不存在。

- [ ] **Step 3：在 Domain Port 新增方法**

Edit `patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/read/PublicationReadPort.java`：

在接口尾部（`findPublicationDetail` 下方）追加：

```java
  /// 查询指定期刊的 Top N 高被引文献。
  ///
  /// 按 citation_count 降序、publication_year 降序排序。
  ///
  /// @param venueId 期刊 ID
  /// @param since   发表年下限（可为 null，不过滤）
  /// @param limit   返回条数（建议 1-20）
  /// @return Top N 文献摘要 ReadModel 列表
  java.util.List<PublicationSummaryReadModel> findTopByVenue(
      Long venueId, Integer since, int limit);
```

替换已有 `import java.util.Optional;` 为同级 imports（保持 alphabetical）：

```java
import java.util.List;
import java.util.Optional;
```

并把内部返回类型写法改为 `List<PublicationSummaryReadModel>`。

- [ ] **Step 4：在 ReadAdapter 实现**

Edit `patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/PublicationReadAdapter.java`：

说明：`PublicationSummaryReadModel` 的构造逻辑在现有 **private** 方法 `toSummaryReadModel(PublicationEntity, Map<Long, String>)` 中，通过 `venueNameMap` 注入 `venueName`。本 Adapter 已持有 `venueDao` 字段，我们单 venue 查询一次 `findById` 即可复用该私有方法：

在类尾部追加：

```java
  @Override
  public List<PublicationSummaryReadModel> findTopByVenue(
      Long venueId, Integer since, int limit) {
    Objects.requireNonNull(venueId, "venueId must not be null");
    if (limit <= 0) return List.of();

    List<PublicationEntity> entities = publicationDao.findTopByVenue(venueId, since, limit);
    if (entities.isEmpty()) return List.of();

    // 单 venue 查询，venueName 一次取出，复用现有私有映射方法
    Map<Long, String> venueNameMap =
        venueDao
            .findById(venueId)
            .map(v -> Map.of(venueId, v.getTitle()))
            .orElseGet(Map::of);

    return entities.stream().map(e -> toSummaryReadModel(e, venueNameMap)).toList();
  }
```

确保 imports（若不存在则添加）：

```java
import java.util.List;
import java.util.Map;
import java.util.Objects;
```

**不要**重命名或修改现有 private `toSummaryReadModel` 方法签名——该方法已被 `findPublicationPage` 使用。

- [ ] **Step 5：运行测试确认通过**

```bash
./gradlew :patra-catalog:patra-catalog-infra:test \
  --tests "com.patra.catalog.infra.adapter.read.PublicationReadAdapterTopByVenueIT"
```

期望：通过。

- [ ] **Step 6：提交**

```bash
git add \
  patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/read/PublicationReadPort.java \
  patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/PublicationReadAdapter.java \
  patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/read/PublicationReadAdapterTopByVenueIT.java

git commit -m "$(cat <<'EOF'
feat(catalog): PublicationReadPort 新增 findTopByVenue

Domain Port 增加刊级 Top N 高被引查询能力，Infra 层 PublicationReadAdapter 实现映射。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3：后端 · VenueController 端点 + Query + Request/Response + ApiConverter + 集成测试

**Files:**
- Modify: `patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/VenueController.java`
- Create: `patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/request/TopPublicationsRequest.java`
- Create: `patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/response/TopPublicationItemResponse.java`
- Modify: `patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/publication/mapper/PublicationApiConverter.java`
- Create: `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/publication/query/dto/TopPublicationsQuery.java`
- Modify: `patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/publication/query/PublicationQueryService.java`
- Test: `patra-catalog/patra-catalog-adapter/src/test/java/com/patra/catalog/adapter/rest/venue/VenueControllerTopPublicationsTest.java` (new)

**依赖：** Task 2

**架构遵循：** `rules/layers/application.md` QueryService 规范（接收 `{Xxx}Query` record）、`rules/layers/adapter.md`（Converter 按**数据语义**归属 → `PublicationApiConverter`，不按路径作用域）。

- [ ] **Step 1：新建 `TopPublicationsQuery` Query DTO**

Create `patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/publication/query/dto/TopPublicationsQuery.java`：

```java
package com.patra.catalog.app.usecase.publication.query.dto;

/// 刊级 Top N 高被引查询参数。
///
/// 负责对 `limit` 进行**默认值与范围归一化**（null→5，钳位到 [1,20]），
/// QueryService 无需再次校验入参边界。
///
/// @param venueId 期刊 ID（必填）
/// @param limit   返回条数，已归一化到 [1,20]
/// @param since   发表年下限（可为 null，不过滤）
public record TopPublicationsQuery(Long venueId, int limit, Integer since) {

  /// 静态工厂，承担归一化职责。
  public static TopPublicationsQuery of(Long venueId, Integer rawLimit, Integer since) {
    int normalized = Math.max(1, Math.min(rawLimit == null ? 5 : rawLimit, 20));
    return new TopPublicationsQuery(venueId, normalized, since);
  }
}
```

- [ ] **Step 2：在 PublicationQueryService 新增方法**

在 `PublicationQueryService` 类尾部追加：

```java
  /// 查询期刊的 Top N 高被引文献。
  ///
  /// @param query 查询参数
  /// @return Top N 文献摘要列表
  public java.util.List<PublicationSummaryReadModel> listTopPublicationsByVenue(
      TopPublicationsQuery query) {
    Objects.requireNonNull(query, "query must not be null");
    return publicationReadPort.findTopByVenue(query.venueId(), query.since(), query.limit());
  }
```

imports：
```java
import com.patra.catalog.app.usecase.publication.query.dto.TopPublicationsQuery;
```

- [ ] **Step 3：新增 Request DTO**

Create `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/request/TopPublicationsRequest.java`：

```java
package com.patra.catalog.adapter.rest.venue.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/// 刊级 Top N 高被引查询请求。
///
/// 仅承载校验（`@Min`/`@Max`）；默认值与钳位由 `TopPublicationsQuery.of` 承担。
public record TopPublicationsRequest(
    @Min(1) @Max(20) Integer limit, Integer since) {}
```

- [ ] **Step 4：新增 Response DTO**

Create `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/response/TopPublicationItemResponse.java`：

```java
package com.patra.catalog.adapter.rest.venue.response;

/// 刊级 Top N 高被引响应项。
public record TopPublicationItemResponse(
    Long id,
    String title,
    Integer publicationYear,
    Integer citationCount,
    String doi) {}
```

- [ ] **Step 5：PublicationApiConverter 新增映射方法**

Edit `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/publication/mapper/PublicationApiConverter.java`（MapStruct 接口），新增：

```java
  /// 刊级 Top N 高被引读模型 → 响应 DTO。
  com.patra.catalog.adapter.rest.venue.response.TopPublicationItemResponse toTopItemResponse(
      com.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel readModel);
```

MapStruct 会按字段名自动映射 `id/title/publicationYear/citationCount/doi`（ReadModel 其他字段如 `venueName/venueId/lastSyncedAt` 在 Response 中不存在，被忽略）。

若 mapper 配置了 `unmappedSourcePolicy = ReportingPolicy.ERROR` 导致编译失败，在该方法上加：
```java
  @org.mapstruct.BeanMapping(ignoreUnmappedSourceProperties = {
      "pmid", "doi"  // 注意：doi 保留，其他未在 Response 中的字段按需列出
  })
```
——先跑一遍，确认错误后再按 compiler 提示列出需忽略的字段。

- [ ] **Step 6：VenueController 新增端点**

在 `VenueController` 类中确认已有依赖字段 `private final PublicationQueryService publicationQueryService;`（由 `@RequiredArgsConstructor` 构造）；若没有则加上。同时确认已注入 `private final PublicationApiConverter publicationApiConverter;`（用于 Response 映射）。

新增端点（放在 `/{id}/instances` 下方）：

```java
  /// 查询期刊的 Top N 高被引文献。
  ///
  /// @param id      期刊主键 ID
  /// @param request `limit`（1-20，缺省 5）、`since`（发表年下限，可选）
  /// @return Top N 文献响应列表
  @GetMapping("/{id}/top-publications")
  public java.util.List<TopPublicationItemResponse> listTopPublicationsByVenue(
      @PathVariable Long id,
      @Valid TopPublicationsRequest request) {
    TopPublicationsQuery query = TopPublicationsQuery.of(id, request.limit(), request.since());
    return publicationQueryService.listTopPublicationsByVenue(query).stream()
        .map(publicationApiConverter::toTopItemResponse)
        .toList();
  }
```

imports：
```java
import com.patra.catalog.adapter.rest.venue.request.TopPublicationsRequest;
import com.patra.catalog.adapter.rest.venue.response.TopPublicationItemResponse;
import com.patra.catalog.app.usecase.publication.query.PublicationQueryService;
import com.patra.catalog.app.usecase.publication.query.dto.TopPublicationsQuery;
import com.patra.catalog.adapter.rest.publication.mapper.PublicationApiConverter;
import jakarta.validation.Valid;
```

- [ ] **Step 7：写 WebMvc 切片测试**

Create `patra-catalog-adapter/src/test/java/com/patra/catalog/adapter/rest/venue/VenueControllerTopPublicationsTest.java`：

```java
package com.patra.catalog.adapter.rest.venue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.patra.catalog.adapter.rest.publication.mapper.PublicationApiConverter;
import com.patra.catalog.adapter.rest.venue.mapper.VenueApiConverter;
import com.patra.catalog.adapter.rest.venue.response.TopPublicationItemResponse;
import com.patra.catalog.app.usecase.publication.query.PublicationQueryService;
import com.patra.catalog.app.usecase.publication.query.dto.TopPublicationsQuery;
import com.patra.catalog.app.usecase.venue.query.VenueQueryService;
import com.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VenueController.class)
@DisplayName("VenueController#listTopPublicationsByVenue")
class VenueControllerTopPublicationsTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private VenueQueryService venueQueryService;
  @MockitoBean private PublicationQueryService publicationQueryService;
  @MockitoBean private VenueApiConverter venueApiConverter;
  @MockitoBean private PublicationApiConverter publicationApiConverter;

  @Test
  @DisplayName("默认 limit=5，返回 JSON 数组；Query 归一化 limit=5/since=null")
  void listTopPublications_returnsJsonArray() throws Exception {
    var model =
        new PublicationSummaryReadModel(
            1L, "Paper A", "111", "10.1/A", 2023, "en", false, null, 2001L, "V", 500, Instant.now());
    when(publicationQueryService.listTopPublicationsByVenue(any(TopPublicationsQuery.class)))
        .thenReturn(List.of(model));
    when(publicationApiConverter.toTopItemResponse(any()))
        .thenReturn(new TopPublicationItemResponse(1L, "Paper A", 2023, 500, "10.1/A"));

    mockMvc
        .perform(get("/venues/2001/top-publications"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].title").value("Paper A"))
        .andExpect(jsonPath("$[0].citationCount").value(500));

    // 验证 Query 归一化：limit 缺省→5，since→null
    ArgumentCaptor<TopPublicationsQuery> captor = ArgumentCaptor.forClass(TopPublicationsQuery.class);
    org.mockito.Mockito.verify(publicationQueryService).listTopPublicationsByVenue(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().limit()).isEqualTo(5);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().venueId()).isEqualTo(2001L);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().since()).isNull();
  }

  @Test
  @DisplayName("limit=21 校验失败返回 400")
  void listTopPublications_whenLimitOverMax_returns400() throws Exception {
    mockMvc
        .perform(get("/venues/2001/top-publications").param("limit", "21"))
        .andExpect(status().isBadRequest());
  }
}
```

- [ ] **Step 8：运行测试**

```bash
./gradlew :patra-catalog:patra-catalog-adapter:test \
  --tests "com.patra.catalog.adapter.rest.venue.VenueControllerTopPublicationsTest"
```

期望：2 个测试通过。

- [ ] **Step 9：端到端手工验证**

```bash
curl -s "http://localhost:8080/patra-catalog/venues/<existing-id>/top-publications?limit=3" | jq .
```

期望：返回最多 3 条 publication，按 citationCount 降序。

- [ ] **Step 10：提交**

```bash
git add \
  patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/publication/query/dto/TopPublicationsQuery.java \
  patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/publication/query/PublicationQueryService.java \
  patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/VenueController.java \
  patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/publication/mapper/PublicationApiConverter.java \
  patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/request/TopPublicationsRequest.java \
  patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/response/TopPublicationItemResponse.java \
  patra-catalog/patra-catalog-adapter/src/test/java/com/patra/catalog/adapter/rest/venue/VenueControllerTopPublicationsTest.java

git commit -m "$(cat <<'EOF'
feat(catalog): 新增 GET /venues/{id}/top-publications 端点

支持按期刊查询 Top N 高被引文献，默认 limit=5，范围 1-20，可选 since 按发表年过滤。
QueryService 接收 TopPublicationsQuery record（承担 limit 归一化），响应映射由 PublicationApiConverter 负责。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4：前端 · MeSH mock 工具 + useVenueTopPublications composable

**Files:**
- Create: `patra-web/app/utils/mesh-mock.ts`
- Create: `patra-web/app/composables/useVenueTopPublications.ts`
- Modify: `patra-web/app/types/venue.ts`

**依赖：** Task 3（composable 对应后端端点）

- [ ] **Step 1：扩展 types**

Edit `app/types/venue.ts`，在合适位置追加：

```typescript
export interface TopPublicationItem {
  id: number
  title: string
  publicationYear: number | null
  citationCount: number | null
  doi: string | null
}

export interface MeshMockItem {
  descriptorName: string
  qualifierName: string | null
  isMajorTopic: boolean
  count: number
  percentage: number
}
```

- [ ] **Step 2：实现 mesh-mock 工具**

Create `app/utils/mesh-mock.ts`：

```typescript
import type { VenueMeshHeading, MeshMockItem } from '~/types/venue'

/// 基于真实 MeSH descriptor 列表生成"前端假数据"。
/// majorTopic 先排到前面，count 按索引递减（500 * 0.8^i），percentage 按 count 占比计算。
/// 后端 ES 上线后，该工具可被真实接口数据替代，组件消费的数据形状不变。
export function buildMockMeshDistribution(
  headings: VenueMeshHeading[]
): MeshMockItem[] {
  if (headings.length === 0) return []

  const sorted = [...headings].sort((a, b) => {
    const aMajor = a.isMajorTopic ? 1 : 0
    const bMajor = b.isMajorTopic ? 1 : 0
    return bMajor - aMajor
  })

  const counts = sorted.map((_, i) => Math.max(1, Math.round(500 * Math.pow(0.8, i))))
  const total = counts.reduce((a, b) => a + b, 0)

  return sorted.map((h, i) => ({
    descriptorName: h.descriptorName,
    qualifierName: h.qualifierName ?? null,
    isMajorTopic: h.isMajorTopic === true,
    count: counts[i]!,
    percentage: total > 0 ? counts[i]! / total : 0
  }))
}
```

- [ ] **Step 3：实现 composable**

Create `app/composables/useVenueTopPublications.ts`：

```typescript
import type { IdLike } from '~/composables/useApi'
import type { TopPublicationItem } from '~/types/venue'

interface Params {
  limit?: number
  since?: number
}

export function useVenueTopPublications(
  id: IdLike,
  params?: Ref<Params> | Params
) {
  return useApi<TopPublicationItem[]>(
    resolveUrl(i => `/patra-catalog/venues/${i}/top-publications`, id),
    { query: params }
  )
}
```

- [ ] **Step 4：运行 typecheck**

```bash
cd /Users/linqibin/Desktop/Patra/patra-web
pnpm typecheck
```

期望：无错误。

- [ ] **Step 5：提交**

```bash
git add \
  app/utils/mesh-mock.ts \
  app/composables/useVenueTopPublications.ts \
  app/types/venue.ts

git commit -m "$(cat <<'EOF'
feat(venue): 新增 MeSH mock 工具和 useVenueTopPublications composable

mesh-mock 按 majorTopic 优先 + 指数递减生成示例 count/percentage；
composable 对接后端新端点 /venues/{id}/top-publications。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5：前端 · i18n 新 key

**Files:**
- Modify: `patra-web/i18n/locales/zh-CN.json`
- Modify: `patra-web/i18n/locales/en.json`

**依赖：** 无

- [ ] **Step 1：读取现有 `venues.detail.overview` 节点**

```bash
cd /Users/linqibin/Desktop/Patra/patra-web
grep -A 30 '"overview"' i18n/locales/zh-CN.json | head -50
```

- [ ] **Step 2：在 `venues.detail.overview` 新增 key（zh-CN）**

在 `zh-CN.json` 的 `venues.detail.overview` 对象内追加：

```json
    "meshDistribution": "MeSH 主题分布",
    "meshDistributionNote": "示例数据 · ES 上线后替换为真实频率",
    "expandAll": "展开看全部 {count} 项",
    "collapseAll": "收起",
    "latestIssue": "最新一期",
    "browseCurrentIssue": "浏览当期文章",
    "topCited": "高被引文章",
    "citationCount": "{count} 次引用",
    "viewAllPublications": "查看全部文章",
    "externalLinks": "外部链接",
    "links": {
      "homepage": "官网",
      "doaj": "DOAJ",
      "crossref": "Crossref",
      "issnPortal": "ISSN Portal",
      "pubmed": "PubMed"
    }
```

- [ ] **Step 3：对应的 en.json key**

```json
    "meshDistribution": "MeSH Topic Distribution",
    "meshDistributionNote": "Sample data · will be replaced with real frequency after ES rollout",
    "expandAll": "Expand all {count} items",
    "collapseAll": "Collapse",
    "latestIssue": "Latest Issue",
    "browseCurrentIssue": "Browse current issue",
    "topCited": "Top Cited",
    "citationCount": "{count} citations",
    "viewAllPublications": "View all publications",
    "externalLinks": "External Links",
    "links": {
      "homepage": "Homepage",
      "doaj": "DOAJ",
      "crossref": "Crossref",
      "issnPortal": "ISSN Portal",
      "pubmed": "PubMed"
    }
```

- [ ] **Step 4：验证 JSON 合法 + 运行 typecheck**

```bash
node -e "JSON.parse(require('fs').readFileSync('i18n/locales/zh-CN.json'))"
node -e "JSON.parse(require('fs').readFileSync('i18n/locales/en.json'))"
pnpm typecheck
```

期望：无输出 + typecheck 通过。

- [ ] **Step 5：提交**

```bash
git add i18n/locales/zh-CN.json i18n/locales/en.json
git commit -m "$(cat <<'EOF'
feat(i18n): 新增期刊概览页优化所需 key

涵盖 MeSH 分布、最新一期、高被引、外链行等模块的中英双语翻译。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6：前端 · VenueMeshGroupedList 组件

**Files:**
- Create: `patra-web/app/components/venue/VenueMeshGroupedList.vue`

**依赖：** Task 4, Task 5

- [ ] **Step 1：创建组件**

```vue
<script setup lang="ts">
import type { VenueMeshHeading, MeshMockItem } from '~/types/venue'

interface Props {
  headings: VenueMeshHeading[]
}

const props = defineProps<Props>()
const { t } = useI18n()

const items = computed<MeshMockItem[]>(() => buildMockMeshDistribution(props.headings))
const expanded = ref(false)
const VISIBLE_WHEN_COLLAPSED = 5

const visibleItems = computed(() =>
  expanded.value ? items.value : items.value.slice(0, VISIBLE_WHEN_COLLAPSED)
)

const maxCount = computed(() => (items.value[0]?.count ?? 1))

const canExpand = computed(() => items.value.length > VISIBLE_WHEN_COLLAPSED)

function barWidthPct(count: number) {
  return Math.max(4, Math.round((count / maxCount.value) * 100))
}

function pctLabel(percentage: number) {
  return `${Math.round(percentage * 100)}%`
}
</script>

<template>
  <section v-if="items.length > 0" class="rounded-2xl surface-glass p-6">
    <header class="mb-4 flex items-start justify-between">
      <h2 class="text-[11px] font-semibold uppercase tracking-wider text-adaptive-muted">
        {{ t('venues.detail.overview.meshDistribution') }}
      </h2>
      <span class="text-[10px] uppercase tracking-wider text-amber-500/80 dark:text-amber-300/80">
        {{ t('venues.detail.overview.meshDistributionNote') }}
      </span>
    </header>

    <ul class="flex flex-col gap-2.5">
      <li
        v-for="item in visibleItems"
        :key="item.descriptorName"
        class="group"
        :title="item.qualifierName ? `${item.descriptorName} / ${item.qualifierName}` : item.descriptorName"
      >
        <div class="mb-1 flex items-baseline justify-between gap-3 text-sm">
          <span
            :class="[
              'truncate font-medium',
              item.isMajorTopic
                ? 'text-emerald-700 dark:text-emerald-300'
                : 'text-adaptive-body'
            ]"
          >
            <span v-if="item.isMajorTopic" aria-hidden="true">*</span>
            {{ item.descriptorName }}
          </span>
          <span class="shrink-0 font-mono text-xs text-adaptive-muted">{{ pctLabel(item.percentage) }}</span>
        </div>
        <div class="h-1.5 w-full overflow-hidden rounded-full bg-slate-200/70 dark:bg-white/5">
          <div
            :class="[
              'h-full rounded-full transition-all duration-500',
              item.isMajorTopic
                ? 'bg-gradient-to-r from-emerald-500 to-emerald-400'
                : 'bg-gradient-to-r from-violet-500 to-violet-400'
            ]"
            :style="{ width: barWidthPct(item.count) + '%' }"
          />
        </div>
      </li>
    </ul>

    <button
      v-if="canExpand"
      type="button"
      class="mt-4 inline-flex items-center gap-1 text-xs text-adaptive-muted transition-colors hover:text-violet-600 dark:hover:text-violet-400"
      @click="expanded = !expanded"
    >
      <UIcon :name="expanded ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'" class="size-3.5" />
      <span v-if="!expanded">{{ t('venues.detail.overview.expandAll', { count: items.length }) }}</span>
      <span v-else>{{ t('venues.detail.overview.collapseAll') }}</span>
    </button>
  </section>
</template>
```

- [ ] **Step 2：typecheck**

```bash
pnpm typecheck
```

- [ ] **Step 3：提交**

```bash
git add app/components/venue/VenueMeshGroupedList.vue
git commit -m "$(cat <<'EOF'
feat(venue): 新增 VenueMeshGroupedList 组件

MeSH 主题分布 Top-N 条形（前端假数据），majorTopic 用绿色高亮，折叠/展开支持。

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7：前端 · VenueLatestIssueCard 组件

**Files:**
- Create: `patra-web/app/components/venue/VenueLatestIssueCard.vue`

**依赖：** Task 5

- [ ] **Step 1：创建组件**

```vue
<script setup lang="ts">
import type { VenueInstanceItem } from '~/types/venue'

interface Props {
  venueId: string | number
  instance: VenueInstanceItem | null
  loading: boolean
}

const props = defineProps<Props>()
const { t } = useI18n()

const volumeIssueLabel = computed(() => {
  const i = props.instance
  if (!i) return ''
  const parts: string[] = []
  if (i.volume) parts.push(t('venues.detail.issues.volume', { volume: i.volume }))
  if (i.issue) parts.push(t('venues.detail.issues.issue', { issue: i.issue }))
  return parts.join(' · ')
})

const dateLabel = computed(() => {
  const i = props.instance
  if (!i) return ''
  return formatPubDate(i.publicationYear, i.publicationMonth, i.publicationDay)
})
</script>

<template>
  <section class="relative overflow-hidden rounded-2xl surface-glass p-6">
    <div class="absolute -right-16 -top-16 size-40 rounded-full bg-violet-500/10 blur-3xl pointer-events-none" />

    <h2 class="relative z-10 mb-4 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wider text-adaptive-muted">
      <UIcon name="i-lucide-sparkles" class="size-3.5" />
      {{ t('venues.detail.overview.latestIssue') }}
    </h2>

    <div v-if="loading" class="space-y-3">
      <AppSkeleton height="h-7" rounded="rounded-lg" />
      <AppSkeleton height="h-5" width="w-40" />
      <AppSkeleton height="h-5" width="w-32" />
    </div>

    <NuxtLink
      v-else-if="instance"
      :to="`/venues/${venueId}/issues/${instance.id}`"
      class="relative z-10 block group"
    >
      <div class="text-2xl font-bold tracking-tight text-adaptive-title transition-colors group-hover:text-violet-600 dark:group-hover:text-violet-400">
        {{ volumeIssueLabel || t('venues.detail.overview.latestIssue') }}
      </div>
      <div class="mt-1 text-sm text-adaptive-muted">{{ dateLabel }}</div>
      <div class="mt-3 inline-flex items-center gap-1 rounded-md bg-violet-500/10 px-2 py-0.5 text-[11px] font-medium text-violet-700 ring-1 ring-violet-500/20 dark:text-violet-300">
        {{ t('articles.summary', { date: dateLabel, count: instance.publicationCount }) }}
      </div>
      <div class="mt-4 inline-flex items-center gap-1 text-sm font-medium text-violet-600 transition-all group-hover:gap-2 dark:text-violet-400">
        {{ t('venues.detail.overview.browseCurrentIssue') }}
        <UIcon name="i-lucide-arrow-right" class="size-4" />
      </div>
    </NuxtLink>

    <div v-else class="relative z-10 text-sm text-adaptive-muted">
      {{ t('articles.noArticles') }}
    </div>
  </section>
</template>
```

- [ ] **Step 2：typecheck + 提交**

```bash
pnpm typecheck
git add app/components/venue/VenueLatestIssueCard.vue
git commit -m "feat(venue): 新增 VenueLatestIssueCard 组件$(printf '\n\n')Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 8：前端 · VenueTopPublicationsList 组件

**Files:**
- Create: `patra-web/app/components/venue/VenueTopPublicationsList.vue`

**依赖：** Task 4, Task 5

- [ ] **Step 1：创建组件**

```vue
<script setup lang="ts">
import type { TopPublicationItem } from '~/types/venue'

interface Props {
  venueId: string | number
  items: TopPublicationItem[] | null
  loading: boolean
}

const props = defineProps<Props>()
const { t } = useI18n()

const isEmpty = computed(() => !props.loading && (!props.items || props.items.length === 0))
</script>

<template>
  <section v-if="loading || !isEmpty" class="rounded-2xl surface-glass p-6">
    <h2 class="mb-4 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wider text-adaptive-muted">
      <UIcon name="i-lucide-flame" class="size-3.5" />
      {{ t('venues.detail.overview.topCited') }}
    </h2>

    <div v-if="loading" class="space-y-3">
      <AppSkeleton v-for="i in 5" :key="i" height="h-12" rounded="rounded-lg" />
    </div>

    <ol v-else-if="items" class="flex flex-col gap-3">
      <li v-for="(pub, idx) in items" :key="pub.id">
        <NuxtLink
          :to="`/publications/${pub.id}`"
          class="group block rounded-lg border border-transparent px-2 py-2 transition-all hover:border-violet-500/20 hover:bg-violet-500/5"
        >
          <div class="flex items-start gap-3">
            <span class="mt-0.5 shrink-0 font-mono text-xs font-semibold text-violet-500/70 dark:text-violet-400/70">
              {{ String(idx + 1).padStart(2, '0') }}
            </span>
            <div class="min-w-0 flex-1">
              <p class="line-clamp-2 text-sm font-medium text-adaptive-body transition-colors group-hover:text-violet-600 dark:group-hover:text-violet-400">
                {{ pub.title }}
              </p>
              <div class="mt-1 flex items-center gap-3 text-[11px] text-adaptive-muted">
                <span v-if="pub.publicationYear">{{ pub.publicationYear }}</span>
                <span v-if="pub.citationCount != null" class="inline-flex items-center gap-1">
                  <UIcon name="i-lucide-quote" class="size-3" />
                  {{ t('venues.detail.overview.citationCount', { count: pub.citationCount }) }}
                </span>
              </div>
            </div>
          </div>
        </NuxtLink>
      </li>
    </ol>
  </section>
</template>
```

- [ ] **Step 2：typecheck + 提交**

```bash
pnpm typecheck
git add app/components/venue/VenueTopPublicationsList.vue
git commit -m "feat(venue): 新增 VenueTopPublicationsList 组件$(printf '\n\n')Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 9：前端 · VenueExternalLinks 组件

**Files:**
- Create: `patra-web/app/components/venue/VenueExternalLinks.vue`

**依赖：** Task 5

- [ ] **Step 1：创建组件**

```vue
<script setup lang="ts">
import type { VenueDetail } from '~/types/venue'

interface Props {
  venue: VenueDetail
}

interface LinkItem {
  key: string
  icon: string
  url: string
}

const props = defineProps<Props>()
const { t } = useI18n()

const links = computed<LinkItem[]>(() => {
  const v = props.venue
  const result: LinkItem[] = []
  const homepage = v.publicationProfile?.homepageUrl
  if (homepage) result.push({ key: 'homepage', icon: 'i-lucide-globe', url: homepage })
  if (v.issnL) {
    result.push({
      key: 'doaj',
      icon: 'i-lucide-book-open',
      url: `https://doaj.org/toc/${v.issnL}`
    })
    result.push({
      key: 'issnPortal',
      icon: 'i-lucide-id-card',
      url: `https://portal.issn.org/resource/ISSN/${v.issnL}`
    })
  }
  if (v.title) {
    result.push({
      key: 'crossref',
      icon: 'i-lucide-search',
      url: `https://search.crossref.org/?from_ui=yes&container-title=${encodeURIComponent(v.title)}`
    })
  }
  if (v.nlmId) {
    result.push({
      key: 'pubmed',
      icon: 'i-lucide-microscope',
      url: `https://www.ncbi.nlm.nih.gov/nlmcatalog/${v.nlmId}`
    })
  }
  return result
})
</script>

<template>
  <section v-if="links.length > 0" class="rounded-2xl surface-glass p-6">
    <h2 class="mb-4 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wider text-adaptive-muted">
      <UIcon name="i-lucide-link-2" class="size-3.5" />
      {{ t('venues.detail.overview.externalLinks') }}
    </h2>
    <div class="flex flex-wrap gap-2">
      <a
        v-for="link in links"
        :key="link.key"
        :href="link.url"
        target="_blank"
        rel="noopener"
        class="inline-flex items-center gap-1.5 rounded-md bg-slate-100 px-3 py-1.5 text-xs font-medium text-adaptive-body ring-1 ring-slate-200 transition-all hover:bg-violet-500/10 hover:text-violet-700 hover:ring-violet-500/30 dark:bg-white/5 dark:ring-white/10 dark:hover:bg-violet-500/10 dark:hover:text-violet-300 dark:hover:ring-violet-500/30"
      >
        <UIcon :name="link.icon" class="size-3.5" />
        {{ t(`venues.detail.overview.links.${link.key}`) }}
        <UIcon name="i-lucide-arrow-up-right" class="size-3 opacity-60" />
      </a>
    </div>
  </section>
</template>
```

- [ ] **Step 2：typecheck + 提交**

```bash
pnpm typecheck
git add app/components/venue/VenueExternalLinks.vue
git commit -m "feat(venue): 新增 VenueExternalLinks 组件$(printf '\n\n')Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 10：前端 · VenueDetailHeader Meta 瘦身

**Files:**
- Modify: `patra-web/app/components/venue/VenueDetailHeader.vue`

**依赖：** 无

- [ ] **Step 1：修改 metaItems 计算**

把 `VenueDetailHeader.vue` 中的 `metaItems` computed 替换为（只保留 ISSN-L / 出版机构 / 国家 3 项）：

```typescript
const metaItems = computed(() => {
  const v = props.venue
  const profile = v.publicationProfile
  const items: { icon: string; text: string }[] = []
  if (v.issnL) items.push({ icon: 'i-lucide-hash', text: `ISSN-L ${v.issnL}` })
  if (profile?.hostOrganization?.name) items.push({ icon: 'i-lucide-building-2', text: profile.hostOrganization.name })
  if (v.countryCode) items.push({ icon: 'i-lucide-globe', text: v.countryCode })
  return items
})
```

**删除** 原有的 frequency / languages / publicationHistory 相关 push 逻辑（6 行）。

- [ ] **Step 2：typecheck + 本地预览**

```bash
pnpm typecheck
pnpm dev  # 打开 http://localhost:3000/venues/<id> 确认 meta 行仅剩 3 项
```

- [ ] **Step 3：提交**

```bash
git add app/components/venue/VenueDetailHeader.vue
git commit -m "refactor(venue): VenueDetailHeader meta 行瘦身到 3 项$(printf '\n\n')移除刊期、语言、创刊年，下沉到概览档案区，解决 Header/Overview 字段重复。$(printf '\n\n')Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 11：前端 · VenueTabOverview 3 屏骨架重写

**Files:**
- Modify: `patra-web/app/components/venue/VenueTabOverview.vue`
- Modify: `patra-web/app/pages/venues/[id].vue` (传递新 props 如需)

**依赖：** Task 4, 5, 6, 7, 8, 9

- [ ] **Step 1：完整替换 `VenueTabOverview.vue`**

```vue
<script setup lang="ts">
import type { VenueDetail, VenueStats, VenueInstanceItem } from '~/types/venue'

interface Props {
  venue: VenueDetail
  stats: VenueStats | null
}

const props = defineProps<Props>()
const { t } = useI18n()

const venueId = computed(() => props.venue.id)

// --- 第一屏数据 ---
const trendDatasets = computed(() => {
  const years = props.stats?.stats ?? []
  if (years.length === 0) return []
  return [
    {
      label: t('venues.detail.overview.worksCount'),
      data: years.map(y => ({ year: y.year, value: y.worksCount })),
      color: 'rgb(139, 92, 246)'
    },
    {
      label: t('venues.detail.overview.citedByCount'),
      data: years.map(y => ({ year: y.year, value: y.citedByCount })),
      color: 'rgb(6, 182, 212)'
    }
  ]
})

// --- 第二屏：最新一期 + Top5 ---
const instanceQuery = reactive({ page: 1, pageSize: 1 })
const { data: latestPage, status: latestStatus } = useVenueInstances(
  () => venueId.value,
  instanceQuery
)
const latestInstance = computed<VenueInstanceItem | null>(
  () => latestPage.value?.items?.[0] ?? null
)

const topPubsParams = reactive({ limit: 5 })
const { data: topPubs, status: topStatus } = useVenueTopPublications(
  () => venueId.value,
  topPubsParams
)

// --- 第三屏：档案下沉版 ---
const profile = computed(() => props.venue.publicationProfile)

const publishingEntries = computed(() => {
  const p = profile.value
  const entries: { label: string; value: string }[] = []
  if (p?.frequency) entries.push({ label: t('venues.detail.overview.frequency'), value: p.frequency })
  const primary = p?.languages?.primary ?? []
  if (primary.length > 0) entries.push({ label: t('venues.detail.overview.language'), value: primary.join(', ') })
  else if (props.venue.primaryLanguage) entries.push({ label: t('venues.detail.overview.language'), value: props.venue.primaryLanguage })
  if (p?.publicationHistory?.startYear) {
    const start = p.publicationHistory.startYear
    const end = p.publicationHistory.endYear
    entries.push({ label: t('venues.detail.overview.founded'), value: end ? `${start} – ${end}` : String(start) })
  }
  if (p?.publicationHistory?.ceased) entries.push({ label: t('venues.detail.overview.ceased'), value: t('common.yes') })
  return entries
})

const identifierEntries = computed(() => {
  const v = props.venue
  const list: { label: string; value: string }[] = []
  if (v.issnL) list.push({ label: 'ISSN-L', value: v.issnL })
  if (v.nlmId) list.push({ label: 'NLM ID', value: v.nlmId })
  if (v.openalexId) list.push({ label: 'OpenAlex', value: v.openalexId })
  return list
})

const relationLabel = (type: string): string => {
  const key = `venues.detail.overview.relationTypes.${type}`
  const translated = t(key)
  return translated === key ? type : translated
}
</script>

<template>
  <div class="space-y-6">
    <!-- 第一屏：主题 + 趋势 -->
    <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <VenueMeshGroupedList :headings="venue.meshHeadings" />
      <section v-if="trendDatasets.length > 0" class="rounded-2xl surface-glass p-6">
        <h2 class="mb-4 text-[11px] font-semibold uppercase tracking-wider text-adaptive-muted">
          {{ t('venues.detail.overview.annualTrend') }}
        </h2>
        <VenueRatingTrendChart :datasets="trendDatasets" :height="260" />
      </section>
    </div>

    <!-- 第二屏：最新一期 + Top5 -->
    <div class="grid grid-cols-1 gap-6 lg:grid-cols-[3fr_2fr]">
      <VenueLatestIssueCard
        :venue-id="venueId"
        :instance="latestInstance"
        :loading="latestStatus === 'pending'"
      />
      <VenueTopPublicationsList
        :venue-id="venueId"
        :items="topPubs ?? null"
        :loading="topStatus === 'pending'"
      />
    </div>

    <!-- 第三屏：档案 + 出口辅助 -->
    <div class="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_24rem]">
      <div class="space-y-6">
        <section v-if="publishingEntries.length > 0" class="rounded-2xl surface-glass p-6">
          <h2 class="mb-4 text-[11px] font-semibold uppercase tracking-wider text-adaptive-muted">
            {{ t('venues.detail.overview.publishingInfo') }}
          </h2>
          <dl class="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
            <div v-for="entry in publishingEntries" :key="entry.label" class="flex flex-col">
              <dt class="text-[10px] font-medium uppercase tracking-wider text-adaptive-muted">{{ entry.label }}</dt>
              <dd class="mt-0.5 text-sm font-medium text-adaptive-body">{{ entry.value }}</dd>
            </div>
          </dl>
        </section>

        <section v-if="venue.affiliatedSocieties.length > 0" class="rounded-2xl surface-glass p-6">
          <h2 class="mb-4 text-[11px] font-semibold uppercase tracking-wider text-adaptive-muted">
            {{ t('venues.detail.overview.societies') }}
          </h2>
          <ul class="flex flex-col gap-2">
            <li v-for="(society, idx) in venue.affiliatedSocieties" :key="idx"
                class="flex items-center gap-2 text-sm text-adaptive-body">
              <UIcon name="i-lucide-users" class="size-4 text-adaptive-muted" />
              <a v-if="society.url" :href="society.url" target="_blank" rel="noopener"
                 class="inline-flex items-center gap-1 transition-colors hover:text-cyan-600 dark:hover:text-cyan-400">
                {{ society.organization }}
                <UIcon name="i-lucide-arrow-up-right" class="size-3" />
              </a>
              <span v-else>{{ society.organization }}</span>
            </li>
          </ul>
        </section>

        <section v-if="venue.relations.length > 0" class="rounded-2xl surface-glass p-6">
          <h2 class="mb-4 text-[11px] font-semibold uppercase tracking-wider text-adaptive-muted">
            {{ t('venues.detail.overview.relations') }}
          </h2>
          <ul class="flex flex-col gap-2">
            <li v-for="(rel, idx) in venue.relations" :key="idx" class="flex items-center gap-2 text-sm">
              <span class="inline-flex items-center rounded-md bg-slate-100 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider text-slate-500 ring-1 ring-slate-200 dark:bg-white/5 dark:text-slate-400 dark:ring-white/10">
                {{ relationLabel(rel.relationType) }}
              </span>
              <NuxtLink v-if="rel.relatedVenueId" :to="`/venues/${rel.relatedVenueId}`"
                class="text-adaptive-body transition-colors hover:text-violet-600 dark:hover:text-violet-400">
                {{ rel.relatedTitle }}
              </NuxtLink>
              <span v-else class="text-adaptive-body">{{ rel.relatedTitle }}</span>
            </li>
          </ul>
        </section>
      </div>

      <div class="space-y-6 lg:w-96">
        <section v-if="identifierEntries.length > 0" class="rounded-2xl surface-glass p-6">
          <h2 class="mb-4 text-[11px] font-semibold uppercase tracking-wider text-adaptive-muted">
            {{ t('venues.detail.overview.identifiers') }}
          </h2>
          <ul class="flex flex-col gap-3">
            <li v-for="entry in identifierEntries" :key="entry.label" class="flex flex-col">
              <span class="text-[10px] font-medium uppercase tracking-wider text-adaptive-muted">{{ entry.label }}</span>
              <span class="mt-0.5 break-all font-mono text-sm text-adaptive-body">{{ entry.value }}</span>
            </li>
          </ul>
        </section>

        <VenueExternalLinks :venue="venue" />
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 2：移除 `[id].vue` 中已不需要的处理（若有）**

扫一眼 `app/pages/venues/[id].vue`，确认 `<VenueTabOverview :venue :stats />` prop 仍一致；无需额外改动。

- [ ] **Step 3：本地预览验证**

```bash
pnpm dev
# 浏览器打开 http://localhost:3000/venues/<existing-id>
# 目测：
# - 第一屏：左 MeSH 条形、右 趋势图宽幅
# - 第二屏：最新一期卡（可点）+ Top5（可点）
# - 第三屏：档案不含 ISSN-L/机构/国家，外链行在右下
```

- [ ] **Step 4：typecheck**

```bash
pnpm typecheck
```

- [ ] **Step 5：提交**

```bash
git add app/components/venue/VenueTabOverview.vue
git commit -m "$(cat <<'EOF'
refactor(venue): VenueTabOverview 改为 3 屏骨架

按"身份→影响力→主题→趋势→出口"故事线重排：
- 第一屏：MeSH 主题分布（假数据）+ 年度趋势宽图
- 第二屏：最新一期卡 + Top5 高被引
- 第三屏：档案下沉版（去重 ISSN-L/机构/国家）+ 外链行

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12：集成验证 + 回归

**Files:** 无改动（验证性 Task）

**依赖：** Task 1–11 全部完成

- [ ] **Step 1：后端全量测试**

```bash
cd /Users/linqibin/Desktop/Patra/patra-api
./gradlew :patra-catalog:patra-catalog-infra:test :patra-catalog:patra-catalog-adapter:test :patra-catalog:patra-catalog-app:test
```

期望：全绿。若出现已知的 `AuthorRepositoryAdapterIT#ChildEntityTests.save_fullAuthorInfo_shouldPersistAllChildEntities` 失败（memory 记录，与本次改动无关），记录但不阻塞。

- [ ] **Step 2：后端端点冒烟**

```bash
# 启动 catalog-boot 后
curl -s "http://localhost:8080/patra-catalog/venues/<existing-id>/top-publications?limit=3" | jq .
curl -s "http://localhost:8080/patra-catalog/venues/<existing-id>/top-publications?limit=21" -w "\nHTTP %{http_code}\n"  # 期望 400
curl -s "http://localhost:8080/patra-catalog/venues/<existing-id>/top-publications?since=2020&limit=5" | jq '.[].publicationYear'
```

期望：第 1 条返回 ≤3 条按 citation_count 降序；第 2 条返回 HTTP 400；第 3 条所有 publicationYear ≥ 2020。

- [ ] **Step 3：前端全链路**

```bash
cd /Users/linqibin/Desktop/Patra/patra-web
pnpm typecheck
pnpm dev
```

浏览器手动验证清单：
- [ ] 列表页 `/venues` 正常
- [ ] 详情页 `/venues/<id>` Header 仅 3 项 meta
- [ ] 概览 Tab 三屏布局正确
- [ ] MeSH 条形渲染 + "示例数据" 提示可见
- [ ] MeSH 展开/折叠切换
- [ ] 最新一期卡可点 → 跳 `/venues/<id>/issues/<iid>`
- [ ] Top5 某行可点 → 跳 `/publications/<pubId>`
- [ ] 外链 chip 只显示字段可用的
- [ ] 深色/浅色模式切换无视觉残缺
- [ ] 加载中显示 skeleton

- [ ] **Step 4：不变量回归（其他页面）**

确认以下页面 **未受影响**（快速扫一眼）：
- `/venues/compare` 对比页
- `/venues/<id>/issues/<iid>` 文章列表
- `/publications/<id>` 文献详情
- Metrics / Indexing / Issues 其他 Tab

- [ ] **Step 5：生成 follow-up issue 清单（不提交代码，只记录）**

把 Task 1 Step 5 诊断到的索引情况、浏览器验证发现的小问题写入 `docs/superpowers/followups/2026-04-15-venue-overview-followups.md`（如有）。

- [ ] **Step 6：最终提交（如有遗留）**

```bash
cd /Users/linqibin/Desktop/Patra/patra-api
git status  # 确认干净
cd /Users/linqibin/Desktop/Patra/patra-web
git status  # 确认干净
```

无未提交内容即可。

---

## Self-Review（plan 自检）

**Spec 覆盖检查：**

| Spec 要求 | 对应 Task |
|---|---|
| §4.1 Header 瘦身 | Task 10 |
| §4.2/4.3 3 屏骨架 | Task 11 |
| §5.1 VenueMeshGroupedList | Task 4（工具）+ Task 6（组件）|
| §5.2 VenueLatestIssueCard | Task 7 |
| §5.3 VenueTopPublicationsList | Task 4（composable）+ Task 8（组件）|
| §5.4 VenueExternalLinks | Task 9 |
| §5.5 趋势图位置调整 | Task 11（布局网格变更）|
| §6 BE-1 Top-N 端点 | Task 1, 2, 3 |
| §7 i18n 新 key | Task 5 |
| §8 验收清单 | Task 12 |

**Placeholder 扫描：** 无 TBD/TODO/"handle edge cases"。所有测试和代码均已写出完整内容。

**类型一致性：** `TopPublicationItem`（前端）对应 `TopPublicationItemResponse`（后端 DTO），字段名一致（id/title/publicationYear/citationCount/doi）。`MeshMockItem` 与 `VenueMeshHeading` 通过 `buildMockMeshDistribution` 转换。

**命名一致性：** `findTopByVenue` 在 DAO/ReadPort/ReadAdapter 统一；`listTopPublicationsByVenue` 在 QueryService/Controller 统一。

---

## 执行方式选择

**1. Subagent-Driven（推荐）** —— 每个 Task 派发全新 subagent，两阶段 review（spec 合规 + 代码质量），同 session 内推进。

**2. Inline Execution** —— 在当前 session 批量执行，checkpoint review。

两种都会严格遵循 TDD（后端）+ DESIGN.md/GEMINI.md（前端）。
