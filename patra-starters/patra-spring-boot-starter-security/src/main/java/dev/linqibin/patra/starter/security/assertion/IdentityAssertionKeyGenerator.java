package dev.linqibin.patra.starter.security.assertion;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/// 生成一对身份断言的签名密钥：私钥给网关，公钥给每个下游。
///
/// 由 Gradle 任务 `generateIdentityAssertionKey` 运行。私钥只写进指定的文件（权限 0600），
/// 不打到标准输出：Gradle 会把标准输出记进 daemon 日志，私钥不能留在那里。标准输出只有公钥。
public final class IdentityAssertionKeyGenerator {

  /// 私钥文件的权限：只有属主能读写。
  private static final Set<PosixFilePermission> OWNER_ONLY =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  /// 工具类，不允许实例化。
  private IdentityAssertionKeyGenerator() {}

  /// 命令行入口：`args[0]` 是私钥文件的路径。打印公钥 JWK Set 和私钥文件的位置。
  ///
  /// @param args 第一个参数是私钥文件的路径，文件不能已存在
  /// @throws IOException 写文件失败时
  /// @throws JOSEException 生成失败时
  public static void main(String[] args) throws IOException, JOSEException {
    if (args.length != 1 || args[0].isBlank()) {
      System.err.println("用法：generateIdentityAssertionKey <私钥文件路径>（Gradle 里用 -PkeyOut=<路径>）");
      System.exit(2);
    }
    Path privateKeyFile = Path.of(args[0]);
    String publicJwkSet = generateTo(privateKeyFile);
    System.out.println("私钥已写入（只给网关，由 secret 注入）：" + privateKeyFile.toAbsolutePath());
    System.out.println("公钥（给每个下游的 patra.security.identity-assertion.public-keys）：");
    System.out.println(publicJwkSet);
  }

  /// 生成一对 P-256 密钥，`kid` 是公钥的 JWK 指纹。私钥写进文件，返回公钥。
  ///
  /// @param privateKeyFile 私钥文件的路径，不能已存在；创建时权限 0600
  /// @return 只含公钥的 JWK Set JSON，一行
  /// @throws IOException 文件已存在或写入失败时
  /// @throws JOSEException 生成失败时
  public static String generateTo(Path privateKeyFile) throws IOException, JOSEException {
    ECKey key = new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
    Files.createFile(privateKeyFile, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
    Files.writeString(
        privateKeyFile, key.toJSONString() + System.lineSeparator(), StandardOpenOption.WRITE);
    return new JWKSet(key.toPublicJWK()).toString(true);
  }
}
