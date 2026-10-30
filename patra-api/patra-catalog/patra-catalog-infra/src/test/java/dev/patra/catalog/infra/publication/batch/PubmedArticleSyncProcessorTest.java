package dev.patra.catalog.infra.publication.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.kernel.exception.XmlParseException;
import dev.patra.catalog.domain.kernel.model.enums.PublicationIdentifierType;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.Identifier;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication.PublicationDates;
import dev.patra.catalog.domain.publication.model.vo.PartialDate;
import dev.patra.catalog.domain.publication.model.vo.PublicationSyncTarget;
import dev.patra.catalog.domain.publication.model.vo.PubmedSourceRecord;
import dev.patra.catalog.domain.publication.model.vo.SourcePosition;
import dev.patra.catalog.domain.publication.port.parser.PubmedXmlParserPort;
import dev.patra.catalog.domain.publication.port.repository.PublicationSyncRepository;
import dev.patra.catalog.infra.publication.batch.update.PublicationSyncItem;
import dev.patra.catalog.infra.publication.batch.update.PubmedImportCounters;
import java.time.DateTimeException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/// `PubmedArticleSyncProcessor` 单元测试：按来源位置挡住旧版本（spec 第 5.2 节），坏记录只跳过这一篇。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedArticleSyncProcessor 单元测试")
class PubmedArticleSyncProcessorTest {

  private static final String PMID = "38001234";
  private static final String RAW = "<PubmedArticle>…</PubmedArticle>";
  private static final SourcePosition P1333 = SourcePosition.ofFileName("pubmed26n1333.xml.gz");
  private static final SourcePosition P1350 = SourcePosition.ofFileName("pubmed26n1350.xml.gz");

  private final PubmedXmlParserPort parser = mock(PubmedXmlParserPort.class);
  private final PublicationSyncRepository repository = mock(PublicationSyncRepository.class);
  private final PubmedArticleItemProcessor delegate = mock(PubmedArticleItemProcessor.class);
  private final PubmedImportCounters counters = new PubmedImportCounters();
  private final PublicationImportResult built = mock(PublicationImportResult.class);

  private PubmedArticleSyncProcessor processor(PubmedImportScope scope, boolean reparse) {
    return new PubmedArticleSyncProcessor(parser, repository, delegate, scope, counters, reparse);
  }

  private void given(CanonicalPublication parsed, PublicationSyncTarget target) throws Exception {
    when(parser.parseArticle(RAW)).thenReturn(parsed);
    when(repository.findSyncTargetsByPmids(any()))
        .thenReturn(target == null ? Map.of() : Map.of(PMID, target));
    when(delegate.process(any())).thenReturn(built);
  }

  private static CanonicalPublication valid() {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
        .title("T")
        .dates(PublicationDates.builder().published(PartialDate.ofYear(2021)).build())
        .build();
  }

  private static PublicationSyncTarget live(Integer rank, boolean deleted) {
    return PublicationSyncTarget.builder()
        .publicationId(7L)
        .pmid(PMID)
        .doi("10.1/x")
        .deleted(deleted)
        .sourceRank(rank)
        .build();
  }

  private static PublicationSyncTarget tombstone(int rank) {
    return PublicationSyncTarget.builder().pmid(PMID).sourceRank(rank).build();
  }

  private static PubmedSourceRecord record(SourcePosition position) {
    return PubmedSourceRecord.of(PMID, RAW, position);
  }

  @Test
  @DisplayName("库里没有、EXISTING_ONLY：跳过，计 skippedAbsent")
  void should_skip_absent_pmid_in_existing_only_scope() throws Exception {
    given(valid(), null);

    assertThat(processor(PubmedImportScope.EXISTING_ONLY, false).process(record(P1333))).isNull();
    assertThat(counters.snapshot(0).skippedAbsent()).isEqualTo(1);
    verify(delegate, never()).process(any());
  }

