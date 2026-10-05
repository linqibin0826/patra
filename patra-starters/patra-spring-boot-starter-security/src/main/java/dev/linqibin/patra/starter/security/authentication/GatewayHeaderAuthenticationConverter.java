package dev.linqibin.patra.starter.security.authentication;

import dev.linqibin.patra.starter.security.header.IdentityHeaders;
import dev.linqibin.patra.starter.security.header.IdentityParseResult;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;

/// 把网关传来的身份头变成认证对象。
///
/// 只有带着正确内部令牌的请求，身份头才被采信。令牌缺失或不对时按匿名处理：
/// 服务之间直连的调用不带令牌，它们不应该因此被拒绝。
@Slf4j
public final class GatewayHeaderAuthenticationConverter implements AuthenticationConverter {

  private final byte[] expectedToken;

  /// 创建转换器。
  ///
  /// @param gatewayToken 网关与下游共享的内部令牌，调用方保证非空白
  public GatewayHeaderAuthenticationConverter(String gatewayToken) {
    this.expectedToken = gatewayToken.getBytes(StandardCharsets.UTF_8);
  }

  /// 从请求头建立认证。
  ///
  /// @param request 当前请求
  /// @return 认证对象；应按匿名处理时返回 `null`
  /// @throws MalformedIdentityException 内部令牌正确但身份头残缺或不合法时
  @Override
  public Authentication convert(HttpServletRequest request) {
    HttpHeaders identityHeaders = identityHeadersOf(request);
    if (!hasValidGatewayToken(request)) {
      if (!identityHeaders.isEmpty()) {
        log.warn(
            "收到带身份头但内部令牌缺失或不正确的请求，身份头不予采信，按匿名处理: {} {}",
            request.getMethod(),
            request.getRequestURI());
      }
      return null;
    }
    IdentityParseResult result = IdentityHeaders.parse(identityHeaders);
    if (result instanceof IdentityParseResult.Identified identified) {
      return new CurrentUserAuthentication(identified.user());
    }
    if (result instanceof IdentityParseResult.Malformed malformed) {
      throw new MalformedIdentityException(malformed.reason());
    }
    return null;
  }

  /// 判断请求是否带着正确的内部令牌。
  ///
  /// 令牌头必须恰好出现一次。比较用恒定时间算法，不因内容不同而提前返回。
  ///
  /// @param request 当前请求
  /// @return 令牌正确时为 true
  private boolean hasValidGatewayToken(HttpServletRequest request) {
    List<String> presented = Collections.list(request.getHeaders(IdentityHeaders.GATEWAY_TOKEN));
    return presented.size() == 1
        && MessageDigest.isEqual(
            expectedToken, presented.getFirst().getBytes(StandardCharsets.UTF_8));
  }

  /// 把请求里的四个身份头收集成 `HttpHeaders`，保留重复出现的值。
  ///
  /// @param request 当前请求
  /// @return 只含身份头的请求头集合
  private static HttpHeaders identityHeadersOf(HttpServletRequest request) {
    HttpHeaders headers = new HttpHeaders();
    for (String name : IdentityHeaders.IDENTITY_HEADER_NAMES) {
      Enumeration<String> values = request.getHeaders(name);
      while (values.hasMoreElements()) {
        headers.add(name, values.nextElement());
      }
    }
    return headers;
  }
}
