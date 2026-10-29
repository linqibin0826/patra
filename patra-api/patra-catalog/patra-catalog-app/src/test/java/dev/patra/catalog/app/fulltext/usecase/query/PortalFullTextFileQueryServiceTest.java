package dev.patra.catalog.app.fulltext.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.fulltext.exception.FullTextNotAvailableException;
import dev.patra.catalog.domain.fulltext.model.read.FullTextFileRef;
import dev.patra.catalog.domain.fulltext.model.vo.StoredFile;
import dev.patra.catalog.domain.fulltext.port.read.FullTextFileReadPort;
import dev.patra.catalog.domain.fulltext.port.storage.FullTextObjectStoragePort;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// `PortalFullTextFileQueryService` 单元测试。
@ExtendWith(MockitoExtension.class)
@DisplayName("PortalFullTextFileQueryService")
class PortalFullTextFileQueryServiceTest {

  private static final FullTextFileRef REF =
      FullTextFileRef.builder()
          .objectKey("k")
          .fileName("PMC1.pdf")
          .contentType("application/pdf")
          .size(1)
          .etag("e")
          .build();

  @Mock private FullTextFileReadPort readPort;
  @Mock private FullTextObjectStoragePort storage;
  @InjectMocks private PortalFullTextFileQueryService service;

  @Test
  @DisplayName("PDF 不可下载时抛 NOT_FOUND")
  void should_throw_when_pdf_not_available() {
    when(readPort.findPdf(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPdf(1L)).isInstanceOf(FullTextNotAvailableException.class);
  }

  @Test
  @DisplayName("图片不属于该篇时抛 NOT_FOUND")
  void should_throw_when_figure_not_found() {
    when(readPort.findFigure(1L, "x.jpg")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getFigure(1L, "x.jpg"))
        .isInstanceOf(FullTextNotAvailableException.class);
  }

  @Test
  @DisplayName("打开文件委托给对象存储")
  void should_open_object_from_storage() {
    StoredFile file = StoredFile.of(InputStream.nullInputStream(), null);
    when(readPort.findPdf(1L)).thenReturn(Optional.of(REF));
    when(storage.open("k")).thenReturn(file);

    assertThat(service.getPdf(1L)).isEqualTo(REF);
    assertThat(service.open(REF)).isSameAs(file);
  }
}
