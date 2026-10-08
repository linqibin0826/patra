package dev.linqibin.patra.starter.security.authentication;

import dev.linqibin.patra.starter.security.assertion.IdentityAssertionClaims;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.AuthenticationConverter;

/// 把 `Authorization: Bearer` 里的身份断言变成认证对象。
///
/// 没带断言按匿名；断言无效返回 401；签名正确但内容不合法返回 500（只可能是网关的缺陷）。
/// 断言只可能来自网关，无效说明配置错了或有人伪造，所以不当成匿名静默放过。
@Slf4j
public final class IdentityAssertionAuthenticationConverter implements AuthenticationConverter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtDecoder decoder;

  /// 创建转换器。
  ///
  /// @param decoder 用网关公钥建的解码器
  public IdentityAssertionAuthenticationConverter(JwtDecoder decoder) {
    this.decoder = Objects.requireNonNull(decoder, "decoder 不能为 null");
  }

  /// 从请求头建立认证。
  ///
  /// @param request 当前请求
  /// @return 认证对象；没带断言时返回 `null`
  /// @throws BadCredentialsException `Authorization` 不是单个 Bearer 断言，或断言无效时
  /// @throws MalformedIdentityException 断言签名正确但内容不合法时
  @Override
  public Authentication convert(HttpServletRequest request) {
    List<String> values = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
    if (values.isEmpty()) {
      return null;
    }
    if (values.size() > 1) {
      throw reject(request, "Authorization 头出现多次");
    }
    String value = values.getFirst();
    if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      throw reject(request, "Authorization 头不是 Bearer 方案");
    }
    Jwt jwt;
    try {
      jwt = decoder.decode(value.substring(BEARER_PREFIX.length()).strip());
    } catch (JwtException e) {
      throw reject(request, e.getMessage());
    }
    return new CurrentUserAuthentication(
        IdentityAssertionClaims.toCurrentUser(jwt), jwt.getTokenValue());
  }

  /// 记一行警告并构造 401 对应的异常。日志里只有原因，没有断言的内容。
  ///
  /// @param request 当前请求
  /// @param reason 拒绝的原因
  /// @return 给调用方抛出的异常
  private static BadCredentialsException reject(HttpServletRequest request, String reason) {
    log.warn("拒绝身份断言: {} {}，原因: {}", request.getMethod(), request.getRequestURI(), reason);
    return new BadCredentialsException("身份断言无效");
  }
}
