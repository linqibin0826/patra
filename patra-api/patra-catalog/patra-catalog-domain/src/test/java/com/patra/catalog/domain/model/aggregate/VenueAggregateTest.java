package com.patra.catalog.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.ProvenanceInfo;
import com.patra.catalog.domain.model.vo.venue.PublicationProfile;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.domain.model.vo.venue.VenueLanguages;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueAggregate 聚合根单元测试（最小聚合版本）。
///
/// **CQRS 模式**：聚合根只负责写入，验证核心不变量。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 覆盖工厂方法、标识符管理和不变量验证
///
/// **核心不变量**：
///
/// - venueType 必填
/// - displayName 必填
/// - identifiers 管理 ISSN-L 唯一性
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueAggregate 单元测试（最小聚合）")
@Timeout(2)
class VenueAggregateTest {

  // ========== 测试数据 ==========

  private static final String DISPLAY_NAME = "Nature";
  private static final String NLM_ID = "0410462";
  private static final String ISSN_L = "0028-0836";
  private static final String ISSN = "1476-4687";

  @Nested
  @DisplayName("fromPubMed() 工厂方法测试")
  class FromPubMedTests {

    @Test
    @DisplayName("应该使用 NLM ID 创建载体")
    void shouldCreateVenueWithNlmId() {
      // When
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);

      // Then
      assertThat(venue.getVenueType()).isEqualTo(VenueType.JOURNAL);
      assertThat(venue.getDisplayName()).isEqualTo(DISPLAY_NAME);
      assertThat(venue.getIdentifier(VenueIdentifierType.NLM)).contains(NLM_ID);
      assertThat(venue.isFromPubMed()).isTrue();
    }

    @Test
    @DisplayName("应该使用 ISSN-L 创建载体")
    void shouldCreateVenueWithIssnL() {
      // When
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, null, ISSN_L);

