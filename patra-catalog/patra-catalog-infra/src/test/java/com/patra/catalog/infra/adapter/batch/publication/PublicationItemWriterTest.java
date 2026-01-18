package com.patra.catalog.infra.adapter.batch.publication;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifiers;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.common.enums.ProvenanceCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

/// PublicationItemWriter 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationItemWriter")
@ExtendWith(MockitoExtension.class)
class PublicationItemWriterTest {

  @Mock private PublicationRepository publicationRepository;

  private PublicationItemWriter writer;

  @BeforeEach
  void setUp() {
    writer = new PublicationItemWriter(publicationRepository);
  }

  @Nested
  @DisplayName("write()")
  class WriteTest {

    @Test
    @DisplayName("应该批量插入 Publication 列表")
    void should_insert_all_publications() throws Exception {
      // given
      PublicationAggregate pub1 = createPublication("12345678");
      PublicationAggregate pub2 = createPublication("87654321");
      Chunk<PublicationAggregate> chunk = new Chunk<>(List.of(pub1, pub2));

      // when
      writer.write(chunk);

      // then
      verify(publicationRepository).insertAll(List.of(pub1, pub2));
    }

    @Test
    @DisplayName("空 chunk 不应调用 insertAll")
    void should_not_call_insert_all_for_empty_chunk() throws Exception {
      // given
      Chunk<PublicationAggregate> emptyChunk = new Chunk<>();

      // when
      writer.write(emptyChunk);

      // then
      verify(publicationRepository, never()).insertAll(anyList());
    }
  }

  /// 创建测试用的 PublicationAggregate。
  private PublicationAggregate createPublication(String pmid) {
    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        PublicationIdentifiers.ofPmid(pmid),
        VenueId.of(1L),
        VenueInstanceId.of(100L),
        "Test Article " + pmid,
        null,
        null,
        null,
        null,
        2024,
        true,
        null,
        null);
  }
}
