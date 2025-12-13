package com.patra.catalog.domain.model.aggregate;

import static org.assertj.core.api.Assertions.*;

import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueInstanceAggregate 聚合根单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueInstanceAggregate 聚合根")
@Timeout(2)
class VenueInstanceAggregateTest {

  // ========== 测试数据常量 ==========

  private static final VenueId VENUE_ID = VenueId.of(123456789L);
  private static final String VOLUME = "45";
  private static final String ISSUE = "3";
  private static final Integer PUBLICATION_YEAR = 2024;
  private static final Integer PUBLICATION_MONTH = 3;
  private static final Integer PUBLICATION_DAY = 15;

  @Nested
  @DisplayName("期刊实例创建测试")
  class ForJournalTests {

    @Test
    @DisplayName("创建完整的期刊实例")
    void createJournalInstance_withAllFields_success() {
      // when
      VenueInstanceAggregate instance =
          VenueInstanceAggregate.forJournal(
              VENUE_ID, VOLUME, ISSUE, PUBLICATION_YEAR, PUBLICATION_MONTH, PUBLICATION_DAY);

      // then
      assertThat(instance.getId()).isNull();
      assertThat(instance.getVenueId()).isEqualTo(VENUE_ID);
      assertThat(instance.getVolume()).isEqualTo(VOLUME);
      assertThat(instance.getIssue()).isEqualTo(ISSUE);
      assertThat(instance.getPublicationYear()).isEqualTo(PUBLICATION_YEAR);
      assertThat(instance.getPublicationMonth()).isEqualTo(PUBLICATION_MONTH);
      assertThat(instance.getPublicationDay()).isEqualTo(PUBLICATION_DAY);
      assertThat(instance.isJournalInstance()).isTrue();
      assertThat(instance.isBookInstance()).isFalse();
      assertThat(instance.isConferenceInstance()).isFalse();
    }

    @Test
    @DisplayName("创建仅有卷号的期刊实例")
    void createJournalInstance_volumeOnly_success() {
      // when
      VenueInstanceAggregate instance =
          VenueInstanceAggregate.forJournal(VENUE_ID, VOLUME, null, PUBLICATION_YEAR, null, null);

      // then
      assertThat(instance.getVolume()).isEqualTo(VOLUME);
      assertThat(instance.getIssue()).isNull();
      assertThat(instance.isJournalInstance()).isTrue();
    }

