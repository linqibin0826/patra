package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// ProvenanceInfo 值对象单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 覆盖工厂方法、判断方法和行为方法
/// - 验证值对象的不可变性和相等性
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ProvenanceInfo 值对象测试")
@Timeout(2)
class ProvenanceInfoTest {

  @Nested
  @DisplayName("构造与工厂方法测试")
  class ConstructorAndFactoryTests {

    @Test
    @DisplayName("of() 应该创建包含代码和时间的完整信息")
    void of_shouldCreateCompleteInfo() {
      // Given
      ProvenanceCode code = ProvenanceCode.OPENALEX;
      Instant syncedAt = Instant.now();

      // When
      ProvenanceInfo info = ProvenanceInfo.of(code, syncedAt);

      // Then
      assertThat(info.code()).isEqualTo(ProvenanceCode.OPENALEX);
      assertThat(info.lastSyncedAt()).isEqualTo(syncedAt);
    }

    @Test
    @DisplayName("of() 应该允许 lastSyncedAt 为 null")
    void of_shouldAllowNullLastSyncedAt() {
      // When
      ProvenanceInfo info = ProvenanceInfo.of(ProvenanceCode.CROSSREF, null);

      // Then
      assertThat(info.code()).isEqualTo(ProvenanceCode.CROSSREF);
      assertThat(info.lastSyncedAt()).isNull();
    }

    @Test
    @DisplayName("ofCode() 应该创建仅包含代码的信息（时间为 null）")
    void ofCode_shouldCreateInfoWithNullTime() {
      // When
      ProvenanceInfo info = ProvenanceInfo.ofCode(ProvenanceCode.DOAJ);

      // Then
      assertThat(info.code()).isEqualTo(ProvenanceCode.DOAJ);
      assertThat(info.lastSyncedAt()).isNull();
    }

