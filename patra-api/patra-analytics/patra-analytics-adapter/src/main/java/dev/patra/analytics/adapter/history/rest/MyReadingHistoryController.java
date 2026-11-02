package dev.patra.analytics.adapter.history.rest;

import dev.patra.analytics.adapter.history.rest.request.RecordingRequest;
import dev.patra.analytics.adapter.history.rest.response.ReadingHistoryResponse;
import dev.patra.analytics.adapter.history.rest.response.RecordingResponse;
import dev.patra.analytics.app.history.usecase.clear.ClearReadingHistoryCommand;
import dev.patra.analytics.app.history.usecase.delete.DeleteReadingEntryCommand;
import dev.patra.analytics.app.history.usecase.query.ReadingHistoryQueryService;
import dev.patra.analytics.app.history.usecase.recording.ChangeRecordingCommand;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 当前用户的阅读历史（阅读历史 spec §6，都要登录，未登录 401）。校验失败一律 422。
@Tag(name = "MyReadingHistory", description = "我的阅读历史")
@Validated
@RestController
@RequestMapping("/me/reading-history")
@RequiredArgsConstructor
public class MyReadingHistoryController {

  private final CommandBus commandBus;
  private final ReadingHistoryQueryService query;

  /// 一页历史（30 条，最近打开的在前），顺带返回开关状态。422：游标解析不了。
  ///
  /// @param cursor 上一页给的 `nextCursor`；第一页不传
  /// @return 这一页
  @GetMapping
  public ReadingHistoryResponse page(@RequestParam(required = false) String cursor) {
    return ReadingHistoryResponse.from(query.page(cursor));
  }

  /// 删一篇；本来不在也是 204。422：`articleId` 不是正整数。
  ///
  /// @param articleId 文献 ID
  @DeleteMapping("/{articleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable @Positive long articleId) {
    commandBus.handle(DeleteReadingEntryCommand.of(articleId));
  }

  /// 清空全部；开关不变，没有记录也是 204。
  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clear() {
    commandBus.handle(ClearReadingHistoryCommand.of());
  }

  /// 读开关；没设置过为开。
  ///
  /// @return 开关
  @GetMapping("/recording")
  public RecordingResponse recording() {
    return RecordingResponse.of(query.recordingEnabled());
  }

  /// 改开关，立即保存。422：`enabled` 缺失。
  ///
  /// @param request 请求体
  /// @return 保存后的开关
  @PutMapping("/recording")
  public RecordingResponse changeRecording(@Valid @RequestBody RecordingRequest request) {
    return RecordingResponse.of(
        commandBus.handle(ChangeRecordingCommand.of(request.enabled())).enabled());
  }
}
