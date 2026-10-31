package dev.patra.notification.adapter.preference.rest;

import dev.patra.notification.adapter.preference.rest.request.ChangeEmailModeRequest;
import dev.patra.notification.adapter.preference.rest.response.EmailPreferenceResponse;
import dev.patra.notification.app.preference.usecase.change.ChangeEmailModeCommand;
import dev.patra.notification.app.preference.usecase.query.EmailPreferenceQueryService;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 当前用户的提醒邮件偏好（都要登录，未登录 401）：「回复我的」「@ 我的」各三档，设置页一次只改一类。
@Tag(name = "MyNotificationPreferences", description = "我的提醒邮件偏好")
@RestController
@RequestMapping("/me/notification-preferences")
@RequiredArgsConstructor
public class MyEmailPreferenceController {

  private final CommandBus commandBus;
  private final EmailPreferenceQueryService query;

  /// 读两类的档位；没改过的是默认的离站时逐条。
  ///
  /// @return 档位
  @GetMapping
  public EmailPreferenceResponse preferences() {
    return EmailPreferenceResponse.from(query.mine());
  }

  /// 改一类，立即保存。422：类别不是 `REPLY` / `MENTION`（含 `HELPFUL`）、档位缺失或不认识。
  ///
  /// @param category `REPLY` 或 `MENTION`
  /// @param request 请求体
  /// @return 保存后两类的档位
  @PutMapping("/{category}")
  public EmailPreferenceResponse change(
      @PathVariable String category, @RequestBody ChangeEmailModeRequest request) {
    return EmailPreferenceResponse.from(
        commandBus.handle(ChangeEmailModeCommand.of(category, request.mode())));
  }
}
