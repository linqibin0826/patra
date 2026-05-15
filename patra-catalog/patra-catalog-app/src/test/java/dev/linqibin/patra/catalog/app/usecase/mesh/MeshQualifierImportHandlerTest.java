package dev.linqibin.patra.catalog.app.usecase.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.app.usecase.mesh.command.MeshQualifierImportCommand;
import dev.linqibin.patra.catalog.app.usecase.mesh.dto.MeshQualifierImportResult;
import dev.linqibin.patra.catalog.domain.exception.DataAlreadyExistsException;
import dev.linqibin.patra.catalog.domain.exception.XmlParseException;
import dev.linqibin.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.MeshUI;
import dev.linqibin.patra.catalog.domain.port.parser.MeshQualifierParserPort;
import dev.linqibin.patra.catalog.domain.port.repository.MeshQualifierRepository;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadPort;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadResult;
import dev.linqibin.commons.error.ApplicationException;
import dev.linqibin.commons.error.codes.ErrorCodeLike;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// MeSH 限定词导入处理器单元测试。
///
/// **测试策略**：
///
/// - 单元测试：Mock 所有 Port 依赖
/// - 使用 @TempDir 创建真实临时文件，验证文件清理行为
///
/// **重点测试场景**：
///
/// - 数据存在性检查：表中有数据时应拒绝导入
/// - 正常导入流程：下载临时文件 → 解析 XML → 设置版本号 → 批量保存
/// - 临时文件清理：成功和失败场景下均应删除临时文件
/// - 异常传播和包装
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("MeshQualifierImportHandler 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class MeshQualifierImportHandlerTest {

  private static final String TEST_URL =
      "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml";
  private static final String TEST_VERSION = "2025";

  @Mock private FileDownloadPort fileDownloadPort;
  @Mock private MeshQualifierParserPort qualifierParserPort;
  @Mock private MeshQualifierRepository qualifierRepository;

  @TempDir Path tempDir;

  private MeshQualifierImportHandler handler;
  private Path tempFile;
  private FileDownloadResult downloadResult;

  @BeforeEach
  void setUp() throws Exception {
    handler =
        new MeshQualifierImportHandler(fileDownloadPort, qualifierParserPort, qualifierRepository);
    tempFile = tempDir.resolve("qual2025.xml");
    Files.write(tempFile, "<xml/>".getBytes());
    downloadResult = FileDownloadResult.of(tempFile, Files.size(tempFile));
  }

  @Nested
  @DisplayName("数据存在性检查测试")
  class DataExistenceCheckTest {

    @Test
    @DisplayName("表中已有数据时 - 应该抛出 DataAlreadyExistsException")
    void shouldThrowException_whenDataAlreadyExists() {
      // Given
      MeshQualifierImportCommand command = new MeshQualifierImportCommand(TEST_URL, TEST_VERSION);
      when(qualifierRepository.hasAnyData()).thenReturn(true);

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(DataAlreadyExistsException.class)
          .hasMessageContaining("MeSH Qualifier");

      // 验证没有触发下载和解析
      verify(fileDownloadPort, never()).download(any());
      verify(qualifierParserPort, never()).parse(any());
    }

    @Test
    @DisplayName("表中无数据时 - 应该正常执行导入")
    void shouldProceed_whenNoDataExists() {
      // Given
      MeshQualifierImportCommand command = new MeshQualifierImportCommand(TEST_URL, TEST_VERSION);
      MeshQualifierAggregate q1 =
          MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM");

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(qualifierParserPort.parse(any(InputStream.class))).thenReturn(Stream.of(q1));

      // When
      MeshQualifierImportResult result = handler.handle(command);

      // Then
      assertThat(result).isNotNull();
      verify(qualifierRepository).hasAnyData();
      verify(fileDownloadPort).download(any(URI.class));
      verify(qualifierParserPort).parse(any(InputStream.class));
      verify(qualifierRepository).saveBatch(anyList());
    }
  }

  @Nested
  @DisplayName("正常导入流程测试")
  class NormalImportFlowTest {

    @Test
    @DisplayName("应该正确传递 URL 到 FileDownloadPort")
    void shouldPassCorrectUrlToDownloadPort() {
      // Given
      MeshQualifierImportCommand command = new MeshQualifierImportCommand(TEST_URL, TEST_VERSION);
      MeshQualifierAggregate q1 =
          MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM");

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(qualifierParserPort.parse(any(InputStream.class))).thenReturn(Stream.of(q1));

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
      verify(fileDownloadPort).download(uriCaptor.capture());
      assertThat(uriCaptor.getValue()).hasToString(TEST_URL);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("应该将解析结果设置版本号后批量保存")
    void shouldSetVersionAndSaveBatch() {
      // Given
      MeshQualifierImportCommand command = new MeshQualifierImportCommand(TEST_URL, TEST_VERSION);
      MeshQualifierAggregate q1 =
          MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM");
      MeshQualifierAggregate q2 =
          MeshQualifierAggregate.create(MeshUI.qualifierOf(2), "diagnosis", "DI");

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(qualifierParserPort.parse(any(InputStream.class))).thenReturn(Stream.of(q1, q2));

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<List<MeshQualifierAggregate>> captor = ArgumentCaptor.forClass(List.class);
      verify(qualifierRepository).saveBatch(captor.capture());

      List<MeshQualifierAggregate> saved = captor.getValue();
      assertThat(saved).hasSize(2);
      assertThat(saved).allSatisfy(q -> assertThat(q.getMeshVersion()).isEqualTo(TEST_VERSION));
    }

    @Test
    @DisplayName("返回结果应包含正确的导入信息")
    void shouldReturnCorrectResult() {
      // Given
      MeshQualifierImportCommand command = new MeshQualifierImportCommand(TEST_URL, TEST_VERSION);
      MeshQualifierAggregate q1 =
          MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM");
      MeshQualifierAggregate q2 =
          MeshQualifierAggregate.create(MeshUI.qualifierOf(2), "diagnosis", "DI");
      MeshQualifierAggregate q3 =
          MeshQualifierAggregate.create(MeshUI.qualifierOf(3), "genetics", "GE");

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(qualifierParserPort.parse(any(InputStream.class))).thenReturn(Stream.of(q1, q2, q3));

      // When
      MeshQualifierImportResult result = handler.handle(command);

      // Then
      assertThat(result.sourceUrl()).isEqualTo(TEST_URL);
      assertThat(result.meshVersion()).isEqualTo(TEST_VERSION);
      assertThat(result.importedCount()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("临时文件清理测试")
  class TempFileCleanupTest {

    @Test
    @DisplayName("导入成功后应该删除临时文件")
    void shouldDeleteTempFile_afterSuccessfulImport() {
      // Given
      MeshQualifierImportCommand command = new MeshQualifierImportCommand(TEST_URL, TEST_VERSION);
      MeshQualifierAggregate q1 =
          MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM");

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(qualifierParserPort.parse(any(InputStream.class))).thenReturn(Stream.of(q1));

      // When
      handler.handle(command);

      // Then - 临时文件应已被删除
      assertThat(Files.exists(tempFile)).isFalse();
    }

    @Test
    @DisplayName("导入失败后也应该删除临时文件")
    void shouldDeleteTempFile_afterFailedImport() {
      // Given
      MeshQualifierImportCommand command = new MeshQualifierImportCommand(TEST_URL, TEST_VERSION);

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(qualifierParserPort.parse(any(InputStream.class)))
          .thenThrow(new RuntimeException("解析失败"));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(ApplicationException.class);

      // 临时文件应仍然被清理
      assertThat(Files.exists(tempFile)).isFalse();
    }
  }

  @Nested
  @DisplayName("异常处理测试")
  class ExceptionHandlingTest {

    @Test
    @DisplayName("XmlParseException（DomainException）应该直接传播，不被包装")
    void shouldRethrowDomainException() {
      // Given
      MeshQualifierImportCommand command = new MeshQualifierImportCommand(TEST_URL, TEST_VERSION);

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(qualifierParserPort.parse(any(InputStream.class)))
          .thenThrow(new XmlParseException("XML 格式错误"));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(XmlParseException.class)
          .hasMessageContaining("XML 格式错误");
    }

    @Test
    @DisplayName("ApplicationException 应该直接抛出，不重复包装")
    void shouldRethrowApplicationException() {
      // Given
      MeshQualifierImportCommand command = new MeshQualifierImportCommand(TEST_URL, TEST_VERSION);
      ApplicationException originalException =
          new ApplicationException(
              new ErrorCodeLike() {
                @Override
                public String code() {
                  return "TEST_ERROR";
                }

                @Override
                public int httpStatus() {
                  return 500;
                }
              },
              "原始错误");

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(qualifierParserPort.parse(any(InputStream.class))).thenThrow(originalException);

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .isSameAs(originalException);
    }

    @Test
    @DisplayName("RuntimeException 应该包装为 ApplicationException (CAT_1001)")
    void shouldWrapRuntimeException() {
      // Given
      MeshQualifierImportCommand command = new MeshQualifierImportCommand(TEST_URL, TEST_VERSION);

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(qualifierParserPort.parse(any(InputStream.class)))
          .thenThrow(new RuntimeException("内部错误"));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("MeSH 限定词导入失败")
          .hasMessageContaining("内部错误")
          .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Checked Exception 应该包装为 ApplicationException (CAT_1001)")
    void shouldWrapCheckedException() {
      // Given
      MeshQualifierImportCommand command = new MeshQualifierImportCommand(TEST_URL, TEST_VERSION);

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(fileDownloadPort.download(any(URI.class))).thenReturn(downloadResult);
      when(qualifierParserPort.parse(any(InputStream.class)))
          .thenAnswer(
              invocation -> {
                throw new Exception("IO 错误");
              });

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("MeSH 限定词导入时发生意外错误");
    }
  }
}
