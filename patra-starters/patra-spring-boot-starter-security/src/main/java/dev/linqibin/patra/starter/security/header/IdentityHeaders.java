package dev.linqibin.patra.starter.security.header;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;

/// 网关向下游传递身份的请求头约定。网关和下游用同一个类读写。
///
/// 五个头都是单值。身份头没有签名：安全性依赖服务不对外暴露和内部令牌不泄露。
public final class IdentityHeaders {

  /// 用户 ID，十进制字符串。
  public static final String USER_ID = "X-Patra-User-Id";

  /// 会话 ID，十进制字符串。
  public static final String SESSION_ID = "X-Patra-Session-Id";

  /// 账号类型，`AccountType` 的字符串形式。
  public static final String ACCOUNT_TYPE = "X-Patra-Account-Type";

  /// 客户端类型，`ClientType` 的字符串形式。
  public static final String CLIENT_TYPE = "X-Patra-Client-Type";

  /// 网关与下游共享的内部令牌。
  public static final String GATEWAY_TOKEN = "X-Patra-Gateway-Token";

  /// 四个身份头的名字（不含内部令牌头）。
  public static final List<String> IDENTITY_HEADER_NAMES =
      List.of(USER_ID, SESSION_ID, ACCOUNT_TYPE, CLIENT_TYPE);

  /// 规范写法的正整数：没有符号、没有前导零、只有 ASCII 数字、最多 19 位。
  private static final Pattern POSITIVE_LONG = Pattern.compile("[1-9][0-9]{0,18}");

  /// 工具类，不允许实例化。
  private IdentityHeaders() {}

  /// 从一组请求头解析身份。
  ///
  /// @param headers 请求头
  /// @return 三种结果之一：当前用户、没有身份、格式不合法
  public static IdentityParseResult parse(HttpHeaders headers) {
    int present = 0;
    for (String name : IDENTITY_HEADER_NAMES) {
      List<String> values = headers.getOrEmpty(name);
      if (values.size() > 1) {
        return IdentityParseResult.malformed(name + " 出现多次");
      }
      present += values.size();
    }
    if (present == 0) {
      return IdentityParseResult.absent();
    }
    if (present < IDENTITY_HEADER_NAMES.size()) {
      return IdentityParseResult.malformed("身份头不完整");
    }
    OptionalLong userId = parsePositiveLong(headers.getFirst(USER_ID));
    if (userId.isEmpty()) {
      return IdentityParseResult.malformed(USER_ID + " 不是正整数");
    }
    OptionalLong sessionId = parsePositiveLong(headers.getFirst(SESSION_ID));
    if (sessionId.isEmpty()) {
      return IdentityParseResult.malformed(SESSION_ID + " 不是正整数");
    }
    Optional<AccountType> accountType = AccountType.fromCode(headers.getFirst(ACCOUNT_TYPE));
    if (accountType.isEmpty()) {
      return IdentityParseResult.malformed(ACCOUNT_TYPE + " 的值不认识");
    }
    Optional<ClientType> clientType = ClientType.fromCode(headers.getFirst(CLIENT_TYPE));
    if (clientType.isEmpty()) {
      return IdentityParseResult.malformed(CLIENT_TYPE + " 的值不认识");
    }
    return IdentityParseResult.identified(
        CurrentUser.of(
            userId.getAsLong(), sessionId.getAsLong(), accountType.get(), clientType.get()));
  }

  /// 把规范写法的正整数解析成 long。
  ///
  /// @param value 请求头的值，可能为 `null`
  /// @return 解析结果；写法不规范或超出 Long 范围时返回空
  private static OptionalLong parsePositiveLong(String value) {
    if (value == null || !POSITIVE_LONG.matcher(value).matches()) {
      return OptionalLong.empty();
    }
    try {
      return OptionalLong.of(Long.parseLong(value));
    } catch (NumberFormatException overflow) {
      return OptionalLong.empty();
    }
  }

  /// 把当前用户写成四个身份头。已有的同名头会被覆盖。
  ///
  /// @param headers 要写入的请求头
  /// @param user 当前用户
  public static void write(HttpHeaders headers, CurrentUser user) {
    headers.set(USER_ID, Long.toString(user.userId()));
    headers.set(SESSION_ID, Long.toString(user.sessionId()));
    headers.set(ACCOUNT_TYPE, user.accountType().getCode());
    headers.set(CLIENT_TYPE, user.clientType().getCode());
  }
}
