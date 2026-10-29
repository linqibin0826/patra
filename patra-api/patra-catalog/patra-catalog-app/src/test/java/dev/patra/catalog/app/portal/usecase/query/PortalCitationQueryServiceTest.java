package dev.patra.catalog.app.portal.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.portal.model.enums.CitationStyle;
import dev.patra.catalog.domain.portal.model.read.CitationEntry;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import dev.patra.catalog.domain.portal.port.citation.CitationFormatterPort;
import dev.patra.catalog.domain.portal.port.read.CitationRecordReadPort;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

/// `PortalCitationQueryService` 单元测试：去重、读一次、按请求顺序交给格式化端口。
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("PortalCitationQueryService 单元测试")
class PortalCitationQueryServiceTest {

  @Captor private ArgumentCaptor<List<CitationRecord>> passed;

  private final CitationRecordReadPort readPort = mock(CitationRecordReadPort.class);
  private final CitationFormatterPort formatter = mock(CitationFormatterPort.class);
  private final PortalCitationQueryService service =
      new PortalCitationQueryService(readPort, formatter);

  @Test
  @DisplayName("记录按请求顺序（去重、跳过不存在）交给格式化端口，结果原样返回")
  void should_pass_records_in_request_order_to_formatter() {
    when(readPort.findByIds(any())).thenReturn(List.of(record(1), record(2), record(3)));
    List<CitationEntry> entries = List.of(CitationEntry.of("[1]", "x"));
    when(formatter.format(eq(CitationStyle.VANCOUVER), anyList())).thenReturn(entries);

    List<CitationEntry> result =
        service.format(List.of(2L, 99L, 3L, 2L, 1L), CitationStyle.VANCOUVER);

    verify(formatter).format(eq(CitationStyle.VANCOUVER), passed.capture());
    assertThat(passed.getValue()).extracting(CitationRecord::id).containsExactly(2L, 3L, 1L);
    assertThat(result).isEqualTo(entries);
  }

  @Test
  @DisplayName("全部不存在：返回空列表，不调用格式化端口")
  void should_return_empty_entries_when_all_missing() {
    when(readPort.findByIds(any())).thenReturn(List.of());

    assertThat(service.format(List.of(7L), CitationStyle.APA_7)).isEmpty();
    verifyNoInteractions(formatter);
  }

  private static CitationRecord record(long id) {
    return CitationRecord.builder().id(id).title("Paper " + id).build();
  }
}
