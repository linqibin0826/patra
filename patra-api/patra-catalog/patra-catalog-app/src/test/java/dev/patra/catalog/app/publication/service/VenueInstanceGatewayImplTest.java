package dev.patra.catalog.app.publication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.kernel.model.vo.VenueId;
import dev.patra.catalog.domain.publication.model.aggregate.VenueInstanceAggregate;
import dev.patra.catalog.domain.publication.model.vo.JournalInstanceParams;
import dev.patra.catalog.domain.publication.port.repository.VenueInstanceRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// VenueInstanceGatewayImpl 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueInstanceGatewayImpl")
@ExtendWith(MockitoExtension.class)
class VenueInstanceGatewayImplTest {

  @Mock private VenueInstanceRepository venueInstanceRepository;

  @InjectMocks private VenueInstanceGatewayImpl venueInstanceGatewayImpl;

  private static final VenueId VENUE_ID = VenueId.of(1L);
  private static final String VOLUME = "10";
  private static final String ISSUE = "2";
  private static final Integer YEAR = 2024;
  private static final Integer MONTH = 6;
  private static final Integer DAY = 15;

  @Nested
  @DisplayName("findOrCreateJournalInstance()")
  class FindOrCreateJournalInstanceTest {

    @Test
    @DisplayName("已存在实例时应直接返回，不创建新实例")
    void should_return_existing_instance_when_found() {
      // given
      VenueInstanceAggregate existingInstance =
          VenueInstanceAggregate.forJournal(VENUE_ID, VOLUME, ISSUE, YEAR, MONTH, DAY);
      when(venueInstanceRepository.findJournalInstance(
              eq(VENUE_ID.value()), eq(VOLUME), eq(ISSUE), eq(YEAR)))
          .thenReturn(Optional.of(existingInstance));

      JournalInstanceParams params =
          JournalInstanceParams.builder()
              .venueId(VENUE_ID)
              .volume(VOLUME)
              .issue(ISSUE)
              .publicationYear(YEAR)
              .publicationMonth(MONTH)
              .publicationDay(DAY)
              .build();

      // when
      VenueInstanceAggregate result = venueInstanceGatewayImpl.findOrCreateJournalInstance(params);

      // then
      assertThat(result).isSameAs(existingInstance);
      verify(venueInstanceRepository, never()).insertJournalInstanceIfAbsent(any());
    }

    @Test
    @DisplayName("不存在实例时应创建并保存新实例")
    void should_create_new_instance_when_not_found() {
      // given
      when(venueInstanceRepository.findJournalInstance(
              eq(VENUE_ID.value()), eq(VOLUME), eq(ISSUE), eq(YEAR)))
          .thenReturn(Optional.empty());
      when(venueInstanceRepository.insertJournalInstanceIfAbsent(any())).thenReturn(true);

      JournalInstanceParams params =
          JournalInstanceParams.builder()
              .venueId(VENUE_ID)
              .volume(VOLUME)
              .issue(ISSUE)
              .publicationYear(YEAR)
              .publicationMonth(MONTH)
              .publicationDay(DAY)
              .build();

      // when
      VenueInstanceAggregate result = venueInstanceGatewayImpl.findOrCreateJournalInstance(params);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getVenueId()).isEqualTo(VENUE_ID);
      assertThat(result.getVolume()).isEqualTo(VOLUME);
      assertThat(result.getIssue()).isEqualTo(ISSUE);
      assertThat(result.getPublicationYear()).isEqualTo(YEAR);
      assertThat(result.getPublicationMonth()).isEqualTo(MONTH);
      assertThat(result.getPublicationDay()).isEqualTo(DAY);
      verify(venueInstanceRepository)
          .insertJournalInstanceIfAbsent(any(VenueInstanceAggregate.class));
    }

    @Test
    @DisplayName("volume 和 issue 为 null 时也应正常工作")
    void should_work_when_volume_and_issue_are_null() {
      // given
      when(venueInstanceRepository.findJournalInstance(
              eq(VENUE_ID.value()), eq(null), eq(null), eq(YEAR)))
          .thenReturn(Optional.empty());
      when(venueInstanceRepository.insertJournalInstanceIfAbsent(any())).thenReturn(true);

      JournalInstanceParams params =
          JournalInstanceParams.builder()
              .venueId(VENUE_ID)
              .volume(null)
              .issue(null)
              .publicationYear(YEAR)
              .publicationMonth(null)
              .publicationDay(null)
              .build();

      // when
      VenueInstanceAggregate result = venueInstanceGatewayImpl.findOrCreateJournalInstance(params);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getVolume()).isNull();
      assertThat(result.getIssue()).isNull();
      assertThat(result.getPublicationYear()).isEqualTo(YEAR);
      verify(venueInstanceRepository)
          .insertJournalInstanceIfAbsent(any(VenueInstanceAggregate.class));
    }

    /// 插入因同一卷期已被并发插入而跳过时，重新查询返回已有实例。
    @Test
    @DisplayName("同一卷期已被并发插入时应重新查询返回已存在实例")
    void should_return_existing_instance_when_insert_skipped_by_conflict() {
      // given
      VenueInstanceAggregate existingInstance =
          VenueInstanceAggregate.forJournal(VENUE_ID, VOLUME, ISSUE, YEAR, MONTH, DAY);
      when(venueInstanceRepository.findJournalInstance(
              eq(VENUE_ID.value()), eq(VOLUME), eq(ISSUE), eq(YEAR)))
          .thenReturn(Optional.empty())
          .thenReturn(Optional.of(existingInstance));
      when(venueInstanceRepository.insertJournalInstanceIfAbsent(any())).thenReturn(false);

      JournalInstanceParams params =
          JournalInstanceParams.builder()
              .venueId(VENUE_ID)
              .volume(VOLUME)
              .issue(ISSUE)
              .publicationYear(YEAR)
              .publicationMonth(MONTH)
              .publicationDay(DAY)
              .build();

      // when
      VenueInstanceAggregate result = venueInstanceGatewayImpl.findOrCreateJournalInstance(params);

      // then
      assertThat(result).isSameAs(existingInstance);
    }
  }
}