  @Test
  @DisplayName("库里没有、ALL：新增（target 为空），带上原文与位置")
  void should_insert_absent_pmid_in_all_scope() throws Exception {
    given(valid(), null);

    PublicationSyncItem item = processor(PubmedImportScope.ALL, false).process(record(P1333));

    assertThat(item.target()).isNull();
    assertThat(item.result()).isSameAs(built);
    assertThat(item.rawXml()).isEqualTo(RAW);
    assertThat(item.position()).isEqualTo(P1333);
  }

  @Test
  @DisplayName("进来的比库里旧：跳过，计 skippedStale")
  void should_skip_older_rank_and_count_stale() throws Exception {
    given(valid(), live(261_340, false));

    assertThat(processor(PubmedImportScope.EXISTING_ONLY, false).process(record(P1333))).isNull();
    assertThat(counters.snapshot(0).skippedStale()).isEqualTo(1);
  }

  @Test
  @DisplayName("同一位置：覆盖（按新解析器重算）")
  void should_overwrite_equal_rank() throws Exception {
    given(valid(), live(261_333, false));

    assertThat(processor(PubmedImportScope.EXISTING_ONLY, false).process(record(P1333)).target())
        .isEqualTo(live(261_333, false));
  }

  @Test
  @DisplayName("进来的更新：覆盖")
  void should_overwrite_newer_rank() throws Exception {
    given(valid(), live(261_333, false));

    assertThat(processor(PubmedImportScope.EXISTING_ONLY, false).process(record(P1350)))
        .isNotNull();
  }

  @Test
  @DisplayName("库里还没有位置（旧数据）：视为最旧，覆盖")
  void should_overwrite_when_stored_rank_null() throws Exception {
    given(valid(), live(null, false));

    assertThat(processor(PubmedImportScope.EXISTING_ONLY, false).process(record(P1333)))
        .isNotNull();
  }

  @Test
  @DisplayName("已软删、进来的不比库里新：跳过；更新才复活")
  void should_skip_soft_deleted_when_incoming_not_newer() throws Exception {
    given(valid(), live(261_350, true));

    assertThat(processor(PubmedImportScope.EXISTING_ONLY, false).process(record(P1350))).isNull();
    assertThat(processor(PubmedImportScope.EXISTING_ONLY, false).process(record(P1333))).isNull();
    assertThat(counters.snapshot(0).skippedStale()).isEqualTo(2);
  }

  @Test
  @DisplayName("墓碑比进来的新：跳过")
  void should_skip_tombstone_when_incoming_older() throws Exception {
    given(valid(), tombstone(261_350));

    assertThat(processor(PubmedImportScope.ALL, false).process(record(P1333))).isNull();
    assertThat(counters.snapshot(0).skippedStale()).isEqualTo(1);
  }

  @Test
  @DisplayName("墓碑比进来的旧、ALL：当作库里没有，新增")
  void should_insert_when_tombstone_is_older_in_all_scope() throws Exception {
    given(valid(), tombstone(261_340));

    PublicationSyncItem item = processor(PubmedImportScope.ALL, false).process(record(P1350));

    assertThat(item).isNotNull();
    assertThat(item.target()).isNull();
  }

  @Test
  @DisplayName("墓碑比进来的旧、EXISTING_ONLY：当作库里没有，跳过")
  void should_skip_when_tombstone_is_older_in_existing_only_scope() throws Exception {
    given(valid(), tombstone(261_340));

    assertThat(processor(PubmedImportScope.EXISTING_ONLY, false).process(record(P1350))).isNull();
    assertThat(counters.snapshot(0).skippedAbsent()).isEqualTo(1);
  }

  @Test
  @DisplayName("原文不是合法 XML：跳过这一篇，计 skippedInvalid")
  void should_skip_record_on_xml_parse_exception() throws Exception {
    when(parser.parseArticle(RAW)).thenThrow(new XmlParseException("坏", null));

    assertThat(processor(PubmedImportScope.ALL, false).process(record(P1333))).isNull();
    assertThat(counters.snapshot(0).skippedInvalid()).isEqualTo(1);
  }

  @Test
  @DisplayName("解析阶段抛出非 XML 的单篇异常（日期越界等）：跳过这一篇，不让整个文件失败")
  void should_skip_record_on_non_xml_parse_exception() throws Exception {
    when(parser.parseArticle(RAW)).thenThrow(new DateTimeException("Year 99999 越界"));

    assertThat(processor(PubmedImportScope.ALL, false).process(record(P1333))).isNull();
    assertThat(counters.snapshot(0).skippedInvalid()).isEqualTo(1);
  }

