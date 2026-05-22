package dev.linqibin.patra.catalog.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.catalog.domain.model.enums.AuthorStatus;
import dev.linqibin.patra.catalog.domain.model.enums.DataSourceCode;
import dev.linqibin.patra.catalog.domain.model.vo.author.AuthorId;
import dev.linqibin.patra.catalog.domain.model.vo.author.AuthorNameVariant;
import dev.linqibin.patra.catalog.domain.model.vo.author.Orcid;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// AuthorAggregate 聚合根测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("AuthorAggregate 聚合根测试")
class AuthorAggregateTest {

  @Nested
  @DisplayName("fromPubMedComputed() 工厂方法测试")
  class FromPubMedComputedTests {

    @Test
    @DisplayName("应能创建新的作者聚合根")
    void shouldCreateNewAuthor() {
      // When
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");

      // Then
      assertThat(author.getNormalizedKey()).isEqualTo("Lu+Z");
      assertThat(author.getProvenanceCode()).isEqualTo(DataSourceCode.PUBMED);
      assertThat(author.getStatus()).isEqualTo(AuthorStatus.ACTIVE);
      assertThat(author.isTransient()).isTrue();
      assertThat(author.getNameVariants()).isEmpty();
      assertThat(author.getOrcids()).isEmpty();
    }

    @Test
    @DisplayName("应拒绝空的 normalizedKey")
    void shouldRejectEmptyNormalizedKey() {
      assertThatThrownBy(() -> AuthorAggregate.fromPubMedComputed(""))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> AuthorAggregate.fromPubMedComputed(null))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("restore() 重建方法测试")
  class RestoreTests {

    @Test
    @DisplayName("应能从持久化状态重建聚合根")
    void shouldRestoreFromPersistence() {
      // Given
      AuthorId id = AuthorId.of(12345L);
      String normalizedKey = "Smith+J";
      String displayName = "John Smith";
      DataSourceCode provenance = DataSourceCode.PUBMED;
      AuthorStatus status = AuthorStatus.ACTIVE;
      Instant lastSyncedAt = Instant.now();
      Long version = 5L;

      // When
      AuthorAggregate author =
          AuthorAggregate.restore(
              id, normalizedKey, displayName, provenance, status, lastSyncedAt, version);

      // Then
      assertThat(author.getId()).isEqualTo(id);
      assertThat(author.getNormalizedKey()).isEqualTo(normalizedKey);
      assertThat(author.getDisplayName()).isEqualTo(displayName);
      assertThat(author.getProvenanceCode()).isEqualTo(provenance);
      assertThat(author.getStatus()).isEqualTo(status);
      assertThat(author.getLastSyncedAt()).isEqualTo(lastSyncedAt);
      assertThat(author.getVersion()).isEqualTo(version);
      assertThat(author.isTransient()).isFalse();
    }
  }

  @Nested
  @DisplayName("名字变体管理测试")
  class NameVariantTests {

    @Test
    @DisplayName("应能添加名字变体")
    void shouldAddNameVariant() {
      // Given
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");
      AuthorNameVariant variant = AuthorNameVariant.parse("Lu,Zhiyong,Z");

      // When
      author.addNameVariant(variant);

      // Then
      assertThat(author.getNameVariants()).hasSize(1);
      assertThat(author.getNameVariants().getFirst()).isEqualTo(variant);
    }

    @Test
    @DisplayName("添加首个名字变体时应更新展示名称")
    void shouldUpdateDisplayNameOnFirstVariant() {
      // Given
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");
      AuthorNameVariant variant = AuthorNameVariant.parse("Lu,Zhiyong,Z");

      // When
      author.addNameVariant(variant);

      // Then
      assertThat(author.getDisplayName()).isEqualTo("Zhiyong Lu");
    }

    @Test
    @DisplayName("应能批量设置名字变体")
    void shouldSetNameVariants() {
      // Given
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");
      List<AuthorNameVariant> variants =
          List.of(AuthorNameVariant.parse("Lu,Zhiyong,Z"), AuthorNameVariant.parse("Lu,Z.Y.,ZY"));

      // When
      AuthorAggregate result = author.withNameVariants(variants);

      // Then
      assertThat(result).isSameAs(author); // 返回自身，支持链式调用
      assertThat(author.getNameVariants()).hasSize(2);
      assertThat(author.getDisplayName()).isEqualTo("Zhiyong Lu");
    }

