package dev.patra.interaction.adapter.subject.rest;

import dev.patra.interaction.adapter.subject.rest.request.SubjectStatsRequest;
import dev.patra.interaction.adapter.subject.rest.response.SubjectStatsItemResponse;
import dev.patra.interaction.adapter.subject.rest.response.SubjectStatsResponse;
import dev.patra.interaction.app.subject.usecase.query.SubjectStatsQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/// 对象的公开计数（不要会话，登录与否结果一样）：门户服务端按一屏一批取文献卡片的收藏人数与讨论数，文献页单篇也走它。
@Tag(name = "SubjectStats", description = "对象统计")
@RestController
@RequiredArgsConstructor
public class SubjectStatsController {

  private final SubjectStatsQueryService statsQuery;

  /// 批量对象统计：一次 1 ~ 100 个 key，每个 key 一行；没人互动过、格式不对的 key 两项都是 0。422 类型不支持（期刊）
  /// 或 key 个数越界。
  ///
  /// @param request 请求体
  /// @return 计数
  @PostMapping("/subjects/stats")
  public SubjectStatsResponse stats(@RequestBody SubjectStatsRequest request) {
    return SubjectStatsResponse.of(
        statsQuery.stats(request.type(), request.keys()).stream()
            .map(SubjectStatsItemResponse::from)
            .toList());
  }
}