    @Test
    @DisplayName("forPubMed() 应该创建 PubMed 来源信息")
    void forPubMed_shouldCreatePubMedInfo() {
      // Given
      Instant before = Instant.now();

      // When
      ProvenanceInfo info = ProvenanceInfo.forPubMed();

      // Then
      Instant after = Instant.now();
      assertThat(info.code()).isEqualTo(ProvenanceCode.PUBMED);
      assertThat(info.lastSyncedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("forManual() 应该创建手动录入来源信息")
    void forManual_shouldCreateManualInfo() {
      // Given
      Instant before = Instant.now();

      // When
      ProvenanceInfo info = ProvenanceInfo.forManual();

      // Then
      Instant after = Instant.now();
      assertThat(info.code()).isEqualTo(ProvenanceCode.MANUAL);
      assertThat(info.lastSyncedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("code 为 null 时应该抛出 IllegalArgumentException")
    void constructor_shouldThrowWhenCodeIsNull() {
      assertThatThrownBy(() -> ProvenanceInfo.of(null, Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("来源代码不能为空");
    }

    @Test
    @DisplayName("ofCode() 的 code 为 null 时应该抛出异常")
    void ofCode_shouldThrowWhenCodeIsNull() {
      assertThatThrownBy(() -> ProvenanceInfo.ofCode(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("来源代码不能为空");
    }
  }

  @Nested
  @DisplayName("判断方法测试")
  class PredicateTests {

    @Test
    @DisplayName("isFromPubMed() 对 PubMed 来源应该返回 true")
    void isFromPubMed_shouldReturnTrueForPubMed() {
      // Given
      ProvenanceInfo info = ProvenanceInfo.forPubMed();

      // When & Then
      assertThat(info.isFromPubMed()).isTrue();
      assertThat(info.isManual()).isFalse();
    }

    @Test
    @DisplayName("isManual() 对手动录入应该返回 true")
    void isManual_shouldReturnTrueForManual() {
      // Given
      ProvenanceInfo info = ProvenanceInfo.forManual();

      // When & Then
      assertThat(info.isManual()).isTrue();
      assertThat(info.isFromPubMed()).isFalse();
    }

    @Test
    @DisplayName("其他来源代码的判断方法应该都返回 false")
    void predicates_shouldReturnFalseForOtherCodes() {
      // Given
      ProvenanceInfo info = ProvenanceInfo.ofCode(ProvenanceCode.CROSSREF);

      // When & Then
      assertThat(info.isFromPubMed()).isFalse();
      assertThat(info.isManual()).isFalse();
    }
  }

  @Nested
  @DisplayName("行为方法测试")
  class BehaviorTests {

    @Test
    @DisplayName("withSyncedNow() 应该更新时间并保持 code 不变")
    void withSyncedNow_shouldUpdateTimeAndPreserveCode() {
      // Given
      Instant oldTime = Instant.now().minus(1, ChronoUnit.HOURS);
      ProvenanceInfo original = ProvenanceInfo.of(ProvenanceCode.OPENALEX, oldTime);

      // When
      Instant before = Instant.now();
      ProvenanceInfo updated = original.withSyncedNow();
      Instant after = Instant.now();

      // Then
      assertThat(updated.code()).isEqualTo(ProvenanceCode.OPENALEX);
      assertThat(updated.lastSyncedAt()).isBetween(before, after);
      assertThat(updated.lastSyncedAt()).isAfter(oldTime);
    }

    @Test
    @DisplayName("withSyncedNow() 应该返回新对象（不可变性）")
    void withSyncedNow_shouldReturnNewInstance() {
      // Given
      ProvenanceInfo original = ProvenanceInfo.ofCode(ProvenanceCode.PUBMED);

      // When
      ProvenanceInfo updated = original.withSyncedNow();

      // Then
      assertThat(updated).isNotSameAs(original);
      assertThat(original.lastSyncedAt()).isNull(); // 原对象不变
      assertThat(updated.lastSyncedAt()).isNotNull();
    }

    @Test
    @DisplayName("codeAsString() 应该返回正确的代码字符串")
    void codeAsString_shouldReturnCorrectString() {
      // Given & When & Then
      assertThat(ProvenanceInfo.forPubMed().codeAsString()).isEqualTo("PUBMED");
      assertThat(ProvenanceInfo.forManual().codeAsString()).isEqualTo("MANUAL");
      assertThat(ProvenanceInfo.ofCode(ProvenanceCode.CROSSREF).codeAsString())
          .isEqualTo("CROSSREF");
      assertThat(ProvenanceInfo.ofCode(ProvenanceCode.DOAJ).codeAsString()).isEqualTo("DOAJ");
    }
  }

  @Nested
  @DisplayName("Record 特性测试")
  class RecordFeaturesTests {

    @Test
    @DisplayName("相同 code 和 lastSyncedAt 的对象应该相等")
    void equals_shouldReturnTrueForSameValues() {
      // Given
      Instant time = Instant.now();
      ProvenanceInfo info1 = ProvenanceInfo.of(ProvenanceCode.OPENALEX, time);
      ProvenanceInfo info2 = ProvenanceInfo.of(ProvenanceCode.OPENALEX, time);

      // When & Then
      assertThat(info1).isEqualTo(info2);
      assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
    }

    @Test
    @DisplayName("不同 code 的对象应该不相等")
    void equals_shouldReturnFalseForDifferentCodes() {
      // Given
      Instant time = Instant.now();
      ProvenanceInfo info1 = ProvenanceInfo.of(ProvenanceCode.OPENALEX, time);
      ProvenanceInfo info2 = ProvenanceInfo.of(ProvenanceCode.PUBMED, time);

      // When & Then
      assertThat(info1).isNotEqualTo(info2);
    }

    @Test
    @DisplayName("不同 lastSyncedAt 的对象应该不相等")
    void equals_shouldReturnFalseForDifferentTimes() {
      // Given
      ProvenanceInfo info1 = ProvenanceInfo.of(ProvenanceCode.OPENALEX, Instant.now());
      ProvenanceInfo info2 =
          ProvenanceInfo.of(ProvenanceCode.OPENALEX, Instant.now().plus(1, ChronoUnit.SECONDS));

      // When & Then
      assertThat(info1).isNotEqualTo(info2);
    }

    @Test
    @DisplayName("toString() 应该包含关键信息")
    void toString_shouldContainKeyInfo() {
      // Given
      ProvenanceInfo info = ProvenanceInfo.forPubMed();

      // When
      String result = info.toString();

      // Then
      assertThat(result).contains("ProvenanceInfo");
      assertThat(result).contains("PUBMED");
    }
  }
}