  @Test
  @DisplayName("组装时领域断言失败：跳过这一篇")
  void should_skip_record_on_domain_assertion() throws Exception {
    given(valid(), null);
    when(delegate.process(any())).thenThrow(new IllegalArgumentException("月份必须在 1-12"));

    assertThat(processor(PubmedImportScope.ALL, false).process(record(P1333))).isNull();
    assertThat(counters.snapshot(0).skippedInvalid()).isEqualTo(1);
  }

  @Test
  @DisplayName("缺年份或缺标题：跳过这一篇，不进组装")
  void should_skip_record_without_year_or_title() throws Exception {
    CanonicalPublication noYear =
        CanonicalPublication.builder()
            .identifiers(
                List.of(
                    Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
            .title("T")
            .dates(PublicationDates.builder().build())
            .build();
    given(noYear, null);

    assertThat(processor(PubmedImportScope.ALL, false).process(record(P1333))).isNull();
    assertThat(counters.snapshot(0).skippedInvalid()).isEqualTo(1);
    verify(delegate, never()).process(any());
  }

  /// `ArticleTitle` 为空、只有 `VernacularTitle`（非英文文献）时照常进组装，不算无效记录。
  ///
  /// @throws Exception 处理失败时
  @Test
  @DisplayName("只有 VernacularTitle（非英文文献）：照常进组装，不计 skippedInvalid")
  void should_pass_record_with_only_vernacular_title_to_assembler() throws Exception {
    CanonicalPublication vernacularOnly =
        CanonicalPublication.builder()
            .identifiers(
                List.of(
                    Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
            .originalTitle("Medizinische Klinik dankt den Gutachterinnen und Gutachtern")
            .dates(PublicationDates.builder().published(PartialDate.ofYear(2026)).build())
            .build();
    given(vernacularOnly, null);

    assertThat(processor(PubmedImportScope.ALL, false).process(record(P1333))).isNotNull();
    assertThat(counters.snapshot(0).skippedInvalid()).isZero();
    verify(delegate).process(any());
  }

  /// `ArticleTitle` 与 `VernacularTitle` 都为空时跳过这一篇，计 `skippedInvalid`，不进组装。
  ///
  /// @throws Exception 处理失败时
  @Test
  @DisplayName("两种标题都没有：跳过这一篇，不进组装")
  void should_skip_record_without_any_title() throws Exception {
    CanonicalPublication noTitle =
        CanonicalPublication.builder()
            .identifiers(
                List.of(
                    Identifier.builder().type(PublicationIdentifierType.PMID).value(PMID).build()))
            .dates(PublicationDates.builder().published(PartialDate.ofYear(2026)).build())
            .build();
    given(noTitle, null);

    assertThat(processor(PubmedImportScope.ALL, false).process(record(P1333))).isNull();
    assertThat(counters.snapshot(0).skippedInvalid()).isEqualTo(1);
    verify(delegate, never()).process(any());
  }

  @Test
  @DisplayName("数据库异常不吞，照常抛出让文件失败")
  void should_propagate_infrastructure_exception() throws Exception {
    when(parser.parseArticle(RAW)).thenReturn(valid());
    when(repository.findSyncTargetsByPmids(any()))
        .thenThrow(new DataAccessResourceFailureException("db down"));

    assertThatThrownBy(() -> processor(PubmedImportScope.ALL, false).process(record(P1333)))
        .isInstanceOf(DataAccessResourceFailureException.class);
  }

  @Test
  @DisplayName("本地重算只接受同一位置的未软删文献")
  void should_only_accept_equal_rank_in_reparse_mode() throws Exception {
    given(valid(), live(261_350, false));

    assertThat(processor(PubmedImportScope.EXISTING_ONLY, true).process(record(P1333))).isNull();
    assertThat(processor(PubmedImportScope.EXISTING_ONLY, true).process(record(P1350))).isNotNull();
  }
}
