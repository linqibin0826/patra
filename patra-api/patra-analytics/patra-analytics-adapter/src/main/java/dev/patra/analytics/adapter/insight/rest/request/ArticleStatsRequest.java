package dev.patra.analytics.adapter.insight.rest.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/// 批量计数的请求体。缺失、为空、超过 100 个或含 0、负数时 422；含 `null` 元素时拷贝失败，按请求体不可读处理（400）。
///
/// @param articleIds 文献 ID，1 ~ 100 个（JSON 里是字符串）
public record ArticleStatsRequest(
    @NotNull @Size(min = 1, max = 100) List<@NotNull @Positive Long> articleIds) {

  /// 紧凑构造器：防御性拷贝；`null` 原样保留交给 `@NotNull`。
  public ArticleStatsRequest {
    articleIds = articleIds != null ? List.copyOf(articleIds) : null;
  }
}
