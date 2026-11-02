package dev.patra.analytics.adapter.insight.rest;

import dev.patra.analytics.adapter.insight.rest.request.ArticleStatsRequest;
import dev.patra.analytics.adapter.insight.rest.response.AlsoReadItemResponse;
import dev.patra.analytics.adapter.insight.rest.response.AlsoReadResponse;
import dev.patra.analytics.adapter.insight.rest.response.ArticleStatsItemResponse;
import dev.patra.analytics.adapter.insight.rest.response.ArticleStatsResponse;
import dev.patra.analytics.app.insight.usecase.query.AlsoReadQueryService;
import dev.patra.analytics.app.insight.usecase.query.ArticleStatsQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 文献的公开数字（insight spec §9.1、§9.3）：不要登录，登录与否结果一样。校验失败 422，依赖不可用 503。
@Tag(name = "ArticleInsight", description = "文献计数与看过还看了")
@Validated
@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
public class ArticleInsightController {

  private final ArticleStatsQueryService statsQuery;
  private final AlsoReadQueryService alsoReadQuery;

  /// 批量计数：一次 1 ~ 100 篇，去重后按请求顺序每篇一行；没人看过的是 0。数字有 60 秒缓存。
  ///
  /// @param request 请求体
  /// @return 计数
  @PostMapping("/stats")
  public ArticleStatsResponse stats(@Valid @RequestBody ArticleStatsRequest request) {
    return ArticleStatsResponse.of(
        statsQuery.stats(request.articleIds()).stream()
            .map(ArticleStatsItemResponse::from)
            .toList());
  }

  /// 看过这篇的人还看了：最多 6 篇，一起读的在前，不足时补同主题、同期刊的文献。
  ///
  /// @param articleId 文献 ID
  /// @return 文献
  @GetMapping("/{articleId}/also-read")
  public AlsoReadResponse alsoRead(@PathVariable @Positive long articleId) {
    return AlsoReadResponse.of(
        alsoReadQuery.alsoRead(articleId).stream().map(AlsoReadItemResponse::from).toList());
  }
}
