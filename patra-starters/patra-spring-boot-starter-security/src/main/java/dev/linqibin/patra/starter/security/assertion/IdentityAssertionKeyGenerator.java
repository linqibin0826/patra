package dev.linqibin.patra.starter.security.assertion;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

/// 生成一对身份断言的签名密钥：私钥给网关，公钥给每个下游。
///
/// 由 Gradle 任务 `generateIdentityAssertionKey` 运行，输出两行 JSON。生成的密钥不落盘，
/// 由运维把两行分别注入网关的 secret 和下游的配置（PAP-66 的 runbook）。
public final class IdentityAssertionKeyGenerator {

  /// 工具类，不允许实例化。
  private IdentityAssertionKeyGenerator() {}

  /// 命令行入口：先打私钥 JWK，再打公钥 JWK Set，各占一行。
  ///
  /// @param args 不用
  /// @throws JOSEException 生成失败时
  public static void main(String[] args) throws JOSEException {
    GeneratedKey generated = generate();
    System.out.println("私钥（只给网关，由 secret 注入）：");
    System.out.println(generated.privateJwk());
    System.out.println("公钥（给每个下游的 patra.security.identity-assertion.public-keys）：");
    System.out.println(generated.publicJwkSet());
  }

  /// 生成一对 P-256 密钥，`kid` 是公钥的 JWK 指纹。
  ///
  /// @return 私钥 JWK 和公钥 JWK Set，都是一行 JSON
  /// @throws JOSEException 生成失败时
  public static GeneratedKey generate() throws JOSEException {
    ECKey key = new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
    return new GeneratedKey(key.toJSONString(), new JWKSet(key.toPublicJWK()).toString(true));
  }

  /// 生成结果。
  ///
  /// @param privateJwk 含私钥的 JWK JSON，一行
  /// @param publicJwkSet 只含公钥的 JWK Set JSON，一行
  public record GeneratedKey(String privateJwk, String publicJwkSet) {}
}
