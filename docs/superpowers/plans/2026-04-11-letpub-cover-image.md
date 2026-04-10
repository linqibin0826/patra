# LetPub 期刊封面图抓取实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 patra-catalog 的 LetPub 富化流程中，解析期刊详情页的封面图 URL，下载后上传到对象存储，数据库只保留对象键；失败降级为 WARN 日志不阻断批次。

**Architecture:** 严格遵守六边形架构：Domain 新增 `VenueCoverImageDownloadPort` 抽象下载+存储能力，Infra 通过 `VenueCoverImageDownloadAdapter` 组合现有 `FileDownloadPort` + `ObjectStorageOperations` 实现。字段 `imageUrl` → `imageObjectKey` 级联重命名贯穿 SQL/Entity/Aggregate/ReadModel/API Response。`LetPubVenueItemProcessor` 内联调用下载，用 try-catch 隔离故障。

**Tech Stack:** Java 25 / Spring Boot 4.0.1 / Spring Batch / Hibernate JPA / Jsoup / MapStruct / MinIO (via `patra-spring-boot-starter-object-storage`) / JUnit 5 / Mockito / AssertJ

**Related Spec:** `docs/superpowers/specs/2026-04-11-letpub-cover-image-design.md`

---

## File Structure Summary

### New files
| 路径 | 职责 |
|------|------|
| `patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/storage/VenueCoverImageDownloadPort.java` | Domain Port：下载并存储封面图接口 |
| `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageDownloadAdapter.java` | Infra 适配器：组合 FileDownloadPort + ObjectStorage |
| `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageProperties.java` | 桶配置属性 record |
| `patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageDownloadAdapterTest.java` | Adapter 单元测试 |
| `patra-catalog/patra-catalog-domain/src/test/java/com/patra/catalog/domain/model/aggregate/VenueAggregateImageKeyTest.java` | Aggregate 封面字段单元测试 |

### Modified files
| 路径 | 修改点 |
|------|--------|
| `V1.0.0__create_venue_aggregate.sql:74` | `image_url VARCHAR(2048)` → `image_object_key VARCHAR(512)` |
| `VenueEntity.java:74-76` | `@Column(name="image_url") imageUrl` → `@Column(name="image_object_key") imageObjectKey` |
| `VenueAggregate.java:75-76,103-115,150-159,172-184` | 字段 / 构造函数 / restore / enrichImageUrl 重命名 |
| `VenueJpaMapper.java:116,198-207` | `getImageUrl()/setImageUrl()` → `getImageObjectKey()/setImageObjectKey()` |
| `VenueReadModelMapper.java:33` | `@Mapping source=entity.imageUrl target=imageUrl` → `source=entity.imageObjectKey target=imageObjectKey` |
| `VenueSummaryReadModel.java:18` | 字段 `String imageUrl` → `String imageObjectKey` |
| `VenueItemResponse.java:14` | 字段 `String imageUrl` → `String imageObjectKey` |
| `VenueSummaryReadModelTest.java:60,78,91` | 字段名级联更新 |
| `VenueControllerIT.java:58,95-96` | Builder 字段 + JsonPath 更新 |
| `LetPubVenueData.java:83,56-83` | 新增字段 `String coverImageSourceUrl` |
| `LetPubDetailPageParser.java:98-117` | `parse()` 中新增 `parseCoverImageUrl()` 调用 |
| `LetPubDetailPageParserTest.java` (末尾) | 新增 `CoverImageTests` @Nested 类 |
| `detail-page.html:32-40` | 替换 fake img，加入 layui-form-item + 真实 Aliyun URL |
| `LetPubVenueItemProcessor.java:23-60` | 注入 `VenueCoverImageDownloadPort`，新增 `downloadCoverIfNeeded()` |

---

## Execution Order Rationale

遵循**依赖倒置**——从纯领域对象到外部适配：

1. **Domain 重命名**（Task 1）：字段改名是所有后续工作的基础，先改掉不会破坏编译
2. **持久化层**（Task 2）：Flyway + Entity + Mapper 同步
3. **读模型 + API 契约**（Task 3）：对外契约破坏性变更一次到位
4. **Parser 解析封面 URL**（Task 4）：填充 `LetPubVenueData.coverImageSourceUrl`
5. **Port + Adapter + Properties**（Task 5）：引入下载能力
6. **Adapter 异常路径**（Task 6）：超限 / 空文件 / 清理
7. **Processor 编排**（Task 7）：串起全链路
8. **Processor 幂等与 URI 容错**（Task 8）
9. **回归修复**（Task 9）：原有 Parser 测试不因 fixture 变化而断
10. **全量验证**（Task 10）：module-level gradle build

---

## Task 1: Rename `imageUrl` → `imageObjectKey` in VenueAggregate (Domain)

**Files:**
- Create: `patra-catalog/patra-catalog-domain/src/test/java/com/patra/catalog/domain/model/aggregate/VenueAggregateImageKeyTest.java`
- Modify: `patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/aggregate/VenueAggregate.java`

- [ ] **Step 1.1: Write the failing test**

Create file `patra-catalog/patra-catalog-domain/src/test/java/com/patra/catalog/domain/model/aggregate/VenueAggregateImageKeyTest.java`:

```java
package com.patra.catalog.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueAggregate 封面对象键字段单元测试。
///
/// 验证 `imageObjectKey` 字段的 null-safe 幂等富化语义。
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@DisplayName("VenueAggregate imageObjectKey 字段测试")
class VenueAggregateImageKeyTest {

  @Nested
  @DisplayName("enrichImageObjectKey 幂等语义")
  class EnrichImageObjectKeyTests {

    @Test
    @DisplayName("restore 后传入非 null 值应更新字段")
    void shouldUpdateWhenPreviousIsNull() {
      VenueAggregate aggregate =
          VenueAggregate.restore(VenueId.of(1L), VenueType.JOURNAL, "Nature", null, 0L);
      assertThat(aggregate.getImageObjectKey()).isNull();

      aggregate.enrichImageObjectKey("catalog/venue-cover/1.jpg");

      assertThat(aggregate.getImageObjectKey()).isEqualTo("catalog/venue-cover/1.jpg");
    }

    @Test
    @DisplayName("传入 null 不应清空已有值")
    void shouldIgnoreWhenInputIsNull() {
      VenueAggregate aggregate =
          VenueAggregate.restore(
              VenueId.of(1L), VenueType.JOURNAL, "Nature", "catalog/venue-cover/1.jpg", 0L);
      assertThat(aggregate.getImageObjectKey()).isEqualTo("catalog/venue-cover/1.jpg");

      aggregate.enrichImageObjectKey(null);

      assertThat(aggregate.getImageObjectKey()).isEqualTo("catalog/venue-cover/1.jpg");
    }
  }
}
```

- [ ] **Step 1.2: Run test to verify it fails**

Run: `./gradlew :patra-catalog:patra-catalog-domain:test --tests "com.patra.catalog.domain.model.aggregate.VenueAggregateImageKeyTest"`

Expected: FAIL with compile error `cannot find symbol: method getImageObjectKey()` / `enrichImageObjectKey(String)` / `restore(..., String, Long)` matching new name.

- [ ] **Step 1.3: Rename the field in VenueAggregate**

In `patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/aggregate/VenueAggregate.java`:

Change line 75-76 from:
```java
  /// 封面图片 URL（可空，来自 LetPub 期刊详情页，可后续富化）
  private String imageUrl;
```
to:
```java
  /// 封面图片对象存储键（可空，来自 LetPub 下载后上传到对象存储，相对于 venue-cover 桶）
  private String imageObjectKey;
```

- [ ] **Step 1.4: Update the private constructor**

Change lines 98-115 from:
```java
  /// 私有构造函数（通过工厂方法创建）。
  ///
  /// @param id 主键 ID（新建时为 null）
  /// @param venueType 载体类型
  /// @param title 标题
  /// @param imageUrl 封面图片 URL（可空）
  private VenueAggregate(VenueId id, VenueType venueType, String title, String imageUrl) {
    super(id);

    Assert.notNull(venueType, "载体类型不能为空");
    Assert.notBlank(title, "标题不能为空");

    this.venueType = venueType;
    this.title = title;
    this.imageUrl = imageUrl;
    this.identifiers = new ArrayList<>();
    this.affiliatedSocieties = new ArrayList<>();
  }
```
to:
```java
  /// 私有构造函数（通过工厂方法创建）。
  ///
  /// @param id 主键 ID（新建时为 null）
  /// @param venueType 载体类型
  /// @param title 标题
  /// @param imageObjectKey 封面对象存储键（可空）
  private VenueAggregate(VenueId id, VenueType venueType, String title, String imageObjectKey) {
    super(id);

    Assert.notNull(venueType, "载体类型不能为空");
    Assert.notBlank(title, "标题不能为空");

    this.venueType = venueType;
    this.title = title;
    this.imageObjectKey = imageObjectKey;
    this.identifiers = new ArrayList<>();
    this.affiliatedSocieties = new ArrayList<>();
  }
```

