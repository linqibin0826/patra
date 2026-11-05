package dev.patra.identity.adapter.account.security;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.endpoint.DefaultMapOAuth2AccessTokenResponseConverter;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

/// 令牌响应的转换器：JSON 里有 `error` 字段时抛 {@link ProviderTokenRefusedException}，其余交给框架的默认实现。
///
/// RFC 6749 要求错误用 400，框架的错误处理器也只处理这种；GitHub 却回 200。错误码记一条 `WARN`：`incorrect_client_credentials`
/// 这类配置错误要靠它排查。`error_description` 不记。
@Slf4j
public final class ProviderErrorAwareTokenResponseConverter
    implements Converter<Map<String, Object>, OAuth2AccessTokenResponse> {

  private static final String ERROR_PARAMETER = "error";

  private final Converter<Map<String, Object>, OAuth2AccessTokenResponse> delegate =
      new DefaultMapOAuth2AccessTokenResponseConverter();

  /// 转换。
  ///
  /// @param source 令牌响应的 JSON
  /// @return 令牌响应
  /// @throws ProviderTokenRefusedException JSON 里是错误
  @Override
  public OAuth2AccessTokenResponse convert(Map<String, Object> source) {
    if (source.get(ERROR_PARAMETER) instanceof String error) {
      log.warn("平台用 2xx 拒绝了换令牌请求: error={}", error);
      throw new ProviderTokenRefusedException(error);
    }
    return delegate.convert(source);
  }
}
