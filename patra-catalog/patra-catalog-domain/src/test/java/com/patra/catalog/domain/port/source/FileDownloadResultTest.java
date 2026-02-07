package com.patra.catalog.domain.port.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// FileDownloadResult 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("FileDownloadResult 值对象")
@Timeout(2)
class FileDownloadResultTest {

  private static final Path VALID_PATH = Path.of("/tmp/test-file.xml");

  @Nested
  @DisplayName("正常创建")
  class CreationTests {

    @Test
    @DisplayName("应该通过 of() 工厂方法正确创建")
    void should_create_via_factory_method() {
      var result = FileDownloadResult.of(VALID_PATH, 1024);

      assertThat(result.filePath()).isEqualTo(VALID_PATH);
      assertThat(result.fileSize()).isEqualTo(1024);
    }

    @Test
    @DisplayName("fileSize 为 0 应该合法（空文件）")
    void should_accept_zero_file_size() {
      var result = FileDownloadResult.of(VALID_PATH, 0);

      assertThat(result.fileSize()).isZero();
    }
  }

  @Nested
  @DisplayName("参数验证")
  class ValidationTests {

    @Test
    @DisplayName("filePath 为 null 时应抛出 IllegalArgumentException")
    void should_throw_when_filePath_is_null() {
      assertThatThrownBy(() -> FileDownloadResult.of(null, 100))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("filePath 不能为 null");
    }

    @Test
    @DisplayName("fileSize 为负数时应抛出 IllegalArgumentException")
    void should_throw_when_fileSize_is_negative() {
      assertThatThrownBy(() -> FileDownloadResult.of(VALID_PATH, -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("fileSize 不能为负数");
    }
  }
}
