package com.patra.catalog.domain.model.vo.publication;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// PublicationImportParams 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationImportParams 值对象")
class PublicationImportParamsTest {

  private static final String BASE_URL = "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/";

  @Nested
  @DisplayName("参数验证")
  class ValidationTest {

    @Test
    @DisplayName("应接受有效参数")
    void should_accept_valid_params() {
      var params = PublicationImportParams.of(BASE_URL, 1);

      assertEquals(BASE_URL, params.baseUrl());
      assertEquals(1, params.fileIndex());
    }

    @Test
    @DisplayName("baseUrl 为空时应抛出异常")
    void should_throw_when_baseUrl_is_blank() {
      assertThatThrownBy(() -> PublicationImportParams.of("", 1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("baseUrl 不能为空");
    }

    @Test
    @DisplayName("fileIndex 小于 1 时应抛出异常")
    void should_throw_when_fileIndex_less_than_1() {
      assertThatThrownBy(() -> PublicationImportParams.of(BASE_URL, 0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("fileIndex 必须在 1 到");
    }

    @Test
    @DisplayName("fileIndex 大于 1334 时应抛出异常")
    void should_throw_when_fileIndex_greater_than_max() {
      assertThatThrownBy(() -> PublicationImportParams.of(BASE_URL, 1335))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("fileIndex 必须在 1 到");
    }

    @Test
    @DisplayName("应接受边界值 1 和 1334")
    void should_accept_boundary_values() {
      var params1 = PublicationImportParams.of(BASE_URL, 1);
      var params1334 = PublicationImportParams.of(BASE_URL, 1334);

      assertEquals(1, params1.fileIndex());
      assertEquals(1334, params1334.fileIndex());
    }
  }

  @Nested
  @DisplayName("文件名生成")
  class FileNameGenerationTest {

    @Test
    @DisplayName("应正确生成第 1 个文件名")
    void should_generate_first_file_name() {
      var params = PublicationImportParams.of(BASE_URL, 1);

      assertEquals("pubmed26n0001.xml.gz", params.getFileName());
    }

    @Test
    @DisplayName("应正确生成第 100 个文件名")
    void should_generate_100th_file_name() {
      var params = PublicationImportParams.of(BASE_URL, 100);

      assertEquals("pubmed26n0100.xml.gz", params.getFileName());
    }

    @Test
    @DisplayName("应正确生成第 1334 个文件名")
    void should_generate_last_file_name() {
      var params = PublicationImportParams.of(BASE_URL, 1334);

      assertEquals("pubmed26n1334.xml.gz", params.getFileName());
    }
  }

  @Nested
  @DisplayName("下载 URL 生成")
  class DownloadUrlGenerationTest {

    @Test
    @DisplayName("baseUrl 以斜杠结尾时应正确拼接")
    void should_generate_url_when_baseUrl_ends_with_slash() {
      var params = PublicationImportParams.of(BASE_URL, 1);

      assertEquals(
          "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/pubmed26n0001.xml.gz",
          params.getDownloadUrl());
    }

    @Test
    @DisplayName("baseUrl 不以斜杠结尾时应自动添加")
    void should_add_slash_when_baseUrl_does_not_end_with_slash() {
      var params = PublicationImportParams.of("https://ftp.ncbi.nlm.nih.gov/pubmed/baseline", 1);

      assertEquals(
          "https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/pubmed26n0001.xml.gz",
          params.getDownloadUrl());
    }
  }

  @Nested
  @DisplayName("常量验证")
  class ConstantsTest {

    @Test
    @DisplayName("TOTAL_FILE_COUNT 应为 1334")
    void should_have_correct_total_file_count() {
      assertEquals(1334, PublicationImportParams.TOTAL_FILE_COUNT);
    }
  }
}
