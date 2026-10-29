package dev.patra.catalog.app.portal.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.portal.model.enums.ExportFormat;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import dev.patra.catalog.domain.portal.port.citation.CitationExportPort;
import dev.patra.catalog.domain.portal.port.read.CitationRecordReadPort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

/// `PortalPublicationExportService` 单元测试：去重、按 500 一批懒加载、每批一个只读事务、按请求顺序。
///
/// 导出端口的替身逐批拉取迭代器，模拟写出器边读边写。
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("PortalPublicationExportService 单元测试")
class PortalPublicationExportServiceTest {

  @Captor private ArgumentCaptor<Collection<Long>> batches;

  private final CitationRecordReadPort readPort = mock(CitationRecordReadPort.class);
  private final CitationExportPort exportPort = mock(CitationExportPort.class);
  private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final List<List<CitationRecord>> pulled = new ArrayList<>();
  private PortalPublicationExportService service;

  @BeforeEach
  void setUp() throws IOException {
    doAnswer(
            inv -> {
              Iterator<List<CitationRecord>> it = inv.getArgument(1);
              while (it.hasNext()) {
                pulled.add(it.next());
              }
              return null;
            })
        .when(exportPort)
        .export(eq(ExportFormat.RIS), any(), eq(out));
    service = new PortalPublicationExportService(readPort, exportPort, txManager);
  }

  @Test
  @DisplayName("5,000 个 ID：读 10 批、每批 ≤ 500 个，每批一个只读事务")
  void should_read_5000_ids_in_10_batches_of_at_most_500() throws IOException {
    when(readPort.findByIds(any())).thenReturn(List.of());

    service.export(LongStream.rangeClosed(1, 5000).boxed().toList(), ExportFormat.RIS, out);

    verify(readPort, times(10)).findByIds(batches.capture());
    assertThat(batches.getAllValues()).allSatisfy(b -> assertThat(b).hasSizeLessThanOrEqualTo(500));
    assertThat(batches.getAllValues().stream().mapToInt(Collection::size).sum()).isEqualTo(5000);
    verify(txManager, times(10)).getTransaction(argThat(def -> def != null && def.isReadOnly()));
    assertThat(pulled).hasSize(10);
  }

  @Test
  @DisplayName("每批按请求顺序，不存在的 ID 跳过")
  void should_write_batches_in_request_order_and_skip_missing() throws IOException {
    when(readPort.findByIds(any())).thenReturn(List.of(record(1), record(2), record(3)));

    service.export(List.of(3L, 99L, 1L, 2L), ExportFormat.RIS, out);

    assertThat(pulled)
        .singleElement()
        .satisfies(
            batch -> assertThat(batch).extracting(CitationRecord::id).containsExactly(3L, 1L, 2L));
  }

  @Test
  @DisplayName("懒加载：写出器拉下一批时才读库")
  void should_read_each_batch_only_when_pulled() throws IOException {
    doAnswer(
            inv -> {
              Iterator<List<CitationRecord>> it = inv.getArgument(1);
              verifyNoInteractions(readPort);
              assertThat(it.hasNext()).isTrue();
              it.next();
              verify(readPort, times(1)).findByIds(any());
              return null;
            })
        .when(exportPort)
        .export(eq(ExportFormat.CSV), any(), eq(out));
    when(readPort.findByIds(any())).thenReturn(List.of());

    service.export(LongStream.rangeClosed(1, 1000).boxed().toList(), ExportFormat.CSV, out);
  }

  @Test
  @DisplayName("先去重再分批")
  void should_deduplicate_ids_before_batching() throws IOException {
    when(readPort.findByIds(any())).thenReturn(List.of());

    service.export(List.of(1L, 1L, 2L, 1L), ExportFormat.RIS, out);

    verify(readPort).findByIds(batches.capture());
    assertThat(new ArrayList<>(batches.getValue())).containsExactly(1L, 2L);
  }

  private static CitationRecord record(long id) {
    return CitationRecord.builder().id(id).title("Paper " + id).build();
  }
}