    @Test
    @DisplayName("应拒绝添加 null 名字变体")
    void shouldRejectNullVariant() {
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");

      assertThatThrownBy(() -> author.addNameVariant(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("合并名字变体 - 应添加不重复的变体")
    void mergeNameVariantsFrom_shouldAddNonDuplicateVariants() {
      // Given: 两个作者，有部分重复的名字变体
      AuthorAggregate author1 = AuthorAggregate.fromPubMedComputed("SARMA+R");
      author1.withNameVariants(
          List.of(
              AuthorNameVariant.parse("Sarma,Rup,R"),
              AuthorNameVariant.parse("Sarma,Rup Jyoti,RJ")));

      AuthorAggregate author2 = AuthorAggregate.fromPubMedComputed("SARMA+R");
      author2.withNameVariants(
          List.of(
              AuthorNameVariant.parse("Sarma,Rup J,RJ"), // 新变体
              AuthorNameVariant.parse("Sarma,Rup,R"))); // 重复变体

      // When
      author1.mergeNameVariantsFrom(author2);

      // Then: 应该有 3 个变体（去除重复后）
      assertThat(author1.getNameVariants()).hasSize(3);
      assertThat(author1.getNameVariants())
          .extracting(AuthorNameVariant::fullString)
          .containsExactly("Sarma,Rup,R", "Sarma,Rup Jyoti,RJ", "Sarma,Rup J,RJ");
    }

    @Test
    @DisplayName("合并名字变体 - 应处理 null 作者")
    void mergeNameVariantsFrom_shouldHandleNullAuthor() {
      // Given
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("SARMA+R");
      author.withNameVariants(List.of(AuthorNameVariant.parse("Sarma,Rup,R")));

      // When: 合并 null 作者
      author.mergeNameVariantsFrom(null);

      // Then: 原始变体不变
      assertThat(author.getNameVariants()).hasSize(1);
    }

    @Test
    @DisplayName("合并名字变体 - 应处理空变体列表的作者")
    void mergeNameVariantsFrom_shouldHandleEmptyVariants() {
      // Given
      AuthorAggregate author1 = AuthorAggregate.fromPubMedComputed("SARMA+R");
      author1.withNameVariants(List.of(AuthorNameVariant.parse("Sarma,Rup,R")));

      AuthorAggregate author2 = AuthorAggregate.fromPubMedComputed("SARMA+R");
      // author2 没有名字变体

      // When
      author1.mergeNameVariantsFrom(author2);

      // Then: 原始变体不变
      assertThat(author1.getNameVariants()).hasSize(1);
    }

    @Test
    @DisplayName("合并名字变体 - 应进行大小写不敏感去重（匹配数据库 utf8mb4_0900_ai_ci 行为）")
    void mergeNameVariantsFrom_shouldBeCaseInsensitive() {
      // Given: 两个作者有大小写不同的名字变体
      AuthorAggregate author1 = AuthorAggregate.fromPubMedComputed("CAMPBELL+B");
      author1.withNameVariants(List.of(AuthorNameVariant.parse("Campbell,Bruce CV,BC")));

      AuthorAggregate author2 = AuthorAggregate.fromPubMedComputed("CAMPBELL+B");
      author2.withNameVariants(
          List.of(
              AuthorNameVariant.parse("Campbell,Bruce Cv,BC"), // 大小写不同 CV vs Cv
              AuthorNameVariant.parse("Campbell,Bruce C,BC"))); // 真正不同的变体

      // When
      author1.mergeNameVariantsFrom(author2);

      // Then: 只有 2 个变体（大小写不同的被视为重复）
      assertThat(author1.getNameVariants()).hasSize(2);
      assertThat(author1.getNameVariants())
          .extracting(AuthorNameVariant::fullString)
          .containsExactly("Campbell,Bruce CV,BC", "Campbell,Bruce C,BC");
    }
  }

  @Nested
  @DisplayName("ORCID 管理测试")
  class OrcidTests {

    @Test
    @DisplayName("应能添加 ORCID")
    void shouldAddOrcid() {
      // Given
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");
      Orcid orcid = Orcid.of("0000-0001-9998-916X");

      // When
      author.addOrcid(orcid);

      // Then
      assertThat(author.getOrcids()).hasSize(1);
      assertThat(author.hasOrcid()).isTrue();
      assertThat(author.getPrimaryOrcid()).isPresent();
      assertThat(author.getPrimaryOrcid().get()).isEqualTo(orcid);
    }

    @Test
    @DisplayName("应能批量设置 ORCID")
    void shouldSetOrcids() {
      // Given
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");
      List<Orcid> orcids =
          List.of(Orcid.of("0000-0001-9998-916X"), Orcid.of("0000-0002-1825-0097"));

      // When
      AuthorAggregate result = author.withOrcids(orcids);

      // Then
      assertThat(result).isSameAs(author);
      assertThat(author.getOrcids()).hasSize(2);
    }

    @Test
    @DisplayName("无 ORCID 时 hasOrcid 返回 false")
    void shouldReturnFalseWhenNoOrcid() {
      // Given
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");

      // Then
      assertThat(author.hasOrcid()).isFalse();
      assertThat(author.getPrimaryOrcid()).isEmpty();
    }
  }

  @Nested
  @DisplayName("状态转换测试")
  class StatusTransitionTests {

    @Test
    @DisplayName("应能标记为已合并状态")
    void shouldMarkAsMerged() {
      // Given
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");

      // When
      author.markAsMerged();

      // Then
      assertThat(author.getStatus()).isEqualTo(AuthorStatus.MERGED);
      assertThat(author.isActive()).isFalse();
    }

    @Test
    @DisplayName("应能标记为已停用状态")
    void shouldMarkAsInactive() {
      // Given
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");

      // When
      author.markAsInactive();

      // Then
      assertThat(author.getStatus()).isEqualTo(AuthorStatus.INACTIVE);
      assertThat(author.isActive()).isFalse();
    }

    @Test
    @DisplayName("新建作者应为活跃状态")
    void newAuthorShouldBeActive() {
      // Given
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");

      // Then
      assertThat(author.isActive()).isTrue();
      assertThat(author.getStatus()).isEqualTo(AuthorStatus.ACTIVE);
    }
  }

  @Nested
  @DisplayName("同步时间管理测试")
  class SyncTimeTests {

    @Test
    @DisplayName("应能更新同步时间")
    void shouldUpdateSyncTime() {
      // Given
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");
      Instant syncTime = Instant.now();

      // When
      author.updateLastSyncedAt(syncTime);

      // Then
      assertThat(author.getLastSyncedAt()).isEqualTo(syncTime);
    }

    @Test
    @DisplayName("应能使用当前时间更新同步时间")
    void shouldUpdateSyncTimeToNow() {
      // Given
      AuthorAggregate author = AuthorAggregate.fromPubMedComputed("Lu+Z");
      Instant before = Instant.now();

      // When
      author.markSynced();

      // Then
      assertThat(author.getLastSyncedAt()).isAfterOrEqualTo(before);
    }
  }
}
