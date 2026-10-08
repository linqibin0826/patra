package dev.linqibin.patra.starter.security.assertion;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
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
  /// 未过期的断言；按头里的 `kid` 在集合里选公钥。任何一项不过，`decode` 抛 `JwtException`。
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
            // Nimbus 自己也查 typ，默认只认 JWT；不放行自定义值就到不了下面的校验器
            .jwtProcessorCustomizer(
                processor ->
                    processor.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(
                            new JOSEObjectType(IdentityAssertionClaims.TYPE))))
            .build();
    JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
    timestamps.setClock(clock);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTypeValidator(IdentityAssertionClaims.TYPE),
            new JwtIssuerValidator(IdentityAssertionClaims.ISSUER),
            new JwtAudienceValidator(IdentityAssertionClaims.AUDIENCE),
            timestamps));
    return decoder;
  }
}
