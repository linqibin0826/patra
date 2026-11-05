package dev.patra.analytics.adapter.insight.rest;

import dev.patra.analytics.adapter.insight.rest.response.HotReadingResponse;
import dev.patra.analytics.app.insight.usecase.query.HotReadingQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/// 首页热读（insight spec §9.2）：不要登录；只读每小时重算一次的 PG 快照。
@Tag(name = "HotReading", description = "近期热读")
@RestController
@RequiredArgsConstructor
public class HotReadingController {

  private final HotReadingQueryService query;

  /// 一页最多 14 篇：上榜的按名次在前，最新收录补位的在后。任务从没跑过时 `items` 为空。
  ///
  /// @return 热读页
  @GetMapping("/hot-reads")
  public HotReadingResponse hotReads() {
    return HotReadingResponse.from(query.page());
  }
}
