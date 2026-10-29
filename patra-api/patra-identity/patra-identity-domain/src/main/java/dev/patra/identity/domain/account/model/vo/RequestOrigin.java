package dev.patra.identity.domain.account.model.vo;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Locale;

/// 一次请求从哪来：适配器给的客户端 IP 与 User-Agent 原值，规范化之后的样子。
///
/// 两者都不是可信输入：受信的门户可能转来浏览器谎报的 IP，tailnet 上直连 identity 时
/// `getRemoteAddr()` 也可以是任意串（PAP-99 设计第 8.1 节）。所以 IP 只收合法字面量，UA 去掉控制字符并截断。
///
/// @param ip 客户端 IP 的规范文本，没有或不合法时为 `null`
/// @param userAgent User-Agent，没有时为 `null`
public record RequestOrigin(String ip, String userAgent) {

  /// UA 最多保留的码点数，与登录记录的列宽一致。
  public static final int USER_AGENT_MAX_LENGTH = 512;

  /// IP 文本的最长长度（`VARCHAR(45)`）。
  static final int IP_MAX_LENGTH = 45;

  /// 规范化两个值。
  public RequestOrigin {
    ip = normalizeIp(ip);
    userAgent = normalizeUserAgent(userAgent);
  }

  /// 从适配器给的原值创建。
  ///
  /// @param ip 客户端 IP，可以为 `null`
  /// @param userAgent User-Agent，可以为 `null`
  /// @return 规范化之后的来源
  public static RequestOrigin of(String ip, String userAgent) {
    return new RequestOrigin(ip, userAgent);
  }

  /// 什么都不知道的来源。
  ///
  /// @return IP 与 UA 都为 `null`
  public static RequestOrigin unknown() {
    return new RequestOrigin(null, null);
  }

  /// IP：去方括号与首尾空白，只收字面量（`InetAddress.ofLiteral` 不做 DNS）；IPv4 映射地址还原成 IPv4，
  /// IPv6 规范成 RFC 5952 的写法（同一地址只有一种写法，经网关转来的展开形式也一样）。
  ///
  /// @param raw 原值
  /// @return 规范文本；不合法时为 `null`
  private static String normalizeIp(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String value = raw.strip();
    if (value.startsWith("[") && value.endsWith("]")) {
      value = value.substring(1, value.length() - 1);
    }
    if (value.length() > IP_MAX_LENGTH) {
      return null;
    }
    InetAddress address;
    try {
      address = InetAddress.ofLiteral(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
    if (address instanceof Inet4Address) {
      return address.getHostAddress();
    }
    int scope = value.indexOf('%');
    String zone = scope < 0 ? "" : value.substring(scope).toLowerCase(Locale.ROOT);
    return rfc5952(address.getAddress()) + zone;
  }

  /// IPv6 的 RFC 5952 写法：每组小写十六进制、去前导零；最长的一段（至少两组）连续零压成 `::`，
  /// 一样长时压第一段。
  ///
  /// @param bytes 16 字节地址
  /// @return 文本，不带区域标识
  private static String rfc5952(byte[] bytes) {
    int[] groups = new int[8];
    for (int i = 0; i < groups.length; i++) {
      groups[i] = ((bytes[2 * i] & 0xff) << 8) | (bytes[2 * i + 1] & 0xff);
    }
    int bestStart = -1;
    int bestLength = 1;
    for (int i = 0; i < groups.length; i++) {
      int length = 0;
      while (i + length < groups.length && groups[i + length] == 0) {
        length++;
      }
      if (length > bestLength) {
        bestStart = i;
        bestLength = length;
      }
      i += length;
    }
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < groups.length; i++) {
      if (i == bestStart) {
        text.append("::");
        i += bestLength - 1;
        continue;
      }
      if (!text.isEmpty() && text.charAt(text.length() - 1) != ':') {
        text.append(':');
      }
      text.append(Integer.toHexString(groups[i]));
    }
    return text.toString();
  }

  /// UA：去掉控制字符，按码点截到 {@link #USER_AGENT_MAX_LENGTH}；空白当 `null`。
  ///
  /// @param raw 原值
  /// @return 规范化之后的 UA；没有时为 `null`
  private static String normalizeUserAgent(String raw) {
    if (raw == null) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    raw.codePoints()
        .filter(codePoint -> !Character.isISOControl(codePoint))
        .limit(USER_AGENT_MAX_LENGTH)
        .forEach(builder::appendCodePoint);
    String value = builder.toString();
    return value.isBlank() ? null : value;
  }
}
