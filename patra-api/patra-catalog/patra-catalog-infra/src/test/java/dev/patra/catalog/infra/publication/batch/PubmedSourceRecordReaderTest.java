package dev.patra.catalog.infra.publication.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.publication.model.vo.PubmedSourceRecord;
import dev.patra.catalog.domain.publication.model.vo.SourcePosition;
import dev.patra.catalog.domain.publication.port.repository.PublicationSyncRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/// `PubmedSourceRecordReader` 单元测试：按 PMID 键集分页读本地原文，断点记最后一个 PMID。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedSourceRecordReader 单元测试")
class PubmedSourceRecordReaderTest {

  private static final SourcePosition P = SourcePosition.ofFileName("pubmed26n1333.xml.gz");

  private final PublicationSyncRepository repository = mock(PublicationSyncRepository.class);

  @Test
  @DisplayName("按页读完：每页用上一页最后一个 PMID 续查，读完返回 null")
  void should_page_through_candidates_by_last_pmid() throws Exception {
    when(repository.findReparseCandidates(null, 2)).thenReturn(List.of(rec("1"), rec("2")));
    when(repository.findReparseCandidates("2", 2)).thenReturn(List.of(rec("3")));
    PubmedSourceRecordReader reader = new PubmedSourceRecordReader(repository, 2);
    reader.open(new ExecutionContext());

    assertThat(reader.read().pmid()).isEqualTo("1");
    assertThat(reader.read().pmid()).isEqualTo("2");
    assertThat(reader.read().pmid()).isEqualTo("3");
    assertThat(reader.read()).isNull();
  }

  @Test
  @DisplayName("断点续传：从上下文记下的最后一个 PMID 之后读")
  void should_resume_after_saved_pmid() throws Exception {
    when(repository.findReparseCandidates("2", 2)).thenReturn(List.of(rec("3")));
    PubmedSourceRecordReader reader = new PubmedSourceRecordReader(repository, 2);
    ExecutionContext ctx = new ExecutionContext();
    ctx.putString(PubmedSourceRecordReader.LAST_PMID, "2");
    reader.open(ctx);

    assertThat(reader.read().pmid()).isEqualTo("3");
    reader.update(ctx);
    assertThat(ctx.getString(PubmedSourceRecordReader.LAST_PMID)).isEqualTo("3");
  }

  private static PubmedSourceRecord rec(String pmid) {
    return PubmedSourceRecord.of(pmid, "<x/>", P);
  }
}
