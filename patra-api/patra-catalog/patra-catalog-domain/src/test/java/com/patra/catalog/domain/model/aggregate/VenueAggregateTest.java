package com.patra.catalog.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.ProvenanceInfo;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import java.util.List;
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
/// - 不测试非核心属性（已移至 VenueDetail）
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

  private static final String OPENALEX_ID = "S1234567890";
  private static final String DISPLAY_NAME = "Nature";
  private static final String NLM_ID = "0410462";
  private static final String ISSN_L = "0028-0836";
  private static final String ISSN = "1476-4687";

  @Nested
  @DisplayName("fromOpenAlex() 工厂方法测试")
  class FromOpenAlexTests {

    @Test
    @DisplayName("应该正确创建 OpenAlex 来源的载体")
    void shouldCreateVenueFromOpenAlex() {
      // When
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);

      // Then
      assertThat(venue.getId()).isNull(); // 新建时无 ID
      assertThat(venue.getVenueType()).isEqualTo(VenueType.JOURNAL);
      assertThat(venue.getDisplayName()).isEqualTo(DISPLAY_NAME);

      // 应该自动添加 OpenAlex 标识符
      assertThat(venue.getIdentifiers()).hasSize(1);
      assertThat(venue.getIdentifier(VenueIdentifierType.OPENALEX)).contains(OPENALEX_ID);

      // 应该设置来源信息
      assertThat(venue.getProvenance()).isNotNull();
      assertThat(venue.isFromOpenAlex()).isTrue();
    }

    @Test
    @DisplayName("OpenAlex ID 为空时应该抛出异常")
    void shouldThrowWhenOpenAlexIdIsBlank() {
      assertThatThrownBy(() -> VenueAggregate.fromOpenAlex("", VenueType.JOURNAL, DISPLAY_NAME))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("OpenAlex ID 不能为空");

      assertThatThrownBy(() -> VenueAggregate.fromOpenAlex(null, VenueType.JOURNAL, DISPLAY_NAME))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("载体类型为 null 时应该抛出异常")
    void shouldThrowWhenVenueTypeIsNull() {
      assertThatThrownBy(() -> VenueAggregate.fromOpenAlex(OPENALEX_ID, null, DISPLAY_NAME))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("载体类型不能为空");
    }

    @Test
    @DisplayName("显示名称为空时应该抛出异常")
    void shouldThrowWhenDisplayNameIsBlank() {
      assertThatThrownBy(() -> VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, ""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("显示名称不能为空");
    }
  }

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
      Long id = 123L;
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
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);
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
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);
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
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);

      // When
      venue.addIdentifier(VenueIdentifierType.MAG, "12345");

      // Then
      assertThat(venue.getIdentifier(VenueIdentifierType.MAG)).contains("12345");
    }

    @Test
    @DisplayName("removeIdentifier() 应该移除标识符")
    void removeIdentifierShouldRemove() {
      // Given
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);
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
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);
      venue.clearDirty();

      // When
      boolean removed = venue.removeIdentifier(VenueIdentifierType.ISSN, "9999-9999");

      // Then
      assertThat(removed).isFalse();
      assertThat(venue.isDirty()).isFalse();
    }

    @Test
    @DisplayName("addIdentifier() 应该将聚合根标记为脏")
    void addIdentifierShouldMarkDirty() {
      // Given
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);
      venue.clearDirty();
      assertThat(venue.isDirty()).isFalse();

      // When
      venue.addIdentifier(VenueIdentifier.forIssn(ISSN));

      // Then
      assertThat(venue.isDirty()).isTrue();
    }

    @Test
    @DisplayName("addIdentifier() 添加重复标识符不应该标记为脏")
    void addIdentifierDuplicateShouldNotMarkDirty() {
      // Given
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);
      venue.addIdentifier(VenueIdentifier.forIssn(ISSN));
      venue.clearDirty();
      assertThat(venue.isDirty()).isFalse();

      // When - 添加相同的标识符
      venue.addIdentifier(VenueIdentifier.forIssn(ISSN));

      // Then - 不应该标记为脏
      assertThat(venue.isDirty()).isFalse();
    }

    @Test
    @DisplayName("removeIdentifier() 应该将聚合根标记为脏")
    void removeIdentifierShouldMarkDirty() {
      // Given
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);
      venue.addIdentifier(VenueIdentifier.forIssn(ISSN));
      venue.clearDirty();
      assertThat(venue.isDirty()).isFalse();

      // When
      venue.removeIdentifier(VenueIdentifierType.ISSN, ISSN);

      // Then
      assertThat(venue.isDirty()).isTrue();
    }

    @Test
    @DisplayName("getIdentifier(type) 应该返回第一个匹配的标识符")
    void getIdentifierShouldReturnFirst() {
      // Given
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);
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
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);
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
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);

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
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);
      ProvenanceInfo newProvenance = ProvenanceInfo.forPubMed();

      // When
      venue.withProvenance(newProvenance);

      // Then
      assertThat(venue.getProvenance()).isEqualTo(newProvenance);
      assertThat(venue.isFromPubMed()).isTrue();
    }

    @Test
    @DisplayName("withProvenance() 应该将聚合根标记为脏")
    void withProvenanceShouldMarkDirty() {
      // Given
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);
      venue.clearDirty();
      assertThat(venue.isDirty()).isFalse();

      // When
      venue.withProvenance(ProvenanceInfo.forPubMed());

      // Then
      assertThat(venue.isDirty()).isTrue();
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodTests {

    @Test
    @DisplayName("isJournal() 应该正确判断")
    void isJournalShouldWork() {
      VenueAggregate journal =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);
      VenueAggregate repo =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.REPOSITORY, DISPLAY_NAME);

      assertThat(journal.isJournal()).isTrue();
      assertThat(repo.isJournal()).isFalse();
    }

    @Test
    @DisplayName("isRepository() 应该正确判断")
    void isRepositoryShouldWork() {
      VenueAggregate repo =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.REPOSITORY, DISPLAY_NAME);

      assertThat(repo.isRepository()).isTrue();
    }

    @Test
    @DisplayName("isConference() 应该正确判断")
    void isConferenceShouldWork() {
      VenueAggregate conf =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.CONFERENCE, DISPLAY_NAME);

      assertThat(conf.isConference()).isTrue();
    }

    @Test
    @DisplayName("isFromOpenAlex() 和 isFromPubMed() 应该正确判断")
    void provenanceMethodsShouldWork() {
      VenueAggregate openalexVenue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);
      VenueAggregate pubmedVenue = VenueAggregate.fromPubMed(DISPLAY_NAME, NLM_ID, null);

      assertThat(openalexVenue.isFromOpenAlex()).isTrue();
      assertThat(openalexVenue.isFromPubMed()).isFalse();

      assertThat(pubmedVenue.isFromPubMed()).isTrue();
      assertThat(pubmedVenue.isFromOpenAlex()).isFalse();
    }
  }

  @Nested
  @DisplayName("toString() 测试")
  class ToStringTests {

    @Test
    @DisplayName("toString() 应该包含关键信息")
    void toStringShouldContainKeyInfo() {
      // Given
      VenueAggregate venue =
          VenueAggregate.fromOpenAlex(OPENALEX_ID, VenueType.JOURNAL, DISPLAY_NAME);

      // When
      String result = venue.toString();

      // Then
      assertThat(result).contains("JOURNAL");
      assertThat(result).contains(DISPLAY_NAME);
    }
  }
}