- [ ] **Step 1.5: Update `restore` factory method**

Change lines 146-159 from:
```java
  /// 从持久化状态重建聚合根（由 Repository 使用）。
  ///
  /// @param id 主键 ID（VenueId 值对象）
  /// @param venueType 载体类型
  /// @param title 标题
  /// @param imageUrl 封面图片 URL（可空）
  /// @param version 乐观锁版本
  /// @return 重建的聚合根
  public static VenueAggregate restore(
      VenueId id, VenueType venueType, String title, String imageUrl, Long version) {
    VenueAggregate aggregate = new VenueAggregate(id, venueType, title, imageUrl);
    aggregate.assignVersion(version != null ? version : 0L);
    return aggregate;
  }
```
to:
```java
  /// 从持久化状态重建聚合根（由 Repository 使用）。
  ///
  /// @param id 主键 ID（VenueId 值对象）
  /// @param venueType 载体类型
  /// @param title 标题
  /// @param imageObjectKey 封面对象存储键（可空）
  /// @param version 乐观锁版本
  /// @return 重建的聚合根
  public static VenueAggregate restore(
      VenueId id, VenueType venueType, String title, String imageObjectKey, Long version) {
    VenueAggregate aggregate = new VenueAggregate(id, venueType, title, imageObjectKey);
    aggregate.assignVersion(version != null ? version : 0L);
    return aggregate;
  }
```

- [ ] **Step 1.6: Update `enrichImageUrl` method**

Change lines 172-184 from:
```java
  // ========== 富化方法 ==========

  /// 富化封面图片 URL。
  ///
  /// 用于 LetPub 富化流程为 Venue 补充从 LetPub 期刊详情页抓取的封面图片地址。
  /// 传入 null 时不做任何操作（表示未查询到数据，不应清除已有值）。
  ///
  /// @param imageUrl 封面图片 URL（null 表示无数据，不清除已有值）
  public void enrichImageUrl(String imageUrl) {
    if (imageUrl != null) {
      this.imageUrl = imageUrl;
    }
  }
```
to:
```java
  // ========== 富化方法 ==========

  /// 富化封面图对象存储键。
  ///
  /// 用于 LetPub 富化流程：下载 LetPub 详情页上的封面图到对象存储后，
  /// 将返回的对象键回填到聚合根。
  /// 传入 null 时不做任何操作（表示未下载到数据，不应清除已有值）。
  ///
  /// @param imageObjectKey 封面对象存储键（null 表示无数据，不清除已有值）
  public void enrichImageObjectKey(String imageObjectKey) {
    if (imageObjectKey != null) {
      this.imageObjectKey = imageObjectKey;
    }
  }
```

- [ ] **Step 1.7: Update `fromPubMed` factory method reference**

Line 130 inside `fromPubMed` uses the constructor with `null` as 4th arg — the argument name changed but positional call works. No code change needed here, but verify visually that the line reads:
```java
    VenueAggregate aggregate = new VenueAggregate(null, VenueType.JOURNAL, title, null);
```
(unchanged, just confirming).

- [ ] **Step 1.8: Run test to verify it passes**

Run: `./gradlew :patra-catalog:patra-catalog-domain:test --tests "com.patra.catalog.domain.model.aggregate.VenueAggregateImageKeyTest"`

Expected: 2 tests PASS.

- [ ] **Step 1.9: Run full domain module tests to catch cascading breakage**

Run: `./gradlew :patra-catalog:patra-catalog-domain:test`

Expected: All tests pass. If `VenueSummaryReadModelTest` fails because it still references `imageUrl()`, that's fine — it will be fixed in Task 3. For now, temporarily skip domain module full test until Task 3. However, if any test in `aggregate/` fails, fix the reference before committing.

