package com.patra.catalog.infra.adapter.batch.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import com.patra.catalog.domain.model.vo.publication.pubmed.PubmedArticle;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import com.patra.catalog.domain.model.vo.venueinstance.JournalInstanceParams;
import com.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.catalog.infra.adapter.batch.publication.cache.VenueCache;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// PubmedArticleItemProcessor 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedArticleItemProcessor")
@ExtendWith(MockitoExtension.class)
class PubmedArticleItemProcessorTest {

  @Mock private PublicationRepository publicationRepository;

  @Mock private VenueCache venueCache;

  @Mock private VenueInstanceGateway venueInstanceGateway;

  private PubmedArticleItemProcessor processor;

  private static final String PMID = "12345678";
  private static final String DOI = "10.1234/example";
  private static final String NLM_ID = "101234567";
  private static final String ISSN = "1234-5678";
  private static final Long VENUE_ID = 1L;
  private static final Long VENUE_INSTANCE_ID = 100L;

  @BeforeEach
  void setUp() {
    processor =
        new PubmedArticleItemProcessor(publicationRepository, venueCache, venueInstanceGateway);
  }

  @Nested
  @DisplayName("process()")
  class ProcessTest {

    @Test
    @DisplayName("已存在的 PMID 应该返回 null（跳过）")
    void should_return_null_when_pmid_already_exists() throws Exception {
      // given
      PubmedArticle article = createArticle(PMID);
      when(publicationRepository.existsByPmid(PMID)).thenReturn(true);

      // when
      PublicationAggregate result = processor.process(article);

      // then
      assertThat(result).isNull();
      verify(venueCache, never()).findByPriority(any(), any());
    }

    @Test
    @DisplayName("无法匹配 Venue 时应该返回 null（跳过）")
    void should_return_null_when_venue_not_matched() throws Exception {
      // given
      PubmedArticle article = createArticle(PMID);
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
      when(venueCache.findByPriority(any(), any())).thenReturn(Optional.empty());

      // when
      PublicationAggregate result = processor.process(article);

      // then
      assertThat(result).isNull();
      verify(venueInstanceGateway, never()).findOrCreateJournalInstance(any());
    }

    @Test
    @DisplayName("成功匹配 Venue 时应该创建 PublicationAggregate")
    void should_create_publication_aggregate_when_venue_matched() throws Exception {
      // given
      PubmedArticle article = createFullArticle();
      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
      when(venueCache.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));

      VenueInstanceAggregate venueInstance = createVenueInstance();
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(venueInstance);

      // when
      PublicationAggregate result = processor.process(article);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getPmid()).isEqualTo(PMID);
      assertThat(result.getDoi()).isEqualTo(DOI);
      assertThat(result.getTitle()).isEqualTo("Test Article Title");
      assertThat(result.getPublicationYear()).isEqualTo(2024);
      assertThat(result.getVenueId().value()).isEqualTo(VENUE_ID);
      assertThat(result.getVenueInstanceId().value()).isEqualTo(VENUE_INSTANCE_ID);
    }

    @Test
    @DisplayName("无 DOI 时应该只使用 PMID 创建标识符")
    void should_create_identifiers_with_pmid_only_when_no_doi() throws Exception {
      // given
      PubmedArticle article =
          PubmedArticle.builder()
              .pmid(PMID)
              .articleTitle("Test Article")
              .nlmUniqueId(NLM_ID)
              .pubYear(2024)
              .build();

      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
      when(venueCache.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));

      VenueInstanceAggregate venueInstance = createVenueInstance();
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(venueInstance);

      // when
      PublicationAggregate result = processor.process(article);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getPmid()).isEqualTo(PMID);
      assertThat(result.getDoi()).isNull();
    }

    @Test
    @DisplayName("应该使用出版状态转换枚举值")
    void should_convert_publication_status() throws Exception {
      // given
      PubmedArticle article =
          PubmedArticle.builder()
              .pmid(PMID)
              .articleTitle("Test Article")
              .nlmUniqueId(NLM_ID)
              .pubYear(2024)
              .publicationStatus("epublish")
              .build();

      when(publicationRepository.existsByPmid(PMID)).thenReturn(false);
      when(venueCache.findByPriority(eq(NLM_ID), any()))
          .thenReturn(Optional.of(VenueId.of(VENUE_ID)));

      VenueInstanceAggregate venueInstance = createVenueInstance();
      when(venueInstanceGateway.findOrCreateJournalInstance(any(JournalInstanceParams.class)))
          .thenReturn(venueInstance);

      // when
      PublicationAggregate result = processor.process(article);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getPublicationStatus()).isNotNull();
      assertThat(result.getPublicationStatus().getCode()).isEqualTo("epublish");
    }
  }

  /// 创建简单的测试文章。
  private PubmedArticle createArticle(String pmid) {
    return PubmedArticle.builder()
        .pmid(pmid)
        .articleTitle("Test Article")
        .nlmUniqueId(NLM_ID)
        .pubYear(2024)
        .build();
  }

  /// 创建包含所有字段的测试文章。
  private PubmedArticle createFullArticle() {
    return PubmedArticle.builder()
        .pmid(PMID)
        .doi(DOI)
        .articleTitle("Test Article Title")
        .vernacularTitle("测试文章标题")
        .nlmUniqueId(NLM_ID)
        .issnPrint(ISSN)
        .journalTitle("Test Journal")
        .volume("10")
        .issue("2")
        .pubYear(2024)
        .pubMonth(6)
        .pubDay(15)
        .languages(List.of("eng"))
        .publicationStatus("ppublish")
        .authorsComplete(true)
        .build();
  }

  /// 创建测试用的 VenueInstance。
  private VenueInstanceAggregate createVenueInstance() {
    // 使用 restore 模拟已持久化的实例
    return VenueInstanceAggregate.restore(
        VenueInstanceId.of(VENUE_INSTANCE_ID),
        VenueId.of(VENUE_ID),
        "10",
        "2",
        null,
        2024,
        6,
        15,
        null,
        null,
        null,
        null,
        0L);
  }
}
