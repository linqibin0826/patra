package dev.patra.notification.adapter.preference.rest;

import dev.patra.notification.adapter.preference.rest.request.UnsubscribeRequest;
import dev.patra.notification.adapter.preference.rest.response.UnsubscribeLinkResponse;
import dev.patra.notification.adapter.preference.rest.response.UnsubscribeResponse;
import dev.patra.notification.app.preference.usecase.query.EmailPreferenceQueryService;
import dev.patra.notification.app.preference.usecase.unsubscribe.UnsubscribeCommand;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/// 凭退订令牌退订提醒邮件（全部免登录，只认令牌，不看当前登录的是谁；spec 第 8 节）。门户的确认页调前两个，
/// 邮箱服务商的 RFC 8058 一键退订经门户原样转发到第三个。
///
/// 链接无效（令牌缺失、格式不对、不存在，类别不对，读接口时账号已注销）一律 404 + `UNSUBSCRIBE_LINK_INVALID`。
@Tag(name = "Unsubscribe", description = "退订提醒邮件")
@RestController
@RequestMapping("/unsubscribe")
@RequiredArgsConstructor
public class UnsubscribeController {

  private final CommandBus commandBus;
  private final EmailPreferenceQueryService query;

  /// 确认页打开时读链接信息：只读，不改任何东西，可被预取。503：identity 不可用。
  ///
  /// @param token 令牌
  /// @param category `REPLY`、`MENTION` 或 `DIGEST`
  /// @return 落地页数据
  @GetMapping
  public UnsubscribeLinkResponse inspect(
      @RequestParam(required = false) String token,
      @RequestParam(required = false) String category) {
    return UnsubscribeLinkResponse.from(query.inspect(token, category));
  }

  /// 确认页上点了按钮：退订这一类，或 `ALL` 关闭全部提醒邮件。重复调用结果相同。
  ///
  /// @param request 令牌与类别
  /// @return 退订结果
  @PostMapping
  public UnsubscribeResponse confirm(@RequestBody UnsubscribeRequest request) {
    return UnsubscribeResponse.from(
        commandBus.handle(UnsubscribeCommand.confirm(request.token(), request.category())));
  }

  /// RFC 8058 一键退订：直接生效、不重定向、重复调用结果相同。地址自带令牌与类别；请求体是邮箱服务商发的
  /// `List-Unsubscribe=One-Click`，不检查它，不声明 `consumes`，表单体、multipart 与空体都收。
  ///
  /// 令牌与类别只从查询串取，不用 `@RequestParam`：表单 POST 下它会把请求体里的同名参数和查询串合并（两个值拼成
  /// `a,b`），请求体被改写或伪造时退订会失败、甚至被顶替。
  ///
  /// @param request 当前请求，`?token=…&category=REPLY|MENTION|DIGEST`
  /// @return 退订结果
  @PostMapping("/one-click")
  public UnsubscribeResponse oneClick(HttpServletRequest request) {
    MultiValueMap<String, String> query =
        UriComponentsBuilder.newInstance().query(request.getQueryString()).build().getQueryParams();
    return UnsubscribeResponse.from(
        commandBus.handle(
            UnsubscribeCommand.oneClick(
                decoded(query.getFirst("token")), decoded(query.getFirst("category")))));
  }

  /// 查询串里的值解码（令牌是 base64url、类别是大写字母，正常不带转义；有邮箱客户端多转义一次也认）。
  ///
  /// @param raw 原始值，可空
  /// @return 解码后的值；缺失为 `null`
  private static String decoded(String raw) {
    return raw == null ? null : UriUtils.decode(raw, StandardCharsets.UTF_8);
  }
}