Note: Since `VenueSummaryReadModel` still has field `imageUrl`, the domain module may still compile. Expected outcome: domain module compiles; `VenueSummaryReadModelTest` still passes (it doesn't reference aggregate). `VenueAggregateImageKeyTest` passes.

- [ ] **Step 1.10: Commit**

```bash
git add patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/aggregate/VenueAggregate.java \
        patra-catalog/patra-catalog-domain/src/test/java/com/patra/catalog/domain/model/aggregate/VenueAggregateImageKeyTest.java
git commit -m "refactor(catalog-domain): rename VenueAggregate.imageUrl → imageObjectKey

封面图字段语义从外链 URL 切换为对象存储键，为 LetPub 下载到对象存储的
闭环改造做准备。重命名字段、构造器、restore 工厂、enrichImageObjectKey 方法。
新增 VenueAggregateImageKeyTest 验证 null-safe 幂等语义。"
```

---

## Task 2: Flyway + Entity + JpaMapper migration (Infra persistence)

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V1.0.0__create_venue_aggregate.sql`
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/entity/VenueEntity.java`
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/converter/mapper/VenueJpaMapper.java`

- [ ] **Step 2.1: Modify Flyway V1.0.0 SQL**

In `patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V1.0.0__create_venue_aggregate.sql`, change line 74 from:
```sql
    `image_url` VARCHAR(2048) NULL DEFAULT NULL COMMENT '封面图片 URL（来自 LetPub 期刊详情页）',
```
to:
```sql
    `image_object_key` VARCHAR(512) NULL DEFAULT NULL COMMENT '封面图片对象存储键（相对于 venue-cover 桶）',
```

- [ ] **Step 2.2: Modify VenueEntity field**

In `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/entity/VenueEntity.java`, change lines 74-76 from:
```java
  /// 封面图片 URL（可空，来自 LetPub 期刊详情页）
  @Column(name = "image_url", length = 2048)
  private String imageUrl;
```
to:
```java
  /// 封面图片对象存储键（可空，指向 venue-cover 桶中的对象键）
  @Column(name = "image_object_key", length = 512)
  private String imageObjectKey;
```

- [ ] **Step 2.3: Modify VenueJpaMapper.toAggregate call**

In `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/converter/mapper/VenueJpaMapper.java`, change line 116 from:
```java
            entity.getImageUrl(),
```
to:
```java
            entity.getImageObjectKey(),
```

- [ ] **Step 2.4: Modify VenueJpaMapper.updateEntity**

In the same file, change lines 198-207 from:
```java
  /// 更新托管实体的可变字段。
  ///
  /// 注意：venueType 和 title 是不可变的，不在此更新。
  /// imageUrl 是可富化字段（来自 LetPub），需要同步。
  ///
  /// @param entity 托管实体
  /// @param aggregate 包含更新数据的聚合根
  public void updateEntity(VenueEntity entity, VenueAggregate aggregate) {
    if (entity == null || aggregate == null) {
      return;
    }

    // 更新富化字段：imageUrl 来自 LetPub 期刊详情页
    entity.setImageUrl(aggregate.getImageUrl());
```
to:
```java
  /// 更新托管实体的可变字段。
  ///
  /// 注意：venueType 和 title 是不可变的，不在此更新。
  /// imageObjectKey 是可富化字段（LetPub 下载结果），需要同步。
  ///
  /// @param entity 托管实体
  /// @param aggregate 包含更新数据的聚合根
  public void updateEntity(VenueEntity entity, VenueAggregate aggregate) {
    if (entity == null || aggregate == null) {
      return;
    }

    // 更新富化字段：imageObjectKey 来自 LetPub 下载后的对象存储键
    entity.setImageObjectKey(aggregate.getImageObjectKey());
```

- [ ] **Step 2.5: Compile infra module**

Run: `./gradlew :patra-catalog:patra-catalog-infra:compileJava`

Expected: PASS. MapStruct will regenerate mapper with new field name. If generated sources reference `imageUrl`, rebuild clean:
```bash
./gradlew :patra-catalog:patra-catalog-infra:clean :patra-catalog:patra-catalog-infra:compileJava
```

- [ ] **Step 2.6: Run infra repository integration test**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests "com.patra.catalog.infra.adapter.persistence.VenueRepositoryAdapterIT"`

Expected: PASS (this IT doesn't reference `image_url` directly; TestContainers will pick up updated Flyway V1.0.0).

Note: If the TestContainers cache has the old schema baked in, Flyway will report a checksum mismatch because V1.0.0 was modified. Since this is a greenfield project, the fix is to drop the cached volume — TestContainers starts a fresh MySQL per test run by default, so no manual action should be needed.

- [ ] **Step 2.7: Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V1.0.0__create_venue_aggregate.sql \
        patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/entity/VenueEntity.java \
        patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/converter/mapper/VenueJpaMapper.java
git commit -m "refactor(catalog-infra): rename image_url → image_object_key 列与 JPA 映射

Flyway V1.0.0 列名从 image_url(VARCHAR 2048) 改为
image_object_key(VARCHAR 512)。VenueEntity / VenueJpaMapper 同步字段名。
绿地项目直接修改 V1.0.0 不建新迁移文件。"
```

---

## Task 3: Read model + API response contract rename

**Files:**
- Modify: `patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/read/venue/VenueSummaryReadModel.java`
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadModelMapper.java`
- Modify: `patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/response/VenueItemResponse.java`
- Modify: `patra-catalog/patra-catalog-domain/src/test/java/com/patra/catalog/domain/model/read/venue/VenueSummaryReadModelTest.java`
- Modify: `patra-catalog/patra-catalog-adapter/src/test/java/com/patra/catalog/adapter/rest/venue/VenueControllerIT.java`

- [ ] **Step 3.1: Update VenueSummaryReadModel field**

In `VenueSummaryReadModel.java`, change line 18 from:
```java
    String imageUrl,
```
to:
```java
    String imageObjectKey,
```

- [ ] **Step 3.2: Update VenueReadModelMapper mapping**

In `VenueReadModelMapper.java`, change line 33 from:
```java
  @Mapping(source = "entity.imageUrl", target = "imageUrl")
```
to:
```java
  @Mapping(source = "entity.imageObjectKey", target = "imageObjectKey")
```

- [ ] **Step 3.3: Update VenueItemResponse field**

In `VenueItemResponse.java`, change line 14 from:
```java
    String imageUrl,
```
to:
```java
    String imageObjectKey,
```

- [ ] **Step 3.4: Update VenueApiConverter mapping (if it exists)**

Check whether `VenueApiConverter` has explicit `@Mapping(source="imageUrl", target="imageUrl")`. If so, rename both sides to `imageObjectKey`. If relying on auto name-match, no change needed.

Find with: Grep for `imageUrl` under `patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/mapper/`. Apply rename to any matches.

- [ ] **Step 3.5: Update VenueSummaryReadModelTest fixtures**

In `VenueSummaryReadModelTest.java`:

Change line 60 from:
```java
      assertThat(model.imageUrl()).isNull();
```
to:
```java
      assertThat(model.imageObjectKey()).isNull();
```

Change line 78 from:
```java
              .imageUrl("https://example.com/nature.jpg")
```
to:
```java
              .imageObjectKey("catalog/venue-cover/1.jpg")
```

Change line 91 from:
```java
      assertThat(model.imageUrl()).isEqualTo("https://example.com/nature.jpg");
```
to:
```java
      assertThat(model.imageObjectKey()).isEqualTo("catalog/venue-cover/1.jpg");
```

- [ ] **Step 3.6: Update VenueControllerIT fixture + JsonPath**

In `VenueControllerIT.java`:

Change line 58 from:
```java
            .imageUrl("https://www.letpub.com.cn/cover/journal/0410462.jpg")
```
to:
```java
            .imageObjectKey("catalog/venue-cover/1001.jpg")
```

Change lines 95-96 from:
```java
        .jsonPath("$.items[0].imageUrl")
        .isEqualTo("https://www.letpub.com.cn/cover/journal/0410462.jpg")
```
to:
```java
        .jsonPath("$.items[0].imageObjectKey")
        .isEqualTo("catalog/venue-cover/1001.jpg")
```

- [ ] **Step 3.7: Build all three modules**

Run:
```bash
./gradlew :patra-catalog:patra-catalog-domain:test \
          :patra-catalog:patra-catalog-infra:compileJava \
          :patra-catalog:patra-catalog-adapter:test
```

Expected: All pass. Domain tests green (VenueSummaryReadModelTest uses new field). Adapter IT green (VenueControllerIT JsonPath matches new field). Infra compiles clean (MapStruct regenerates).

- [ ] **Step 3.8: Commit**

```bash
git add patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/read/venue/VenueSummaryReadModel.java \
        patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/read/VenueReadModelMapper.java \
        patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/venue/response/VenueItemResponse.java \
        patra-catalog/patra-catalog-domain/src/test/java/com/patra/catalog/domain/model/read/venue/VenueSummaryReadModelTest.java \
        patra-catalog/patra-catalog-adapter/src/test/java/com/patra/catalog/adapter/rest/venue/VenueControllerIT.java
git commit -m "refactor(catalog): 读模型与 API 响应 imageUrl → imageObjectKey

VenueSummaryReadModel / VenueReadModelMapper / VenueItemResponse 字段改名，
同步 VenueSummaryReadModelTest 与 VenueControllerIT 断言。
绿地项目直接破坏性重命名，不保留兼容字段。"
```

---

## Task 4: Parser extracts `coverImageSourceUrl` + fixture update

**Files:**
- Modify: `patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/enrichment/LetPubVenueData.java`
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/integration/letpub/LetPubDetailPageParser.java`
- Modify: `patra-catalog/patra-catalog-infra/src/test/resources/letpub/detail-page.html`
- Modify: `patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/integration/letpub/LetPubDetailPageParserTest.java`

- [ ] **Step 4.1: Add `coverImageSourceUrl` field to `LetPubVenueData`**

In `LetPubVenueData.java`, change the record header (lines 56-83) to add the new field. Specifically, change:
```java
@Builder
public record LetPubVenueData(
    // 基本信息
    String letPubJournalId,
    String letPubName,
    String researchDirection,
    Integer articlesPerYear,
    String goldOaPercent,
    String researchArticlePercent,
    // JCR 分区
    String jcrSubject,
    String jcrCollection,
    String jifQuartile,
    String jifRank,
    String jciQuartile,
    String jciRank,
    // CAS 分区（多版本）
    List<CasPartition> casPartitions,
    // 预警 + 审稿 + 费用
    String warningListStatus,
    String reviewSpeedOfficial,
    String reviewSpeedUser,
    String acceptanceRate,
    String apcInfo,
    // 影响因子
    Map<String, Double> impactFactorTrend,
    Double fiveYearImpactFactor,
    // 收录
    List<String> indexedIn) {
```
to:
```java
@Builder
public record LetPubVenueData(
    // 基本信息
    String letPubJournalId,
    String letPubName,
    String researchDirection,
    Integer articlesPerYear,
    String goldOaPercent,
    String researchArticlePercent,
    // 封面图（LetPub CDN 原始 URL）
    String coverImageSourceUrl,
    // JCR 分区
    String jcrSubject,
    String jcrCollection,
    String jifQuartile,
    String jifRank,
    String jciQuartile,
    String jciRank,
    // CAS 分区（多版本）
    List<CasPartition> casPartitions,
    // 预警 + 审稿 + 费用
    String warningListStatus,
    String reviewSpeedOfficial,
    String reviewSpeedUser,
    String acceptanceRate,
    String apcInfo,
    // 影响因子
    Map<String, Double> impactFactorTrend,
    Double fiveYearImpactFactor,
    // 收录
    List<String> indexedIn) {
```

Also add the javadoc line for the new field. Add after the `researchArticlePercent` javadoc param line:
```java
/// @param coverImageSourceUrl 封面图原始 URL（LetPub 托管于 Aliyun OSS CDN）
```

- [ ] **Step 4.2: Update fixture HTML to include real layui cover image**

In `patra-catalog/patra-catalog-infra/src/test/resources/letpub/detail-page.html`, replace lines 32-40:

Before:
```html
          <!-- 期刊名字（含封面图和评分，解析时忽略） -->
          <TR>
            <TD style="padding: 8px; border: 1px solid rgb(221, 221, 221); font-weight: bold;">期刊名字</TD>
            <TD colspan="2" style="padding: 8px; border: 1px solid rgb(221, 221, 221);">
              <img src="/journal_cover/0028-0836.jpg" width="80"/>
              Nature
              <span class="letpub-score">LetPub评分: 9.8</span>
            </TD>
          </TR>
```

After (keep the fake `/journal_cover/` img as an "unrelated" image to validate selector precision; add a new layui-form-item wrapper for the real cover above the table):
```html
          <!-- 期刊名字（含无关装饰图与评分，解析时忽略） -->
          <TR>
            <TD style="padding: 8px; border: 1px solid rgb(221, 221, 221); font-weight: bold;">期刊名字</TD>
            <TD colspan="2" style="padding: 8px; border: 1px solid rgb(221, 221, 221);">
              <!-- 无关装饰图：路径是 /journal_cover/（下划线），不匹配 img[src*=/cover/journal/] 选择器 -->
              <img src="/journal_cover/0028-0836.jpg" width="80"/>
              Nature
              <span class="letpub-score">LetPub评分: 9.8</span>
            </TD>
          </TR>
```

Then locate the opening `<table class="table_yjfx">` line and add immediately above it:

```html
      <!-- 期刊封面图（真实 LetPub 页面使用 layui 框架，封面图独立于基本信息表格） -->
      <div class="layui-form-item">
        <img src="https://media-cdn.oss-cn-hangzhou.aliyuncs.com/statics/images/comment_center/cover/journal/6054.jpg?ver=1775839295" title="NATURE">
      </div>
```

Verify the insertion point: the layui-form-item must be BEFORE the main `table.table_yjfx` element. Actually the order doesn't matter for the selector `selectFirst("img[src*=/cover/journal/]")` because only one element in the whole document matches (the other img path uses `/journal_cover/`).

- [ ] **Step 4.3: Write the failing test**

Append a new `@Nested` class to `LetPubDetailPageParserTest.java` (before the closing `}` of the outer class). No new imports needed.

```java
  @Nested
  @DisplayName("封面图片 URL 提取")
  class CoverImageTests {

    @Test
    @DisplayName("应从 layui-form-item 提取真实 Aliyun OSS 封面 URL")
    void shouldExtractCoverImageUrlFromLayuiFormItem() {
      assertThat(data.coverImageSourceUrl())
          .isEqualTo(
              "https://media-cdn.oss-cn-hangzhou.aliyuncs.com/statics/images/comment_center/cover/journal/6054.jpg?ver=1775839295");
    }

    @Test
    @DisplayName("页面无封面图元素时应返回 null")
    void shouldReturnNullWhenNoCoverImageElement() {
      String html = "<html><body><div>no cover here</div></body></html>";
      LetPubVenueData parsed = parser.parse(html, JOURNAL_ID);
      assertThat(parsed.coverImageSourceUrl()).isNull();
    }

    @Test
    @DisplayName("img 的 src 属性为空时应返回 null")
    void shouldReturnNullWhenSrcAttributeIsBlank() {
      String html =
          "<html><body>"
              + "<div class=\"layui-form-item\"><img src=\"\" title=\"NATURE\"></div>"
              + "</body></html>";
      LetPubVenueData parsed = parser.parse(html, JOURNAL_ID);
      assertThat(parsed.coverImageSourceUrl()).isNull();
    }

    @Test
    @DisplayName("只有非 /cover/journal/ 路径的图片时应返回 null")
    void shouldIgnoreUnrelatedImagesWithoutCoverJournalPath() {
      String html =
          "<html><body>"
              + "<img src=\"/journal_cover/0028-0836.jpg\"/>"
              + "<img src=\"/banner/header.png\"/>"
              + "</body></html>";
      LetPubVenueData parsed = parser.parse(html, JOURNAL_ID);
      assertThat(parsed.coverImageSourceUrl()).isNull();
    }
  }
```

- [ ] **Step 4.4: Run test to verify it fails**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests "com.patra.catalog.infra.adapter.integration.letpub.LetPubDetailPageParserTest\$CoverImageTests"`

Expected: 4 FAIL — `data.coverImageSourceUrl()` returns null because the parser does not yet call a cover-extracting method.

- [ ] **Step 4.5: Implement `parseCoverImageUrl` in LetPubDetailPageParser**

In `LetPubDetailPageParser.java`, add a new constant near the other `LABEL_*` constants (below `EDITION_PIONEER` around line 90):

```java
  /// 封面图 CSS 选择器：匹配 src 包含 `/cover/journal/` 路径的 img 元素。
  private static final String COVER_IMG_SELECTOR = "img[src*=/cover/journal/]";
```

Then add a new private method below `parseJournalName` (after the method ending around line 216):

```java
  /// 提取 LetPub 期刊封面图的原始 URL。
  ///
  /// LetPub 详情页使用 layui 框架，封面图以 `<div class="layui-form-item">` 包裹，
  /// src 指向 Aliyun OSS CDN 的绝对 URL（形如
  /// `https://media-cdn.oss-cn-hangzhou.aliyuncs.com/.../cover/journal/{id}.jpg`）。
  ///
  /// 使用内容选择器（路径包含 `/cover/journal/`）而非位置选择器，
  /// 这样即使 LetPub 调整布局也能继续识别封面元素。
  private void parseCoverImageUrl(Document doc, LetPubVenueData.LetPubVenueDataBuilder builder) {
    Element img = doc.selectFirst(COVER_IMG_SELECTOR);
    if (img == null) {
      return;
    }
    String src = img.attr("src");
    if (src.isBlank()) {
      return;
    }
    builder.coverImageSourceUrl(src);
  }
```

Then register the call in `parse()`. Change lines 104-114 from:
```java
    parseJournalName(doc, builder);
    parseBasicInfo(fieldMap, builder);
    parseJcrPartition(fieldMap, builder);
    parseCasPartition(fieldMap, builder);
    parseWarningList(fieldMap, builder);
    parseReviewSpeed(fieldMap, builder);
    parseAcceptanceRate(fieldMap, builder);
    parseApc(fieldMap, builder);
    parseIndexedIn(fieldMap, builder);
    parseFiveYearIf(fieldMap, builder);
    parseImpactFactorTrend(html, builder);
```
to:
```java
    parseJournalName(doc, builder);
    parseCoverImageUrl(doc, builder);
    parseBasicInfo(fieldMap, builder);
    parseJcrPartition(fieldMap, builder);
    parseCasPartition(fieldMap, builder);
    parseWarningList(fieldMap, builder);
    parseReviewSpeed(fieldMap, builder);
    parseAcceptanceRate(fieldMap, builder);
    parseApc(fieldMap, builder);
    parseIndexedIn(fieldMap, builder);
    parseFiveYearIf(fieldMap, builder);
    parseImpactFactorTrend(html, builder);
```

- [ ] **Step 4.6: Run CoverImageTests to verify they pass**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests "com.patra.catalog.infra.adapter.integration.letpub.LetPubDetailPageParserTest\$CoverImageTests"`

Expected: 4 tests PASS.

- [ ] **Step 4.7: Run the full LetPubDetailPageParserTest to catch fixture-related regressions**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests "com.patra.catalog.infra.adapter.integration.letpub.LetPubDetailPageParserTest"`

Expected: All previously passing tests still pass. The fixture changes only added a layui-form-item element outside the main table; the original TR structure remains intact.

- [ ] **Step 4.8: Commit**

```bash
git add patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/enrichment/LetPubVenueData.java \
        patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/integration/letpub/LetPubDetailPageParser.java \
        patra-catalog/patra-catalog-infra/src/test/resources/letpub/detail-page.html \
        patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/integration/letpub/LetPubDetailPageParserTest.java
git commit -m "feat(catalog-infra): LetPub 详情页解析器提取封面图原始 URL

- LetPubVenueData 新增 coverImageSourceUrl 字段承载 Aliyun OSS CDN URL
- LetPubDetailPageParser.parseCoverImageUrl 使用内容选择器
  img[src*=/cover/journal/] 识别封面元素，不依赖位置
- detail-page.html fixture 替换为真实 layui-form-item 结构
- CoverImageTests 新增 4 个单元测试（happy / 无元素 / 空 src / 无关图片）"
```

---

## Task 5: VenueCoverImageDownloadPort + Adapter 核心 happy path

**Files:**
- Create: `patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/storage/VenueCoverImageDownloadPort.java`
- Create: `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageProperties.java`
- Create: `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageDownloadAdapter.java`
- Create: `patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageDownloadAdapterTest.java`

- [ ] **Step 5.1: Create Port interface**

Create `patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/storage/VenueCoverImageDownloadPort.java`:

```java
package com.patra.catalog.domain.port.storage;

import com.patra.catalog.domain.exception.FileDownloadException;
import java.net.URI;

/// 期刊封面图下载与对象存储端口。
///
/// **职责**：从远端 URL 下载封面图后上传至对象存储，返回写入的对象键。
///
/// **实现约束**：
///
/// - Infra 层实现通过组合 `FileDownloadPort` + `ObjectStorageOperations` 完成
/// - 必须在临时文件清理上保证幂等性（try-finally）
/// - 下载失败、大小越界、上传失败统一抛出 `FileDownloadException` 并携带
///   `StandardErrorTrait` 语义
///
/// @author linqibin
/// @since 0.1.0
public interface VenueCoverImageDownloadPort {

  /// 下载远端封面图并上传到对象存储。
  ///
  /// @param sourceUrl LetPub 提供的封面图原始 URL（通常是 Aliyun OSS CDN URL）
  /// @param targetObjectKey 目标对象键（调用方决定，保证稳定性与唯一性）
  /// @return 实际写入的对象键（通常等于 `targetObjectKey`）
  /// @throws FileDownloadException 下载、大小校验或上传失败时抛出
  String downloadAndStore(URI sourceUrl, String targetObjectKey);
}
```

- [ ] **Step 5.2: Create Properties record**

Create `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageProperties.java`:

```java
package com.patra.catalog.infra.adapter.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// Venue 封面图对象存储桶配置。
///
/// 通过 `patra.catalog.object-storage.buckets.venue-cover` 覆盖默认桶名。
/// 单字段设计避免未来扩展时的破坏性迁移。
///
/// @param venueCover 封面图桶名（默认 `patra-catalog`）
/// @author linqibin
/// @since 0.1.0
@ConfigurationProperties("patra.catalog.object-storage.buckets")
public record VenueCoverImageProperties(@DefaultValue("patra-catalog") String venueCover) {}
```

- [ ] **Step 5.3: Write the failing test (happy path only)**

Create `patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageDownloadAdapterTest.java`:

```java
package com.patra.catalog.infra.adapter.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.port.source.FileDownloadPort;
import com.patra.catalog.domain.port.source.FileDownloadResult;
import com.patra.starter.objectstorage.ObjectStorageOperations;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// VenueCoverImageDownloadAdapter 单元测试。
///
/// 验证下载 + 上传 + 临时文件清理的完整生命周期，以及各种异常路径的语义特征。
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@DisplayName("VenueCoverImageDownloadAdapter 单元测试")
class VenueCoverImageDownloadAdapterTest {

  @Mock private FileDownloadPort fileDownloadPort;
  @Mock private ObjectStorageOperations objectStorage;

  private VenueCoverImageProperties properties;
  private VenueCoverImageDownloadAdapter adapter;

  @BeforeEach
  void setUp() {
    properties = new VenueCoverImageProperties("patra-catalog");
    adapter = new VenueCoverImageDownloadAdapter(fileDownloadPort, objectStorage, properties);
  }

  @Test
  @DisplayName("下载成功后应上传并返回对象键，并删除临时文件")
  void shouldDownloadAndUploadThenReturnObjectKey() throws IOException {
    // Given
    URI sourceUrl =
        URI.create(
            "https://media-cdn.oss-cn-hangzhou.aliyuncs.com/statics/images/comment_center/cover/journal/6054.jpg");
    String targetKey = "catalog/venue-cover/1.jpg";
    Path tempFile = Files.createTempFile("cover-test-", ".jpg");
    Files.writeString(tempFile, "fake-image-bytes");
    long fileSize = Files.size(tempFile);

    when(fileDownloadPort.download(sourceUrl))
        .thenReturn(FileDownloadResult.of(tempFile, fileSize));

    // When
    String result = adapter.downloadAndStore(sourceUrl, targetKey);

    // Then
    assertThat(result).isEqualTo(targetKey);
    assertThat(Files.exists(tempFile)).as("临时文件应在 finally 中被清理").isFalse();

    ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
    verify(objectStorage)
        .upload(eq("patra-catalog"), eq(targetKey), any(), metadataCaptor.capture());
    assertThat(metadataCaptor.getValue().getContentType()).isEqualTo("image/jpeg");
    assertThat(metadataCaptor.getValue().getContentLength()).isEqualTo(fileSize);
  }
}
```

- [ ] **Step 5.4: Verify test compiles (will fail because Adapter doesn't exist)**

Run: `./gradlew :patra-catalog:patra-catalog-infra:compileTestJava`

Expected: Compile error — `cannot find symbol VenueCoverImageDownloadAdapter`. This is expected; move on to 5.5.

- [ ] **Step 5.5: Implement VenueCoverImageDownloadAdapter**

Create `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageDownloadAdapter.java`:

```java
package com.patra.catalog.infra.adapter.storage;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.source.FileDownloadPort;
import com.patra.catalog.domain.port.source.FileDownloadResult;
import com.patra.catalog.domain.port.storage.VenueCoverImageDownloadPort;
import com.patra.common.error.trait.StandardErrorTrait;
import com.patra.starter.objectstorage.ObjectStorageOperations;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// LetPub 封面图下载适配器。
///
/// **实现策略**：
///
/// 1. 通过 `FileDownloadPort` 下载原图到本地临时目录
/// 2. 校验大小（上限 16 MiB）
/// 3. 以 `image/jpeg` Content-Type 上传到对象存储
/// 4. try-finally 清理临时文件（即使上传失败也必须清理）
///
/// **异常转译**：
///
/// - 响应为空 / 上传失败 → `FileDownloadException(DEP_UNAVAILABLE)`
/// - 大小超限 → `FileDownloadException(RULE_VIOLATION)`
/// - IOException → `FileDownloadException(DEP_UNAVAILABLE)` 包装
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueCoverImageDownloadAdapter implements VenueCoverImageDownloadPort {

  private static final long MAX_COVER_BYTES = 16L * 1024 * 1024;
  private static final String COVER_CONTENT_TYPE = "image/jpeg";

  private final FileDownloadPort fileDownloadPort;
  private final ObjectStorageOperations objectStorage;
  private final VenueCoverImageProperties properties;

  @Override
  public String downloadAndStore(URI sourceUrl, String targetObjectKey) {
    FileDownloadResult downloadResult = null;
    try {
      downloadResult = fileDownloadPort.download(sourceUrl);
      if (downloadResult.fileSize() <= 0) {
        throw new FileDownloadException(
            "封面响应为空: " + sourceUrl, StandardErrorTrait.DEP_UNAVAILABLE);
      }
      if (downloadResult.fileSize() > MAX_COVER_BYTES) {
        throw new FileDownloadException(
            "封面大小超限: "
                + downloadResult.fileSize()
                + " bytes (limit="
                + MAX_COVER_BYTES
                + ")",
            StandardErrorTrait.RULE_VIOLATION);
      }
      try (InputStream stream = Files.newInputStream(downloadResult.filePath())) {
        ObjectMetadata metadata =
            ObjectMetadata.builder()
                .contentType(COVER_CONTENT_TYPE)
                .contentLength(downloadResult.fileSize())
                .build();
        objectStorage.upload(properties.venueCover(), targetObjectKey, stream, metadata);
      }
      log.info(
          "封面上传成功: venueKey={} size={} source={}",
          targetObjectKey,
          downloadResult.fileSize(),
          sourceUrl);
      return targetObjectKey;
    } catch (FileDownloadException e) {
      throw e;
    } catch (IOException e) {
      throw new FileDownloadException(
          "读取临时封面文件失败: "
              + (downloadResult == null ? "null" : downloadResult.filePath()),
          e,
          StandardErrorTrait.DEP_UNAVAILABLE);
    } catch (RuntimeException e) {
      throw new FileDownloadException(
          "上传封面到对象存储失败: " + targetObjectKey, e, StandardErrorTrait.DEP_UNAVAILABLE);
    } finally {
      if (downloadResult != null) {
        try {
          Files.deleteIfExists(downloadResult.filePath());
        } catch (IOException cleanup) {
          log.warn("删除临时封面文件失败: {}", downloadResult.filePath(), cleanup);
        }
      }
    }
  }
}
```

- [ ] **Step 5.6: Register `VenueCoverImageProperties` in boot configuration**

Find the catalog boot config that enables `@ConfigurationPropertiesScan` or explicit `@EnableConfigurationProperties`:
```bash
grep -rn "ConfigurationPropertiesScan\|EnableConfigurationProperties" patra-catalog/patra-catalog-boot/src/main/java/
```

If `@ConfigurationPropertiesScan` is present and covers `com.patra.catalog.infra`, no change needed. Otherwise add `@EnableConfigurationProperties(VenueCoverImageProperties.class)` to an existing catalog configuration class; if none exists, create `patra-catalog/patra-catalog-boot/src/main/java/com/patra/catalog/boot/config/CatalogStorageConfiguration.java`:

```java
package com.patra.catalog.boot.config;

import com.patra.catalog.infra.adapter.storage.VenueCoverImageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/// Catalog 对象存储相关配置注册。
@Configuration
@EnableConfigurationProperties(VenueCoverImageProperties.class)
public class CatalogStorageConfiguration {}
```

- [ ] **Step 5.7: Run the happy-path adapter test**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests "com.patra.catalog.infra.adapter.storage.VenueCoverImageDownloadAdapterTest#shouldDownloadAndUploadThenReturnObjectKey"`

Expected: PASS.

- [ ] **Step 5.8: Commit**

```bash
git add patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/storage/VenueCoverImageDownloadPort.java \
        patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageProperties.java \
        patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageDownloadAdapter.java \
        patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageDownloadAdapterTest.java \
        patra-catalog/patra-catalog-boot/src/main/java/com/patra/catalog/boot/config/CatalogStorageConfiguration.java
git commit -m "feat(catalog): 新增 VenueCoverImageDownloadPort 与 Adapter 实现

- Domain 层定义 VenueCoverImageDownloadPort 抽象下载+存储能力
- Infra 层 VenueCoverImageDownloadAdapter 组合 FileDownloadPort +
  ObjectStorageOperations 完成下载上传，try-finally 清理临时文件
- VenueCoverImageProperties 桶配置（默认桶 patra-catalog）
- 异常统一为 FileDownloadException + StandardErrorTrait
- 单元测试覆盖 happy path（大小+ContentType+临时文件清理）"
```

---

## Task 6: Adapter 异常路径与清理验证

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageDownloadAdapterTest.java`

- [ ] **Step 6.1: Add failing tests for error paths**

Append to `VenueCoverImageDownloadAdapterTest.java` (inside the same class, after the happy path test):

```java
  @Test
  @DisplayName("下载的文件大小为 0 时应抛出 FileDownloadException(DEP_UNAVAILABLE)")
  void shouldThrowWhenDownloadedFileIsEmpty() throws IOException {
    // Given
    URI sourceUrl = URI.create("https://example.com/empty.jpg");
    String targetKey = "catalog/venue-cover/2.jpg";
    Path tempFile = Files.createTempFile("cover-empty-", ".jpg");
    when(fileDownloadPort.download(sourceUrl)).thenReturn(FileDownloadResult.of(tempFile, 0L));

    // When
    FileDownloadException ex =
        catchThrowableOfType(
            () -> adapter.downloadAndStore(sourceUrl, targetKey), FileDownloadException.class);

    // Then
    assertThat(ex).isNotNull();
    assertThat(ex.getMessage()).contains("封面响应为空");
    assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
    assertThat(Files.exists(tempFile)).as("临时文件仍需清理").isFalse();
  }

  @Test
  @DisplayName("下载的文件大小超过 16 MiB 时应抛出 FileDownloadException(RULE_VIOLATION)")
  void shouldThrowWhenDownloadedFileExceedsMaxBytes() throws IOException {
    // Given
    URI sourceUrl = URI.create("https://example.com/huge.jpg");
    String targetKey = "catalog/venue-cover/3.jpg";
    Path tempFile = Files.createTempFile("cover-huge-", ".jpg");
    long hugeSize = 17L * 1024 * 1024;
    when(fileDownloadPort.download(sourceUrl))
        .thenReturn(FileDownloadResult.of(tempFile, hugeSize));

    // When
    FileDownloadException ex =
        catchThrowableOfType(
            () -> adapter.downloadAndStore(sourceUrl, targetKey), FileDownloadException.class);

    // Then
    assertThat(ex).isNotNull();
    assertThat(ex.getMessage()).contains("封面大小超限");
    assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.RULE_VIOLATION);
    assertThat(Files.exists(tempFile)).as("临时文件仍需清理").isFalse();
  }

  @Test
  @DisplayName("ObjectStorage.upload 抛 RuntimeException 时应包装为 FileDownloadException 并清理临时文件")
  void shouldWrapUploadFailureAndDeleteTempFile() throws IOException {
    // Given
    URI sourceUrl = URI.create("https://example.com/nature.jpg");
    String targetKey = "catalog/venue-cover/4.jpg";
    Path tempFile = Files.createTempFile("cover-upload-fail-", ".jpg");
    Files.writeString(tempFile, "fake-bytes");
    long fileSize = Files.size(tempFile);

    when(fileDownloadPort.download(sourceUrl))
        .thenReturn(FileDownloadResult.of(tempFile, fileSize));
    org.mockito.Mockito.doThrow(new RuntimeException("MinIO unreachable"))
        .when(objectStorage)
        .upload(any(), any(), any(), any(ObjectMetadata.class));

    // When
    FileDownloadException ex =
        catchThrowableOfType(
            () -> adapter.downloadAndStore(sourceUrl, targetKey), FileDownloadException.class);

    // Then
    assertThat(ex).isNotNull();
    assertThat(ex.getMessage()).contains("上传封面到对象存储失败");
    assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
    assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
    assertThat(Files.exists(tempFile)).as("即使上传失败也需清理临时文件").isFalse();
  }
```

Add these imports at the top of the file (merge with existing imports):

```java
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.common.error.trait.StandardErrorTrait;
```

- [ ] **Step 6.2: Verify `FileDownloadException.getErrorTraits()` getter exists**

Run: `grep -rn "getErrorTraits" patra-common/patra-common-core/src/main/java/com/patra/common/error/`

Expected: method `getErrorTraits()` exists on the base class chain (likely via `HasErrorTraits`). If the getter is named differently (e.g., `errorTraits()` as a record accessor), update the assertions accordingly.

- [ ] **Step 6.3: Run new failing tests**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests "com.patra.catalog.infra.adapter.storage.VenueCoverImageDownloadAdapterTest"`

Expected: 4 tests total, all PASS. The 3 new error-path tests pass immediately because the adapter implementation from Task 5 already handles these paths. If any fail:
- empty file: verify `if (downloadResult.fileSize() <= 0)` comparison
- huge file: verify `MAX_COVER_BYTES = 16L * 1024 * 1024` constant
- upload failure: verify `catch (RuntimeException e)` wraps with DEP_UNAVAILABLE

- [ ] **Step 6.4: Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/adapter/storage/VenueCoverImageDownloadAdapterTest.java
git commit -m "test(catalog-infra): VenueCoverImageDownloadAdapter 异常路径覆盖

新增 3 个单元测试：
- 下载空文件 → DEP_UNAVAILABLE
- 文件超过 16 MiB → RULE_VIOLATION
- 上传失败 → DEP_UNAVAILABLE 包装
全部断言临时文件在 finally 中被清理。"
```

---

## Task 7: Processor 编排 — 封面下载失败隔离

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/batch/venue/letpub/LetPubVenueItemProcessor.java`
- Create: `patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/batch/venue/letpub/LetPubVenueItemProcessorTest.java`

**Design note:** Currently `LetPubVenueItemProcessor` returns `LetPubEnrichResult`, which is a container for JCR/CAS ratings. Cover image enrichment **mutates the `VenueEntity`** directly (setting `imageObjectKey`). Since the writer for this batch likely persists the `VenueEntity` separately from ratings, we need to check whether setting the entity field has downstream persistence. If the current flow discards entity mutations, we must either (a) add `imageObjectKey` to `LetPubEnrichResult` or (b) use a different writer path.

- [ ] **Step 7.1: Investigate the writer flow**

Run:
```bash
grep -rn "LetPubEnrichResult\|imageObjectKey\|imageUrl" patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/batch/venue/letpub/
```

And read `patra-catalog-infra/src/main/java/com/patra/catalog/infra/batch/venue/letpub/LetPubEnrichResult.java` plus the corresponding `*Writer.java`.

**Decision branch:**
- **(A)** If the writer flushes the `VenueEntity` (entity is a managed JPA entity still attached to the persistence context), then simply calling `item.setImageObjectKey(...)` in the processor is enough — the dirty check at commit will persist it.
- **(B)** If the writer only persists the ratings and does NOT flush `VenueEntity`, we must add `imageObjectKey` to `LetPubEnrichResult` and update the writer to apply the field update.

Document the chosen branch in a comment on the processor before proceeding.

- [ ] **Step 7.2: Write the failing test (failure isolation)**

Create `patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/batch/venue/letpub/LetPubVenueItemProcessorTest.java`:

```java
package com.patra.catalog.infra.batch.venue.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.domain.port.storage.VenueCoverImageDownloadPort;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import com.patra.common.error.trait.StandardErrorTrait;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@DisplayName("LetPubVenueItemProcessor 封面下载编排测试")
class LetPubVenueItemProcessorTest {

  @Mock private LetPubEnrichmentPort enrichmentPort;
  @Mock private LetPubDataMapper dataMapper;
  @Mock private VenueCoverImageDownloadPort coverImageDownloadPort;

  private LetPubVenueItemProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new LetPubVenueItemProcessor(enrichmentPort, dataMapper, coverImageDownloadPort);
  }

  private VenueEntity buildVenueEntity(Long id, String issnL, String existingKey) {
    VenueEntity entity = new VenueEntity();
    entity.setId(id);
    entity.setIssnL(issnL);
    entity.setVenueType("JOURNAL");
    entity.setTitle("Nature");
    entity.setImageObjectKey(existingKey);
    return entity;
  }

  private LetPubVenueData buildLetPubData(String journalId, String coverUrl) {
    return LetPubVenueData.builder()
        .letPubJournalId(journalId)
        .letPubName("Nature")
        .coverImageSourceUrl(coverUrl)
        .build();
  }

  @Test
  @DisplayName("封面下载失败时主流程应继续返回评级数据")
  void shouldContinueProcessingWhenCoverDownloadFails() throws Exception {
    // Given
    VenueEntity entity = buildVenueEntity(1L, "0028-0836", null);
    LetPubVenueData data =
        buildLetPubData("6054", "https://media-cdn.oss-cn-hangzhou.aliyuncs.com/cover/journal/6054.jpg");

    when(enrichmentPort.findByIssn("0028-0836")).thenReturn(Optional.of(data));
    when(dataMapper.mapToJcrRatings(any(), any(), any())).thenReturn(List.of());
    when(dataMapper.mapToCasRatings(any(), any(), any())).thenReturn(List.of());
    when(coverImageDownloadPort.downloadAndStore(any(), any()))
        .thenThrow(new FileDownloadException("MinIO down", StandardErrorTrait.DEP_UNAVAILABLE));

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).isNotNull();
    assertThat(entity.getImageObjectKey()).as("下载失败不应写入对象键").isNull();
    verify(coverImageDownloadPort).downloadAndStore(any(URI.class), eq("catalog/venue-cover/1.jpg"));
  }
}
```

Note: the test constructor call `new LetPubVenueItemProcessor(enrichmentPort, dataMapper, coverImageDownloadPort)` assumes a 3-arg constructor — to be introduced in Step 7.3.

- [ ] **Step 7.3: Verify test compiles (will fail because constructor doesn't match)**

Run: `./gradlew :patra-catalog:patra-catalog-infra:compileTestJava`

Expected: Compile error — constructor mismatch. Proceed to 7.4.

- [ ] **Step 7.4: Modify `LetPubVenueItemProcessor` to inject the port and add `downloadCoverIfNeeded`**

In `LetPubVenueItemProcessor.java`, change from:
```java
package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/// LetPub 期刊富化 Processor。
///
/// 从 {@link LetPubEnrichmentPort} 按 ISSN-L 查询 LetPub 数据，
/// 然后通过 {@link LetPubDataMapper} 拆解为 JCR + CAS 评级实体。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class LetPubVenueItemProcessor implements ItemProcessor<VenueEntity, LetPubEnrichResult> {

  private final LetPubEnrichmentPort enrichmentPort;
  private final LetPubDataMapper dataMapper;

  /// 处理单个 VenueEntity，通过 ISSN-L 查询 LetPub 数据并拆解为评级行。
  @Override
  public LetPubEnrichResult process(VenueEntity item) throws Exception {
    String issnL = item.getIssnL();
    if (issnL == null || issnL.isBlank()) {
      log.debug("Venue [id={}] ISSN-L 为空，跳过 LetPub 富化", item.getId());
      return null;
    }

    Optional<LetPubVenueData> result = enrichmentPort.findByIssn(issnL);
    if (result.isEmpty()) {
      log.debug("Venue [id={}, issn={}] 未在 LetPub 找到数据", item.getId(), issnL);
      return null;
    }

    LetPubVenueData data = result.get();
    Long venueId = item.getId();
    String sourceUrl = buildSourceUrl(data.letPubJournalId());

    List<JcrRatingEntity> jcrRatings = dataMapper.mapToJcrRatings(data, venueId, sourceUrl);
    List<CasRatingEntity> casRatings = dataMapper.mapToCasRatings(data, venueId, sourceUrl);

    int totalCount = jcrRatings.size() + casRatings.size();
    log.info(
        "Venue [id={}, issn={}] LetPub 富化成功，生成 {} 条评级（JCR:{}, CAS:{}）",
        venueId,
        issnL,
        totalCount,
        jcrRatings.size(),
        casRatings.size());

    return LetPubEnrichResult.of(venueId, jcrRatings, casRatings);
  }

  /// 构建 LetPub 详情页 URL，用于数据溯源。
  private String buildSourceUrl(String journalId) {
    if (journalId == null || journalId.isBlank()) {
      return null;
    }
    return "https://www.letpub.com.cn/index.php?journalid="
        + journalId
        + "&page=journalapp&view=detail";
  }
}
```

to:
```java
package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.domain.port.storage.VenueCoverImageDownloadPort;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/// LetPub 期刊富化 Processor。
///
/// **职责**：
///
/// 1. 按 ISSN-L 从 LetPub 查询详情页数据
/// 2. 通过 `LetPubDataMapper` 拆解为 JCR + CAS 评级实体
/// 3. 若尚未存储封面且 LetPub 返回了封面 URL，则通过
///    {@link VenueCoverImageDownloadPort} 下载并上传到对象存储
///
/// **失败隔离**：
///
/// 封面下载失败（`FileDownloadException` 或 URI 格式错误）**不阻断**主流程，
/// 仅记录 WARN 日志并跳过对象键更新，允许下次批次重试。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class LetPubVenueItemProcessor implements ItemProcessor<VenueEntity, LetPubEnrichResult> {

  private final LetPubEnrichmentPort enrichmentPort;
  private final LetPubDataMapper dataMapper;
  private final VenueCoverImageDownloadPort coverImageDownloadPort;

  /// 处理单个 VenueEntity。
  @Override
  public LetPubEnrichResult process(VenueEntity item) throws Exception {
    String issnL = item.getIssnL();
    if (issnL == null || issnL.isBlank()) {
      log.debug("Venue [id={}] ISSN-L 为空，跳过 LetPub 富化", item.getId());
      return null;
    }

    Optional<LetPubVenueData> result = enrichmentPort.findByIssn(issnL);
    if (result.isEmpty()) {
      log.debug("Venue [id={}, issn={}] 未在 LetPub 找到数据", item.getId(), issnL);
      return null;
    }

    LetPubVenueData data = result.get();
    Long venueId = item.getId();
    String sourceUrl = buildSourceUrl(data.letPubJournalId());

    List<JcrRatingEntity> jcrRatings = dataMapper.mapToJcrRatings(data, venueId, sourceUrl);
    List<CasRatingEntity> casRatings = dataMapper.mapToCasRatings(data, venueId, sourceUrl);

    downloadCoverIfNeeded(item, data);

    int totalCount = jcrRatings.size() + casRatings.size();
    log.info(
        "Venue [id={}, issn={}] LetPub 富化成功，生成 {} 条评级（JCR:{}, CAS:{}）",
        venueId,
        issnL,
        totalCount,
        jcrRatings.size(),
        casRatings.size());

    return LetPubEnrichResult.of(venueId, jcrRatings, casRatings);
  }

  /// 按需下载封面到对象存储。
  ///
  /// **跳过条件**：
  ///
  /// - 已存在 `imageObjectKey`（幂等，不重复下载）
  /// - LetPub 未返回封面 URL（数据缺失）
  ///
  /// **失败处理**：
  ///
  /// - `FileDownloadException` → WARN 日志，不阻断主流程
  /// - `IllegalArgumentException`（URI 格式错误）→ WARN 日志
  private void downloadCoverIfNeeded(VenueEntity item, LetPubVenueData data) {
    if (item.getImageObjectKey() != null) {
      return;
    }
    if (data.coverImageSourceUrl() == null) {
      return;
    }
    String stableKey = "catalog/venue-cover/" + item.getId() + ".jpg";
    try {
      URI sourceUri = URI.create(data.coverImageSourceUrl());
      String key = coverImageDownloadPort.downloadAndStore(sourceUri, stableKey);
      item.setImageObjectKey(key);
    } catch (FileDownloadException e) {
      log.warn(
          "venue 封面下载失败（主流程继续）: venueId={} sourceUrl={} trait={} reason={}",
          item.getId(),
          data.coverImageSourceUrl(),
          e.getErrorTraits(),
          e.getMessage());
    } catch (IllegalArgumentException e) {
      log.warn(
          "venue 封面 URL 格式非法: venueId={} sourceUrl={}",
          item.getId(),
          data.coverImageSourceUrl());
    }
  }

  /// 构建 LetPub 详情页 URL，用于数据溯源。
  private String buildSourceUrl(String journalId) {
    if (journalId == null || journalId.isBlank()) {
      return null;
    }
    return "https://www.letpub.com.cn/index.php?journalid="
        + journalId
        + "&page=journalapp&view=detail";
  }
}
```

- [ ] **Step 7.5: Run the failure-isolation test**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests "com.patra.catalog.infra.batch.venue.letpub.LetPubVenueItemProcessorTest#shouldContinueProcessingWhenCoverDownloadFails"`

Expected: PASS.

- [ ] **Step 7.6: Verify Spring wiring**

Since the processor is annotated-wired (currently `@RequiredArgsConstructor` only), check how it's instantiated in the batch config. Run:
```bash
grep -rn "new LetPubVenueItemProcessor\|LetPubVenueItemProcessor(" patra-catalog/patra-catalog-infra/src/main/java/
```

If there's a batch config with `new LetPubVenueItemProcessor(enrichmentPort, dataMapper)`, update it to pass the new `VenueCoverImageDownloadPort` bean as the third argument.

- [ ] **Step 7.7: Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/batch/venue/letpub/LetPubVenueItemProcessor.java \
        patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/batch/venue/letpub/LetPubVenueItemProcessorTest.java
# Also add any batch config file modified in Step 7.6
git commit -m "feat(catalog-infra): LetPubVenueItemProcessor 集成封面图下载

新增 downloadCoverIfNeeded 方法，注入 VenueCoverImageDownloadPort，
按 venueId 生成稳定对象键 catalog/venue-cover/{id}.jpg。
下载失败（FileDownloadException / URI 格式错误）仅打 WARN 不阻断主流程。
单元测试验证下载失败时评级数据仍正常返回。"
```

---

## Task 8: Processor 幂等 + URI 容错

**Files:**
- Modify: `patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/batch/venue/letpub/LetPubVenueItemProcessorTest.java`

- [ ] **Step 8.1: Add 幂等 + URI 容错 tests**

Append to `LetPubVenueItemProcessorTest.java` after `shouldContinueProcessingWhenCoverDownloadFails`:

```java
  @Test
  @DisplayName("已存在对象键时应跳过封面下载")
  void shouldSkipCoverDownloadWhenImageObjectKeyAlreadyExists() throws Exception {
    // Given
    VenueEntity entity = buildVenueEntity(2L, "1476-4687", "catalog/venue-cover/2.jpg");
    LetPubVenueData data =
        buildLetPubData("6054", "https://media-cdn.oss-cn-hangzhou.aliyuncs.com/cover/journal/6054.jpg");

    when(enrichmentPort.findByIssn("1476-4687")).thenReturn(Optional.of(data));
    when(dataMapper.mapToJcrRatings(any(), any(), any())).thenReturn(List.of());
    when(dataMapper.mapToCasRatings(any(), any(), any())).thenReturn(List.of());

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).isNotNull();
    assertThat(entity.getImageObjectKey()).isEqualTo("catalog/venue-cover/2.jpg");
    verify(coverImageDownloadPort, never()).downloadAndStore(any(), any());
  }

  @Test
  @DisplayName("LetPub 返回的封面 URL 格式非法时应跳过下载")
  void shouldContinueProcessingWhenCoverUrlIsMalformed() throws Exception {
    // Given
    VenueEntity entity = buildVenueEntity(3L, "0028-0836", null);
    LetPubVenueData data = buildLetPubData("6054", "not a valid url with spaces");

    when(enrichmentPort.findByIssn("0028-0836")).thenReturn(Optional.of(data));
    when(dataMapper.mapToJcrRatings(any(), any(), any())).thenReturn(List.of());
    when(dataMapper.mapToCasRatings(any(), any(), any())).thenReturn(List.of());

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).isNotNull();
    assertThat(entity.getImageObjectKey()).isNull();
    verify(coverImageDownloadPort, never()).downloadAndStore(any(), any());
  }

  @Test
  @DisplayName("LetPub 未返回封面 URL 时应跳过下载")
  void shouldSkipCoverDownloadWhenSourceUrlIsNull() throws Exception {
    // Given
    VenueEntity entity = buildVenueEntity(4L, "0028-0836", null);
    LetPubVenueData data = buildLetPubData("6054", null);

    when(enrichmentPort.findByIssn("0028-0836")).thenReturn(Optional.of(data));
    when(dataMapper.mapToJcrRatings(any(), any(), any())).thenReturn(List.of());
    when(dataMapper.mapToCasRatings(any(), any(), any())).thenReturn(List.of());

    // When
    LetPubEnrichResult result = processor.process(entity);

    // Then
    assertThat(result).isNotNull();
    assertThat(entity.getImageObjectKey()).isNull();
    verify(coverImageDownloadPort, never()).downloadAndStore(any(), any());
  }
```

Note on URI malformed case: `URI.create("not a valid url with spaces")` does throw `IllegalArgumentException` (space is not a legal URI character and `URI.create` is strict). If the test environment's JDK parses it leniently, replace the input with a string guaranteed to fail: `"http://:::::bad"`.

- [ ] **Step 8.2: Run the new tests**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test --tests "com.patra.catalog.infra.batch.venue.letpub.LetPubVenueItemProcessorTest"`

Expected: 4 tests total, all PASS. If the malformed-URI test fails because `URI.create` did not throw, adjust the input string per the note above.

- [ ] **Step 8.3: Commit**

```bash
git add patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/batch/venue/letpub/LetPubVenueItemProcessorTest.java
git commit -m "test(catalog-infra): LetPubVenueItemProcessor 幂等与 URI 容错覆盖

新增 3 个测试：
- 已存在 imageObjectKey 时跳过下载（幂等）
- LetPub 返回的 URL 格式非法时捕获 IllegalArgumentException 跳过
- LetPub 未返回 URL 时不调用下载端口"
```

---

## Task 9: 全模块回归验证

**Files:** 无修改，仅运行测试

- [ ] **Step 9.1: Run catalog domain tests**

Run: `./gradlew :patra-catalog:patra-catalog-domain:test`

Expected: All pass.

- [ ] **Step 9.2: Run catalog infra tests**

Run: `./gradlew :patra-catalog:patra-catalog-infra:test`

Expected: All pass. Special attention to:
- `LetPubDetailPageParserTest` — all nested classes green (fixture change shouldn't break anything outside cover tests)
- `VenueRepositoryAdapterIT` — TestContainers picks up new `image_object_key` column
- `LetPubEnrichmentAdapterIT` — any cover-related assertions still pass
- `VenueCoverImageDownloadAdapterTest` — 4 tests green
- `LetPubVenueItemProcessorTest` — 4 tests green

- [ ] **Step 9.3: Run catalog adapter tests**

Run: `./gradlew :patra-catalog:patra-catalog-adapter:test`

Expected: All pass. `VenueControllerIT` JsonPath asserts `imageObjectKey` instead of `imageUrl`.

- [ ] **Step 9.4: Build catalog boot module**

Run: `./gradlew :patra-catalog:patra-catalog-boot:compileJava`

Expected: PASS. This catches any missed field reference in boot-level configuration.

- [ ] **Step 9.5: Full catalog build**

Run: `./gradlew :patra-catalog:build -x test` (compile check all modules without re-running tests)

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9.6: Search for stale `imageUrl` references**

Run:
```bash
grep -rn "imageUrl\|image_url" patra-catalog/ --include="*.java" --include="*.sql" --include="*.html" --include="*.yml" --include="*.yaml"
```

Expected: No hits in production code. If the README or docs still reference `imageUrl`, update them inline (they are not covered by the plan but should be consistent).

- [ ] **Step 9.7: (If stale references found) Fix them**

For each hit:
- Java production code: rename to `imageObjectKey`
- Test code: rename to `imageObjectKey`
- Config YAML: probably none
- SQL comments: already handled in Task 2
- Markdown docs: update to `image_object_key` / `imageObjectKey`

Re-run Step 9.1-9.5 after fixing.

- [ ] **Step 9.8: Commit any cleanup (if needed)**

```bash
git add <fixed files>
git commit -m "chore(catalog): 清理残留的 imageUrl 引用"
```

---

## Task 10: 联调烟测（可选，需要本地 MinIO + MySQL）

**Files:** 无修改

> 此任务可在本地具备 Docker 环境时运行，用于验证真实 MinIO 上传链路。CI 环境中由 TestContainers 自动完成。

- [ ] **Step 10.1: 启动本地 MinIO + MySQL**

Run: `docker-compose -f patra-infra/docker-compose.yml up -d mysql minio` (or equivalent)

- [ ] **Step 10.2: 手动触发 LetPub enrichment job**

Start `patra-catalog-boot` with a test profile and invoke the LetPub 富化 job for a known venue (e.g., Nature, ISSN-L=0028-0836).

- [ ] **Step 10.3: 验证数据库字段**

Run SQL: `SELECT id, title, image_object_key FROM cat_venue WHERE issn_l = '0028-0836';`

Expected: `image_object_key` = `catalog/venue-cover/{id}.jpg`.

- [ ] **Step 10.4: 验证 MinIO 对象存在**

Open MinIO console (http://localhost:9001) → bucket `patra-catalog` → object `catalog/venue-cover/{id}.jpg` should exist with Content-Type `image/jpeg`.

- [ ] **Step 10.5: 幂等验证**

Re-run the job. Expected: no new upload (processor skip log); `image_object_key` unchanged.

- [ ] **Step 10.6: 失败隔离验证**

Stop MinIO container. Re-run job for a venue with `image_object_key IS NULL`. Expected: WARN log `venue 封面下载失败（主流程继续）`, evaluations (JCR/CAS) still persisted.

---

## Final Verification Checklist

After completing all 10 tasks, verify:

- [ ] `git log --oneline main..HEAD` shows 9 commits (Tasks 1-8 + optional cleanup commit from 9.8)
- [ ] `./gradlew :patra-catalog:build` PASS (including tests)
- [ ] No stale `imageUrl` / `image_url` references in `patra-catalog/` production code
- [ ] `VenueCoverImageDownloadAdapterTest` has 4 tests (happy + 3 error paths)
- [ ] `LetPubVenueItemProcessorTest` has 4 tests (failure isolation + idempotent + malformed URI + null URL)
- [ ] `LetPubDetailPageParserTest$CoverImageTests` has 4 tests (happy + 3 edge cases)
- [ ] `VenueAggregateImageKeyTest` has 2 tests (update + ignore null)
- [ ] Spec and plan documents exist under `docs/superpowers/specs/` and `docs/superpowers/plans/`

---

## Appendix A: Test Count Summary

| Test Class | New Tests | Modified Tests |
|------------|-----------|----------------|
| `VenueAggregateImageKeyTest` | 2 | 0 (new file) |
| `VenueSummaryReadModelTest` | 0 | 3 (field rename) |
| `VenueControllerIT` | 0 | 1 (JsonPath rename) |
| `LetPubDetailPageParserTest$CoverImageTests` | 4 | 0 (new nested) |
| `VenueCoverImageDownloadAdapterTest` | 4 | 0 (new file) |
| `LetPubVenueItemProcessorTest` | 4 | 0 (new file) |
| **Total new** | **14** | **4 modified** |

## Appendix B: Commit Sequence

```
Task 1: refactor(catalog-domain): rename VenueAggregate.imageUrl → imageObjectKey
Task 2: refactor(catalog-infra): rename image_url → image_object_key 列与 JPA 映射
Task 3: refactor(catalog): 读模型与 API 响应 imageUrl → imageObjectKey
Task 4: feat(catalog-infra): LetPub 详情页解析器提取封面图原始 URL
Task 5: feat(catalog): 新增 VenueCoverImageDownloadPort 与 Adapter 实现
Task 6: test(catalog-infra): VenueCoverImageDownloadAdapter 异常路径覆盖
Task 7: feat(catalog-infra): LetPubVenueItemProcessor 集成封面图下载
Task 8: test(catalog-infra): LetPubVenueItemProcessor 幂等与 URI 容错覆盖
Task 9 (optional): chore(catalog): 清理残留的 imageUrl 引用
```

---

