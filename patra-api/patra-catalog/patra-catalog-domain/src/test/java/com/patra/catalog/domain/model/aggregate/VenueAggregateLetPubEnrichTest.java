package com.patra.catalog.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.LetPubVenueData;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueAggregate LetPub 富化相关单元测试。
///
/// **测试策略**：
///
/// - `enrichLetPubData()`：null 不清除已有值，非 null 覆盖
/// - `withLetPubData()`：restore 场景下的 fluent setter
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueAggregate LetPub 富化测试")
@Timeout(2)
class VenueAggregateLetPubEnrichTest {

  private static final String TITLE = "Nature";
  private static final String NLM_ID = "0410462";
  private static final String ISSN_L = "0028-0836";

  /// 创建用于测试的最小聚合根。
  private VenueAggregate createTestVenue() {
    return VenueAggregate.fromPubMed(TITLE, null, NLM_ID, ISSN_L);
  }

  /// 创建用于 restore 测试的聚合根。
  private VenueAggregate restoreTestVenue() {
    return VenueAggregate.restore(VenueId.of(1L), VenueType.JOURNAL, TITLE, null, null, 0L);
  }

  /// 创建测试用的 LetPub 数据。
  private LetPubVenueData createTestLetPubData() {
    return LetPubVenueData.builder()
        .letPubJournalId("10000")
        .letPubName("Nature")
        .jifQuartile("Q1")
        .casMajorQuartile("1区")
        .casTopJournal(true)
        .indexedIn(List.of("SCI", "SCIE"))
        .build();
  }

  @Nested
  @DisplayName("enrichLetPubData() 测试")
  class EnrichLetPubDataTests {

    @Test
    @DisplayName("传入非 null 数据时应设置 letPubData")
    void shouldSetLetPubDataWhenNotNull() {
      // Given
      var venue = createTestVenue();
      var letPubData = createTestLetPubData();

      // When
      venue.enrichLetPubData(letPubData);

      // Then
      assertThat(venue.getLetPubData()).isNotNull();
      assertThat(venue.getLetPubData().letPubJournalId()).isEqualTo("10000");
      assertThat(venue.getLetPubData().jifQuartile()).isEqualTo("Q1");
      assertThat(venue.getLetPubData().casTopJournal()).isTrue();
    }

    @Test
    @DisplayName("传入 null 时不应清除已有的 letPubData")
    void shouldNotClearExistingDataWhenNull() {
      // Given
      var venue = createTestVenue();
      var letPubData = createTestLetPubData();
      venue.enrichLetPubData(letPubData);

      // When — 传入 null（模拟未查到数据的场景）
      venue.enrichLetPubData(null);

      // Then — 原有数据应保留
      assertThat(venue.getLetPubData()).isNotNull();
      assertThat(venue.getLetPubData().letPubJournalId()).isEqualTo("10000");
    }

    @Test
    @DisplayName("传入非 null 数据时应覆盖已有的 letPubData")
    void shouldOverwriteExistingData() {
      // Given
      var venue = createTestVenue();
      venue.enrichLetPubData(createTestLetPubData());

      var updatedData =
          LetPubVenueData.builder()
              .letPubJournalId("10000")
              .letPubName("Nature")
              .jifQuartile("Q1")
              .casMajorQuartile("1区")
              .casTopJournal(true)
              .reviewSpeedUser("平均6.0个月")
              .indexedIn(List.of("SCI", "SCIE", "PubMed"))
              .build();

      // When
      venue.enrichLetPubData(updatedData);

      // Then
      assertThat(venue.getLetPubData().reviewSpeedUser()).isEqualTo("平均6.0个月");
      assertThat(venue.getLetPubData().indexedIn()).hasSize(3);
    }

    @Test
    @DisplayName("初始状态下 letPubData 应为 null")
    void shouldBeNullInitially() {
      // Given & When
      var venue = createTestVenue();

      // Then
      assertThat(venue.getLetPubData()).isNull();
    }
  }

  @Nested
  @DisplayName("withLetPubData() 测试")
  class WithLetPubDataTests {

    @Test
    @DisplayName("应该设置 letPubData 并返回当前对象（fluent 风格）")
    void shouldSetAndReturnThis() {
      // Given
      var venue = restoreTestVenue();
      var letPubData = createTestLetPubData();

      // When
      var result = venue.withLetPubData(letPubData);

      // Then
      assertThat(result).isSameAs(venue);
      assertThat(venue.getLetPubData()).isEqualTo(letPubData);
    }

    @Test
    @DisplayName("restore 后通过 withLetPubData 恢复数据")
    void shouldRestoreViaWith() {
      // Given
      var letPubData = createTestLetPubData();

      // When — 模拟 JpaMapper.toAggregate 的 restore + withXxx 链
      var venue = restoreTestVenue().withLetPubData(letPubData);

      // Then
      assertThat(venue.getLetPubData()).isEqualTo(letPubData);
      assertThat(venue.getLetPubData().casTopJournal()).isTrue();
    }
  }
}
