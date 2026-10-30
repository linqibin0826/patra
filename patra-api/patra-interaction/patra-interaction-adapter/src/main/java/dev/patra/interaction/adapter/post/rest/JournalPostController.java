package dev.patra.interaction.adapter.post.rest;

import dev.patra.interaction.adapter.post.rest.request.ExperienceRequest;
import dev.patra.interaction.adapter.post.rest.response.ExperienceDetailResponse;
import dev.patra.interaction.adapter.post.rest.response.ExperienceSummaryResponse;
import dev.patra.interaction.adapter.post.rest.response.JournalExperiencePageResponse;
import dev.patra.interaction.app.post.usecase.experience.PublishExperienceCommand;
import dev.patra.interaction.app.post.usecase.query.ExperienceQueryService;
import dev.patra.interaction.domain.post.model.enums.SummaryWindow;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 期刊页的投稿经验区：经验列表与「同行经验」汇总（登录可选），写经验（要登录）。`journalId` 是 catalog 期刊 ID。
@Tag(name = "JournalExperiences", description = "期刊的投稿经验")
@RestController
@RequestMapping("/journals/{journalId}/posts")
@RequiredArgsConstructor
public class JournalPostController {

  private final CommandBus commandBus;
  private final ExperienceQueryService query;

  /// 经验列表，带期刊下的总条数；不受统计窗口影响。`latest`：投稿日期倒序、同日按发布时间倒序；`helpful`：有帮助数
  /// 降序、相同时发布时间新的在前。422 期刊 ID、排序、游标（含与排序不配套）、条数不合法。
  ///
  /// @param journalId 期刊 ID
  /// @param sort `latest`（默认）/ `helpful`
  /// @param cursor 同一种排序上一页的 `nextCursor`
  /// @param limit 每页 1 ~ 10，默认 10
  /// @return 一页
  @GetMapping
  public JournalExperiencePageResponse list(
      @PathVariable String journalId,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return JournalExperiencePageResponse.from(
        query.journalExperiences(journalId, sort, cursor, limit));
  }

  /// 「同行经验」汇总，读取时计算，写完立即反映。
  ///
  /// @param journalId 期刊 ID
  /// @param window `3y`（默认，按投稿日期近 3 年）/ `all`
  /// @return 汇总
  @GetMapping("/summary")
  public ExperienceSummaryResponse summary(
      @PathVariable String journalId, @RequestParam(required = false) String window) {
    return ExperienceSummaryResponse.from(
        SummaryWindow.fromParam(window).param(), query.summary(journalId, window));
  }

  /// 写一条投稿经验。422 字段错误（`errors[]`）；404 期刊不存在或已失效；409 `DUPLICATE_SUBMISSION`（带
  /// `existingPostId`）、`ACCOUNT_RESTRICTED`、`EMAIL_UNVERIFIED`；429 `DAILY_LIMIT_REACHED`；503 依赖不可用。
  ///
  /// @param journalId 期刊 ID
  /// @param request 请求体
  /// @return 新经验的详情
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ExperienceDetailResponse publish(
      @PathVariable String journalId, @RequestBody ExperienceRequest request) {
    return ExperienceDetailResponse.from(
        commandBus.handle(
            PublishExperienceCommand.of(journalId, request.toInput(), request.anonymousOrFalse())));
  }
}
