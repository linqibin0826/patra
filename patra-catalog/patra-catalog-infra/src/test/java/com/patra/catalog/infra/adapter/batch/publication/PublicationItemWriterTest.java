package com.patra.catalog.infra.adapter.batch.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.vo.publication.PublicationCompleteData;
import com.patra.catalog.domain.model.vo.publication.PublicationId;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.common.enums.ProvenanceCode;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

/// PublicationItemWriter 单元测试。
///
/// **重构后的测试策略**：
///
/// 重构后的 Writer 职责简化为：
/// 1. 使用 Mapper 转换 PublicationImportResult → PublicationCompleteData
/// 2. 委托 Repository 批量写入
///
/// DAO 层的详细写入测试已移至 PublicationRepositoryAdapterTest。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationItemWriter")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class PublicationItemWriterTest {

  @Mock private PublicationRepository publicationRepository;

  @Mock private PublicationImportResultMapper resultMapper;

  @Captor private ArgumentCaptor<List<PublicationCompleteData>> dataCaptor;

  private PublicationItemWriter writer;

  private static final Long PUBLICATION_ID = 1001L;

  @BeforeEach
  void setUp() {
    writer = new PublicationItemWriter(publicationRepository, resultMapper);
  }

  @Nested
  @DisplayName("write()")
  class WriteTest {

    @Test
    @DisplayName("应该将 ImportResult 转换为 CompleteData 并委托 Repository 写入")
    void should_convert_and_delegate_to_repository() throws Exception {
      // given
      PublicationAggregate pub1 = createPublication("12345678");
      pub1.assignId(PublicationId.of(PUBLICATION_ID));
      PublicationAggregate pub2 = createPublication("87654321");
      pub2.assignId(PublicationId.of(PUBLICATION_ID + 1));

      PublicationImportResult result1 = PublicationImportResult.ofPublication(pub1);
      PublicationImportResult result2 = PublicationImportResult.ofPublication(pub2);

      PublicationCompleteData data1 = createCompleteData(pub1);
      PublicationCompleteData data2 = createCompleteData(pub2);

      when(resultMapper.toCompleteData(result1)).thenReturn(data1);
      when(resultMapper.toCompleteData(result2)).thenReturn(data2);

      Chunk<PublicationImportResult> chunk = new Chunk<>(List.of(result1, result2));

      // when
      writer.write(chunk);

      // then
      verify(publicationRepository).insertAllWithAssociations(dataCaptor.capture());
      List<PublicationCompleteData> capturedData = dataCaptor.getValue();
      assertThat(capturedData).hasSize(2);
      assertThat(capturedData).containsExactly(data1, data2);
    }

    @Test
    @DisplayName("空 chunk 不应调用 Repository")
    void should_not_call_repository_for_empty_chunk() throws Exception {
      // given
      Chunk<PublicationImportResult> emptyChunk = new Chunk<>();

      // when
      writer.write(emptyChunk);

      // then
      verify(publicationRepository, never()).insertAllWithAssociations(anyList());
      verify(resultMapper, never()).toCompleteData(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("单条记录应该正确处理")
    void should_handle_single_record() throws Exception {
      // given
      PublicationAggregate pub = createPublication("12345678");
      pub.assignId(PublicationId.of(PUBLICATION_ID));

      PublicationImportResult result = PublicationImportResult.ofPublication(pub);
      PublicationCompleteData data = createCompleteData(pub);

      when(resultMapper.toCompleteData(result)).thenReturn(data);

      Chunk<PublicationImportResult> chunk = new Chunk<>(List.of(result));

      // when
      writer.write(chunk);

      // then
      verify(publicationRepository).insertAllWithAssociations(dataCaptor.capture());
      List<PublicationCompleteData> capturedData = dataCaptor.getValue();
      assertThat(capturedData).hasSize(1);
      assertThat(capturedData.getFirst().publication()).isEqualTo(pub);
    }

    @Test
    @DisplayName("应该为每个 ImportResult 调用一次 Mapper")
    void should_call_mapper_for_each_result() throws Exception {
      // given
      PublicationAggregate pub1 = createPublication("11111111");
      PublicationAggregate pub2 = createPublication("22222222");
      PublicationAggregate pub3 = createPublication("33333333");

      pub1.assignId(PublicationId.of(1L));
      pub2.assignId(PublicationId.of(2L));
      pub3.assignId(PublicationId.of(3L));

      PublicationImportResult result1 = PublicationImportResult.ofPublication(pub1);
      PublicationImportResult result2 = PublicationImportResult.ofPublication(pub2);
      PublicationImportResult result3 = PublicationImportResult.ofPublication(pub3);

      when(resultMapper.toCompleteData(result1)).thenReturn(createCompleteData(pub1));
      when(resultMapper.toCompleteData(result2)).thenReturn(createCompleteData(pub2));
      when(resultMapper.toCompleteData(result3)).thenReturn(createCompleteData(pub3));

      Chunk<PublicationImportResult> chunk = new Chunk<>(List.of(result1, result2, result3));

      // when
      writer.write(chunk);

      // then
      verify(resultMapper).toCompleteData(result1);
      verify(resultMapper).toCompleteData(result2);
      verify(resultMapper).toCompleteData(result3);
    }
  }

  /// 创建测试用的 PublicationAggregate。
  private PublicationAggregate createPublication(String pmid) {
    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        pmid,
        null, // doi
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

  /// 创建测试用的 PublicationCompleteData。
  private PublicationCompleteData createCompleteData(PublicationAggregate publication) {
    return PublicationCompleteData.builder()
        .publication(publication)
        .metadata(null)
        .meshHeadings(List.of())
        .keywords(List.of())
        .funding(List.of())
        .publicationTypes(List.of())
        .supplMeshList(List.of())
        .alternativeAbstracts(List.of())
        .dates(List.of())
        .investigators(List.of())
        .personalNameSubjects(List.of())
        .build();
  }
}