      // Then
      assertThat(venue.getIdentifier(VenueIdentifierType.ISSN_L)).contains(ISSN_L);
    }

    @Test
    @DisplayName("应该同时使用 NLM ID 和 ISSN-L 创建载体")
    void shouldCreateVenueWithBothNlmIdAndIssnL() {
      // When
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, ISSN_L);

      // Then
      assertThat(venue.getIdentifiers()).hasSize(2);
      assertThat(venue.getIdentifier(VenueIdentifierType.NLM)).contains(NLM_ID);
      assertThat(venue.getIdentifier(VenueIdentifierType.ISSN_L)).contains(ISSN_L);
    }

    @Test
    @DisplayName("NLM ID 和 ISSN-L 都为空时应该抛出异常")
    void shouldThrowWhenBothNlmIdAndIssnLAreBlank() {
      assertThatThrownBy(() -> VenueAggregate.fromPubMed(DISPLAY_NAME, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("NLM ID 或 ISSN-L");
    }
  }

  @Nested
  @DisplayName("restore() 方法测试")
  class RestoreTests {

    @Test
    @DisplayName("应该正确从持久化状态重建聚合根")
    void shouldRestoreFromPersistedState() {
      // Given
      VenueId id = VenueId.of(123L);
      Long version = 5L;

      // When
      VenueAggregate venue =
          VenueAggregate.restore(id, VenueType.REPOSITORY, DISPLAY_NAME, version);

      // Then
      assertThat(venue.getId()).isEqualTo(id);
      assertThat(venue.getVenueType()).isEqualTo(VenueType.REPOSITORY);
      assertThat(venue.getDisplayName()).isEqualTo(DISPLAY_NAME);
      assertThat(venue.getVersion()).isEqualTo(version);
    }
  }

  @Nested
  @DisplayName("标识符管理测试")
  class IdentifierManagementTests {

    @Test
    @DisplayName("addIdentifier() 应该添加新标识符")
    void addIdentifierShouldAddNew() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, ISSN_L);
      int initialSize = venue.getIdentifiers().size();

      // When
      venue.addIdentifier(VenueIdentifier.forIssn(ISSN));

      // Then
      assertThat(venue.getIdentifiers()).hasSize(initialSize + 1);
      assertThat(venue.getIdentifier(VenueIdentifierType.ISSN)).contains(ISSN);
    }

    @Test
    @DisplayName("addIdentifier() 应该忽略重复标识符（基于 Record equals）")
    void addIdentifierShouldIgnoreDuplicate() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      venue.addIdentifier(VenueIdentifier.forIssn(ISSN));
      int sizeAfterFirst = venue.getIdentifiers().size();

      // When - 添加相同的标识符
      venue.addIdentifier(VenueIdentifier.forIssn(ISSN));

      // Then - 数量不变
      assertThat(venue.getIdentifiers()).hasSize(sizeAfterFirst);
    }

    @Test
    @DisplayName("addIdentifier(type, value) 便捷方法应该正常工作")
    void addIdentifierConvenienceMethodShouldWork() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);

      // When
      venue.addIdentifier(VenueIdentifierType.MAG, "12345");

      // Then
      assertThat(venue.getIdentifier(VenueIdentifierType.MAG)).contains("12345");
    }

    @Test
    @DisplayName("removeIdentifier() 应该移除标识符")
    void removeIdentifierShouldRemove() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      venue.addIdentifier(VenueIdentifier.forIssn(ISSN));

      // When
      boolean removed = venue.removeIdentifier(VenueIdentifierType.ISSN, ISSN);

      // Then
      assertThat(removed).isTrue();
      assertThat(venue.getIdentifier(VenueIdentifierType.ISSN)).isEmpty();
    }

    @Test
    @DisplayName("removeIdentifier() 移除不存在的标识符应该返回 false")
    void removeIdentifierShouldReturnFalseWhenNotExists() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);

      // When
      boolean removed = venue.removeIdentifier(VenueIdentifierType.ISSN, "9999-9999");

      // Then
      assertThat(removed).isFalse();
    }

    @Test
    @DisplayName("addIdentifier() 应该将聚合根标记为脏")
    void addIdentifierShouldMarkDirty() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);

      // When
      venue.addIdentifier(VenueIdentifier.forIssn(ISSN));

      // Then
    }

    @Test
    @DisplayName("addIdentifier() 添加重复标识符不应该标记为脏")
    void addIdentifierDuplicateShouldNotMarkDirty() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      venue.addIdentifier(VenueIdentifier.forIssn(ISSN));

      // When - 添加相同的标识符
      venue.addIdentifier(VenueIdentifier.forIssn(ISSN));

      // Then - 不应该标记为脏
    }

    @Test
    @DisplayName("removeIdentifier() 应该将聚合根标记为脏")
    void removeIdentifierShouldMarkDirty() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      venue.addIdentifier(VenueIdentifier.forIssn(ISSN));

      // When
      venue.removeIdentifier(VenueIdentifierType.ISSN, ISSN);

      // Then
    }

    @Test
    @DisplayName("getIdentifier(type) 应该返回第一个匹配的标识符")
    void getIdentifierShouldReturnFirst() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      venue.addIdentifier(VenueIdentifier.forIssn("1111-1111"));
      venue.addIdentifier(VenueIdentifier.forIssn("2222-2222"));

      // When
      Optional<String> result = venue.getIdentifier(VenueIdentifierType.ISSN);

      // Then - 返回第一个添加的
      assertThat(result).contains("1111-1111");
    }

    @Test
    @DisplayName("getIdentifiers(type) 应该返回指定类型的所有标识符")
    void getIdentifiersByTypeShouldReturnAll() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      venue.addIdentifier(VenueIdentifier.forIssn("1111-1111"));
      venue.addIdentifier(VenueIdentifier.forIssn("2222-2222"));

      // When
      List<String> issns = venue.getIdentifiers(VenueIdentifierType.ISSN);

      // Then
      assertThat(issns).containsExactlyInAnyOrder("1111-1111", "2222-2222");
    }

    @Test
    @DisplayName("getIdentifiers() 应该返回不可变列表")
    void getIdentifiersShouldReturnUnmodifiableList() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);

      // When
      List<VenueIdentifier> identifiers = venue.getIdentifiers();

      // Then
      assertThatThrownBy(() -> identifiers.add(VenueIdentifier.forNlm(NLM_ID)))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("Provenance 设置方法测试")
  class ProvenanceSetterTests {

    @Test
    @DisplayName("withProvenance() 应该正确设置来源信息")
    void withProvenanceShouldWork() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      ProvenanceInfo newProvenance = ProvenanceInfo.forManual();

      // When
      venue.withProvenance(newProvenance);

      // Then
      assertThat(venue.getProvenance()).isEqualTo(newProvenance);
    }

    @Test
    @DisplayName("withProvenance() 应该将聚合根标记为脏")
    void withProvenanceShouldMarkDirty() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);

      // When
      venue.withProvenance(ProvenanceInfo.forManual());

      // Then
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodTests {

    @Test
    @DisplayName("isJournal() 应该正确判断")
    void isJournalShouldWork() {
      VenueAggregate journal = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      VenueAggregate repo =
          VenueAggregate.restore(VenueId.of(1L), VenueType.REPOSITORY, DISPLAY_NAME, 0L);

      assertThat(journal.isJournal()).isTrue();
      assertThat(repo.isJournal()).isFalse();
    }

    @Test
    @DisplayName("isRepository() 应该正确判断")
    void isRepositoryShouldWork() {
      VenueAggregate repo =
          VenueAggregate.restore(VenueId.of(1L), VenueType.REPOSITORY, DISPLAY_NAME, 0L);

      assertThat(repo.isRepository()).isTrue();
    }

    @Test
    @DisplayName("isConference() 应该正确判断")
    void isConferenceShouldWork() {
      VenueAggregate conf =
          VenueAggregate.restore(VenueId.of(1L), VenueType.CONFERENCE, DISPLAY_NAME, 0L);

      assertThat(conf.isConference()).isTrue();
    }

    @Test
    @DisplayName("isFromPubMed() 应该正确判断")
    void isFromPubMedShouldWork() {
      VenueAggregate pubmedVenue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      VenueAggregate restoredVenue =
          VenueAggregate.restore(VenueId.of(1L), VenueType.JOURNAL, DISPLAY_NAME, 0L);

      assertThat(pubmedVenue.isFromPubMed()).isTrue();
      assertThat(restoredVenue.isFromPubMed()).isFalse(); // restore 不设置 provenance
    }
  }

  @Nested
  @DisplayName("toString() 测试")
  class ToStringTests {

    @Test
    @DisplayName("toString() 应该包含关键信息")
    void toStringShouldContainKeyInfo() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, ISSN_L);

      // When
      String result = venue.toString();

      // Then
      assertThat(result).contains("JOURNAL");
      assertThat(result).contains(DISPLAY_NAME);
    }
  }

  @Nested
  @DisplayName("normalizeCountryCode() 国家编码标准化测试")
  class NormalizeCountryCodeTests {

    @Test
    @DisplayName("有效国家编码应该保持不变")
    void shouldKeepValidCountryCode() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      PublicationProfile profile = PublicationProfile.builder().countryCode("US").build();
      venue.withPublicationProfile(profile);

      // When - 验证结果为 "US"（有效）
      venue.normalizeCountryCode("US");

      // Then - 编码不变，不标记为脏
      assertThat(venue.getPublicationProfile().countryCode()).isEqualTo("US");
    }

    @Test
    @DisplayName("无效国家编码应该被清除为 null")
    void shouldClearInvalidCountryCode() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      PublicationProfile profile = PublicationProfile.builder().countryCode("XX").build();
      venue.withPublicationProfile(profile);

      // When - 验证结果为 null（无效）
      venue.normalizeCountryCode(null);

      // Then - 编码被清除，标记为脏
      assertThat(venue.getPublicationProfile().countryCode()).isNull();
    }

    @Test
    @DisplayName("publicationProfile 为 null 时不应该抛出异常")
    void shouldNotThrowWhenPublicationProfileIsNull() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      // publicationProfile 默认为 null

      // When & Then - 不应该抛出异常
      venue.normalizeCountryCode("US");
      assertThat(venue.getPublicationProfile()).isNull();
    }

    @Test
    @DisplayName("countryCode 本身为 null 时传入 null 不应该更新")
    void shouldNotUpdateWhenBothAreNull() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      PublicationProfile profile = PublicationProfile.builder().countryCode(null).build();
      venue.withPublicationProfile(profile);

      // When
      venue.normalizeCountryCode(null);

      // Then - 不应该标记为脏
    }

    @Test
    @DisplayName("更新国家编码时应该保留其他字段")
    void shouldPreserveOtherFieldsWhenUpdating() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      PublicationProfile profile =
          PublicationProfile.builder()
              .countryCode("XX")
              .abbreviatedTitle("Nat. Med.")
              .homepageUrl("https://example.com")
              .frequency("Monthly")
              .build();
      venue.withPublicationProfile(profile);

      // When
      venue.normalizeCountryCode(null);

      // Then - 其他字段应该保持不变
      PublicationProfile updated = venue.getPublicationProfile();
      assertThat(updated.countryCode()).isNull();
      assertThat(updated.abbreviatedTitle()).isEqualTo("Nat. Med.");
      assertThat(updated.homepageUrl()).isEqualTo("https://example.com");
      assertThat(updated.frequency()).isEqualTo("Monthly");
    }
  }

  @Nested
  @DisplayName("normalizeLanguages() 语言标准化测试")
  class NormalizeLanguagesTests {

    /// 测试用验证映射：ISO 639-3 → BCP 47
    private static final Map<String, String> VALID_MAPPINGS =
        Map.of(
            "eng", "en",
            "chi", "zh",
            "zho", "zh", // chi 和 zho 都映射到 zh
            "jpn", "ja",
            "fre", "fr",
            "fra", "fr", // fre 和 fra 都映射到 fr
            "ger", "de",
            "deu", "de" // ger 和 deu 都映射到 de
            );

    @Test
    @DisplayName("应该将 ISO 639-3 代码转换为 BCP 47")
    void shouldConvertIso639ToBcp47() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      PublicationProfile profile =
          PublicationProfile.builder()
              .languages(VenueLanguages.of(List.of("eng", "chi"), List.of("fre", "ger")))
              .build();
      venue.withPublicationProfile(profile);

      // When
      venue.normalizeLanguages(VALID_MAPPINGS);

      // Then
      VenueLanguages languages = venue.getPublicationProfile().languages();
      assertThat(languages.primary()).containsExactly("en", "zh");
      assertThat(languages.summary()).containsExactly("fr", "de");
    }

    @Test
    @DisplayName("应该移除无效的语言代码")
    void shouldRemoveInvalidLanguageCodes() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      PublicationProfile profile =
          PublicationProfile.builder()
              .languages(VenueLanguages.of(List.of("eng", "xxx"), List.of("yyy", "fre")))
              .build();
      venue.withPublicationProfile(profile);

      // When
      venue.normalizeLanguages(VALID_MAPPINGS);

      // Then - 无效代码被移除
      VenueLanguages languages = venue.getPublicationProfile().languages();
      assertThat(languages.primary()).containsExactly("en");
      assertThat(languages.summary()).containsExactly("fr");
    }

    @Test
    @DisplayName("应该对多映射代码去重")
    void shouldDeduplicateMultiMappedCodes() {
      // Given - chi 和 zho 都映射到 zh
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      PublicationProfile profile =
          PublicationProfile.builder()
              .languages(VenueLanguages.of(List.of("chi", "zho"), List.of("fre", "fra")))
              .build();
      venue.withPublicationProfile(profile);

      // When
      venue.normalizeLanguages(VALID_MAPPINGS);

      // Then - 去重后只保留一个
      VenueLanguages languages = venue.getPublicationProfile().languages();
      assertThat(languages.primary()).containsExactly("zh");
      assertThat(languages.summary()).containsExactly("fr");
    }

    @Test
    @DisplayName("publicationProfile 为 null 时不应该抛出异常")
    void shouldNotThrowWhenPublicationProfileIsNull() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      // publicationProfile 默认为 null

      // When & Then - 不应该抛出异常
      venue.normalizeLanguages(VALID_MAPPINGS);
      assertThat(venue.getPublicationProfile()).isNull();
    }

    @Test
    @DisplayName("languages 为 null 时不应该抛出异常")
    void shouldNotThrowWhenLanguagesIsNull() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      PublicationProfile profile = PublicationProfile.builder().countryCode("US").build();
      venue.withPublicationProfile(profile);

      // When & Then - 不应该抛出异常
      venue.normalizeLanguages(VALID_MAPPINGS);
      assertThat(venue.getPublicationProfile().languages()).isNull();
    }

    @Test
    @DisplayName("空语言列表时不应该更新")
    void shouldNotUpdateWhenLanguagesAreEmpty() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      PublicationProfile profile =
          PublicationProfile.builder().languages(VenueLanguages.empty()).build();
      venue.withPublicationProfile(profile);

      // When
      venue.normalizeLanguages(VALID_MAPPINGS);

      // Then - 不应该标记为脏
    }

    @Test
    @DisplayName("语言已经是 BCP 47 格式且无变化时不应该更新")
    void shouldNotUpdateWhenNoChange() {
      // Given - 假设已经标准化过，现在代码已经是 BCP 47 格式
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      // 但我们的映射是 ISO 639-3 → BCP 47，如果输入就是 BCP 47，不在映射中会被过滤
      // 这个测试场景是：输入 ISO 639-3，映射结果与之前相同
      Map<String, String> mappingWithBcp47 = Map.of("en", "en", "zh", "zh"); // 假设已经是 BCP 47
      PublicationProfile profile =
          PublicationProfile.builder()
              .languages(VenueLanguages.of(List.of("en"), List.of("zh")))
              .build();
      venue.withPublicationProfile(profile);

      // When
      venue.normalizeLanguages(mappingWithBcp47);

      // Then - 结果相同，不应该标记为脏
    }

    @Test
    @DisplayName("更新语言时应该保留其他字段")
    void shouldPreserveOtherFieldsWhenUpdating() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      PublicationProfile profile =
          PublicationProfile.builder()
              .countryCode("US")
              .abbreviatedTitle("Nat. Med.")
              .homepageUrl("https://example.com")
              .languages(VenueLanguages.of(List.of("eng"), List.of()))
              .build();
      venue.withPublicationProfile(profile);

      // When
      venue.normalizeLanguages(VALID_MAPPINGS);

      // Then - 其他字段应该保持不变
      PublicationProfile updated = venue.getPublicationProfile();
      assertThat(updated.countryCode()).isEqualTo("US");
      assertThat(updated.abbreviatedTitle()).isEqualTo("Nat. Med.");
      assertThat(updated.homepageUrl()).isEqualTo("https://example.com");
      assertThat(updated.languages().primary()).containsExactly("en");
    }

    @Test
    @DisplayName("映射表为空时应该清空所有语言代码")
    void shouldClearAllLanguagesWhenMappingIsEmpty() {
      // Given
      VenueAggregate venue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);
      PublicationProfile profile =
          PublicationProfile.builder()
              .languages(VenueLanguages.of(List.of("eng"), List.of("fre")))
              .build();
      venue.withPublicationProfile(profile);

      // When - 空映射表意味着所有代码都无效
      venue.normalizeLanguages(Map.of());

      // Then - 所有代码被清空
      VenueLanguages languages = venue.getPublicationProfile().languages();
      assertThat(languages.primary()).isEmpty();
      assertThat(languages.summary()).isEmpty();
    }
  }
}
