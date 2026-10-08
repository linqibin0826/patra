package dev.linqibin.patra.starter.security.assertion;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/// 身份断言的验签器工厂：下游用网关的公钥验签。
public final class IdentityAssertionDecoders {

  /// 验签时允许的时钟偏差。
  private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

  /// 工具类，不允许实例化。
  private IdentityAssertionDecoders() {}

  /// 用一组公钥建一个解码器。
  ///
  /// 只接受 ES256 签名、`typ` 为 `patra-identity+jwt`、`iss` 为 `patra-gateway`、`aud` 含 `patra`、
  /// 带 `iat` 和 `exp` 且有效期不超过 60 秒、未过期的断言；按头里的 `kid` 在集合里选公钥。
  /// 任何一项不过，`decode` 抛 `JwtException`。
  ///
  /// 所有声明的校验都在验签之后：验签之前不读未签名的内容，错误信息里也就不会带上它。
  ///
  /// @param publicKeys 公钥集合，每把都带 `kid`
  /// @param clock 判断过期用的时钟
  /// @return 解码器
  public static JwtDecoder forPublicKeys(JWKSet publicKeys, Clock clock) {
    Objects.requireNonNull(publicKeys, "publicKeys 不能为 null");
    Objects.requireNonNull(clock, "clock 不能为 null");
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<SecurityContext>(publicKeys))
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            .build();
    // Spring 默认的声明转换器会把缺失的 iat 补成 exp 减一秒；这里不补，缺 iat 就是缺
    MappedJwtClaimSetConverter defaults = MappedJwtClaimSetConverter.withDefaults(Map.of());
    decoder.setClaimSetConverter(
        claims -> {
          Map<String, Object> converted = new HashMap<>(defaults.convert(claims));
          if (!claims.containsKey(JwtClaimNames.IAT)) {
            converted.remove(JwtClaimNames.IAT);
          }
          return converted;
        });
    JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
    timestamps.setClock(clock);
    // 没有 exp 的断言不放行：有效期不能只靠签名方自觉
    timestamps.setAllowEmptyExpiryClaim(false);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTypeValidator(IdentityAssertionClaims.TYPE),
            new JwtIssuerValidator(IdentityAssertionClaims.ISSUER),
            new JwtAudienceValidator(IdentityAssertionClaims.AUDIENCE),
            timestamps,
            IdentityAssertionDecoders::validateLifetime));
    return decoder;
  }

  /// 断言必须带 `iat`，且 `exp` 减 `iat` 不超过设计定下的有效期。
  ///
  /// 验签器自己只查「现在没过期」；这里再把有效期的长度钉死，签名方签出长命断言也会被拒。
  ///
  /// @param jwt 已验签的断言
  /// @return 校验结果
  private static OAuth2TokenValidatorResult validateLifetime(Jwt jwt) {
    Instant issuedAt = jwt.getIssuedAt();
    Instant expiresAt = jwt.getExpiresAt();
    if (issuedAt == null
        || expiresAt == null
        || Duration.between(issuedAt, expiresAt).compareTo(IdentityAssertionClaims.LIFETIME) > 0) {
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "断言缺 iat 或有效期超过 60 秒", null));
    }
    return OAuth2TokenValidatorResult.success();
  }
}