    @Test
    @DisplayName("venueId 为空时抛出异常")
    void createJournalInstance_nullVenueId_throwsException() {
      assertThatThrownBy(
              () ->
                  VenueInstanceAggregate.forJournal(
                      null, VOLUME, ISSUE, PUBLICATION_YEAR, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("载体ID");
    }

    @Test
    @DisplayName("publicationYear 为空时抛出异常")
    void createJournalInstance_nullYear_throwsException() {
      assertThatThrownBy(
              () -> VenueInstanceAggregate.forJournal(VENUE_ID, VOLUME, ISSUE, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("出版年份");
    }

    @Test
    @DisplayName("publicationYear 超出范围时抛出异常")
    void createJournalInstance_yearOutOfRange_throwsException() {
      assertThatThrownBy(
              () -> VenueInstanceAggregate.forJournal(VENUE_ID, VOLUME, ISSUE, 1799, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("1800-2100");

      assertThatThrownBy(
              () -> VenueInstanceAggregate.forJournal(VENUE_ID, VOLUME, ISSUE, 2101, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("1800-2100");
    }

    @Test
    @DisplayName("publicationMonth 超出范围时抛出异常")
    void createJournalInstance_monthOutOfRange_throwsException() {
      assertThatThrownBy(
              () ->
                  VenueInstanceAggregate.forJournal(
                      VENUE_ID, VOLUME, ISSUE, PUBLICATION_YEAR, 13, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("1-12");
    }

    @Test
    @DisplayName("publicationDay 超出范围时抛出异常")
    void createJournalInstance_dayOutOfRange_throwsException() {
      assertThatThrownBy(
              () ->
                  VenueInstanceAggregate.forJournal(
                      VENUE_ID, VOLUME, ISSUE, PUBLICATION_YEAR, 1, 32))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("1-31");
    }
  }

  @Nested
  @DisplayName("书籍实例创建测试")
  class ForBookTests {

    @Test
    @DisplayName("创建书籍实例")
    void createBookInstance_success() {
      // when
      VenueInstanceAggregate instance =
          VenueInstanceAggregate.forBook(VENUE_ID, "2nd Edition", PUBLICATION_YEAR);

      // then
      assertThat(instance.getVenueId()).isEqualTo(VENUE_ID);
      assertThat(instance.getEdition()).isEqualTo("2nd Edition");
      assertThat(instance.getPublicationYear()).isEqualTo(PUBLICATION_YEAR);
      assertThat(instance.isBookInstance()).isTrue();
      assertThat(instance.isJournalInstance()).isFalse();
      assertThat(instance.isConferenceInstance()).isFalse();
    }
  }

  @Nested
  @DisplayName("会议实例创建测试")
  class ForConferenceTests {

    private static final String CONFERENCE_NAME = "AAAI 2024";
    private static final LocalDate START_DATE = LocalDate.of(2024, 2, 20);
    private static final LocalDate END_DATE = LocalDate.of(2024, 2, 27);
    private static final String LOCATION = "Vancouver, Canada";

    @Test
    @DisplayName("创建会议实例")
    void createConferenceInstance_success() {
      // when
      VenueInstanceAggregate instance =
          VenueInstanceAggregate.forConference(
              VENUE_ID, CONFERENCE_NAME, START_DATE, END_DATE, LOCATION, PUBLICATION_YEAR);

      // then
      assertThat(instance.getVenueId()).isEqualTo(VENUE_ID);
      assertThat(instance.getConferenceName()).isEqualTo(CONFERENCE_NAME);
      assertThat(instance.getConferenceStartDate()).isEqualTo(START_DATE);
      assertThat(instance.getConferenceEndDate()).isEqualTo(END_DATE);
      assertThat(instance.getConferenceLocation()).isEqualTo(LOCATION);
      assertThat(instance.isConferenceInstance()).isTrue();
      assertThat(instance.isJournalInstance()).isFalse();
      assertThat(instance.isBookInstance()).isFalse();
    }

    @Test
    @DisplayName("会议结束日期早于开始日期时抛出异常")
    void createConferenceInstance_endBeforeStart_throwsException() {
      LocalDate invalidEndDate = LocalDate.of(2024, 2, 19);

      assertThatThrownBy(
              () ->
                  VenueInstanceAggregate.forConference(
                      VENUE_ID,
                      CONFERENCE_NAME,
                      START_DATE,
                      invalidEndDate,
                      LOCATION,
                      PUBLICATION_YEAR))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("结束日期");
    }
  }

  @Nested
  @DisplayName("重建测试")
  class RestoreTests {

    @Test
    @DisplayName("从持久化状态重建聚合根")
    void restore_success() {
      // given
      VenueInstanceId id = VenueInstanceId.of(999L);
      Long version = 5L;

      // when
      VenueInstanceAggregate instance =
          VenueInstanceAggregate.restore(
              id,
              VENUE_ID,
              VOLUME,
              ISSUE,
              null,
              PUBLICATION_YEAR,
              PUBLICATION_MONTH,
              null,
              null,
              null,
              null,
              null,
              version);

      // then
      assertThat(instance.getId()).isEqualTo(id);
      assertThat(instance.getVenueId()).isEqualTo(VENUE_ID);
      assertThat(instance.getVolume()).isEqualTo(VOLUME);
      assertThat(instance.getIssue()).isEqualTo(ISSUE);
      assertThat(instance.getVersion()).isEqualTo(version);
    }
  }

  @Nested
  @DisplayName("便捷方法测试")
  class ConvenienceMethodTests {

    @Test
    @DisplayName("getVolumeIssueDescription 返回正确格式")
    void getVolumeIssueDescription_returnsCorrectFormat() {
      // given
      VenueInstanceAggregate instance =
          VenueInstanceAggregate.forJournal(VENUE_ID, "29", "3", PUBLICATION_YEAR, null, null);

      // then
      assertThat(instance.getVolumeIssueDescription()).isEqualTo("Vol.29, No.3");
    }

    @Test
    @DisplayName("getVolumeIssueDescription 仅有卷号时")
    void getVolumeIssueDescription_volumeOnly() {
      VenueInstanceAggregate instance =
          VenueInstanceAggregate.forJournal(VENUE_ID, "29", null, PUBLICATION_YEAR, null, null);

      assertThat(instance.getVolumeIssueDescription()).isEqualTo("Vol.29");
    }

    @Test
    @DisplayName("getVolumeIssueDescription 仅有期号时")
    void getVolumeIssueDescription_issueOnly() {
      VenueInstanceAggregate instance =
          VenueInstanceAggregate.forJournal(VENUE_ID, null, "3", PUBLICATION_YEAR, null, null);

      assertThat(instance.getVolumeIssueDescription()).isEqualTo("No.3");
    }

    @Test
    @DisplayName("getConferenceDateRange 返回正确格式")
    void getConferenceDateRange_returnsCorrectFormat() {
      // given
      LocalDate start = LocalDate.of(2024, 2, 20);
      LocalDate end = LocalDate.of(2024, 2, 27);
      VenueInstanceAggregate instance =
          VenueInstanceAggregate.forConference(
              VENUE_ID, "AAAI 2024", start, end, "Vancouver", PUBLICATION_YEAR);

      // then
      assertThat(instance.getConferenceDateRange()).isEqualTo("2024-02-20 至 2024-02-27");
    }
  }

  @Nested
  @DisplayName("元数据设置测试")
  class MetadataTests {

    @Test
    @DisplayName("设置元数据 JSON")
    void setInstanceMetadataJson_success() {
      // given
      VenueInstanceAggregate instance =
          VenueInstanceAggregate.forJournal(VENUE_ID, VOLUME, ISSUE, PUBLICATION_YEAR, null, null);
      String json = "{\"doi\":\"10.1234/test\"}";

      // when
      instance.setInstanceMetadataJson(json);

      // then
      assertThat(instance.getInstanceMetadataJson()).isEqualTo(json);
    }
  }

  @Nested
  @DisplayName("toString 测试")
  class ToStringTests {

    @Test
    @DisplayName("期刊实例 toString")
    void journalInstance_toString() {
      VenueInstanceAggregate instance =
          VenueInstanceAggregate.forJournal(VENUE_ID, "29", "3", 2024, null, null);

      assertThat(instance.toString()).contains("JournalInstance");
      assertThat(instance.toString()).contains("Vol.29");
      assertThat(instance.toString()).contains("2024");
    }

    @Test
    @DisplayName("书籍实例 toString")
    void bookInstance_toString() {
      VenueInstanceAggregate instance =
          VenueInstanceAggregate.forBook(VENUE_ID, "3rd Edition", 2024);

      assertThat(instance.toString()).contains("BookInstance");
      assertThat(instance.toString()).contains("3rd Edition");
    }

    @Test
    @DisplayName("会议实例 toString")
    void conferenceInstance_toString() {
      VenueInstanceAggregate instance =
          VenueInstanceAggregate.forConference(
              VENUE_ID,
              "AAAI 2024",
              LocalDate.of(2024, 2, 20),
              LocalDate.of(2024, 2, 27),
              "Vancouver",
              2024);

      assertThat(instance.toString()).contains("ConferenceInstance");
      assertThat(instance.toString()).contains("AAAI 2024");
    }
  }
}
