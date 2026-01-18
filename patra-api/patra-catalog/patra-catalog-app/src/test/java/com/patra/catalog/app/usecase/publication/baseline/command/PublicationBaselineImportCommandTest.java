package com.patra.catalog.app.usecase.publication.baseline.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.exception.CatalogScheduleParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// PublicationBaselineImportCommand 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationBaselineImportCommand")
class PublicationBaselineImportCommandTest {

  private static final String BASE_URL = "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/";

  @Nested
  @DisplayName("构造函数验证")
  class ConstructorValidation {

    @Test
    @DisplayName("有效参数应该成功创建命令")
    void should_create_command_with_valid_params() {
      // when
      var command = new PublicationBaselineImportCommand(BASE_URL, 1);

      // then
      assertThat(command.baseUrl()).isEqualTo(BASE_URL);
      assertThat(command.fileIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("baseUrl 为空时应该抛出异常")
    void should_throw_when_base_url_is_blank() {
      assertThatThrownBy(() -> new PublicationBaselineImportCommand("", 1))
          .isInstanceOf(CatalogScheduleParameterException.class)
          .hasMessageContaining("baseUrl");
    }

    @Test
    @DisplayName("baseUrl 为 null 时应该抛出异常")
    void should_throw_when_base_url_is_null() {
      assertThatThrownBy(() -> new PublicationBaselineImportCommand(null, 1))
          .isInstanceOf(CatalogScheduleParameterException.class)
          .hasMessageContaining("baseUrl");
    }

    @Test
    @DisplayName("baseUrl 不是 HTTP 协议时应该抛出异常")
    void should_throw_when_base_url_is_not_http() {
      assertThatThrownBy(() -> new PublicationBaselineImportCommand("ftp://example.com", 1))
          .isInstanceOf(CatalogScheduleParameterException.class)
          .hasMessageContaining("HTTP");
    }

    @Test
    @DisplayName("fileIndex 小于 1 时应该抛出异常")
    void should_throw_when_file_index_less_than_1() {
      assertThatThrownBy(() -> new PublicationBaselineImportCommand(BASE_URL, 0))
          .isInstanceOf(CatalogScheduleParameterException.class)
          .hasMessageContaining("fileIndex");
    }

    @Test
    @DisplayName("fileIndex 大于 1274 时应该抛出异常")
    void should_throw_when_file_index_greater_than_1274() {
      assertThatThrownBy(() -> new PublicationBaselineImportCommand(BASE_URL, 1275))
          .isInstanceOf(CatalogScheduleParameterException.class)
          .hasMessageContaining("fileIndex");
    }

    @Test
    @DisplayName("fileIndex 边界值 1 应该有效")
    void should_accept_file_index_1() {
      // when
      var command = new PublicationBaselineImportCommand(BASE_URL, 1);

      // then
      assertThat(command.fileIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("fileIndex 边界值 1274 应该有效")
    void should_accept_file_index_1274() {
      // when
      var command = new PublicationBaselineImportCommand(BASE_URL, 1274);

      // then
      assertThat(command.fileIndex()).isEqualTo(1274);
    }
  }

  @Nested
  @DisplayName("of() 工厂方法")
  class OfFactoryMethod {

    @Test
    @DisplayName("应该创建有效的命令对象")
    void should_create_command() {
      // when
      var command = PublicationBaselineImportCommand.of(BASE_URL, 42);

      // then
      assertThat(command.baseUrl()).isEqualTo(BASE_URL);
      assertThat(command.fileIndex()).isEqualTo(42);
    }
  }
}
