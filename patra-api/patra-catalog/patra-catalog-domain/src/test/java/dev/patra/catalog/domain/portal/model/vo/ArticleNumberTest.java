package dev.patra.catalog.domain.portal.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.kernel.model.vo.Pagination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `ArticleNumber` 单元测试：PII 何时算文章号。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ArticleNumber 单元测试")
class ArticleNumberTest {

  private static final Pagination NO_PAGES = Pagination.of(null, null, null);

  @Test
  @DisplayName("没有页码时，e 编号与纯数字 PII 是文章号")
  void should_use_pii_as_article_number_without_pagination() {
    assertThat(ArticleNumber.of("e0123456", NO_PAGES)).contains("e0123456");
    assertThat(ArticleNumber.of("12345", NO_PAGES)).contains("12345");
  }

  @Test
  @DisplayName("Elsevier 形的 PII（含括号）不是文章号")
  void should_not_use_elsevier_pii() {
    assertThat(ArticleNumber.of("S0140-6736(21)01624-4", NO_PAGES)).isEmpty();
  }

  @Test
  @DisplayName("有页码时以页码为准，没有文章号")
  void should_not_use_pii_when_pagination_present() {
    assertThat(ArticleNumber.of("e0123456", Pagination.of("1451-61", null, null))).isEmpty();
  }

  @Test
  @DisplayName("没有 PII 时为空")
  void should_be_empty_without_pii() {
    assertThat(ArticleNumber.of(null, NO_PAGES)).isEmpty();
    assertThat(ArticleNumber.of(" ", NO_PAGES)).isEmpty();
  }
}
