package dev.linqibin.patra.starter.security.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.text.ParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// IdentityAssertionKeyGenerator 单元测试。
@DisplayName("IdentityAssertionKeyGenerator 单元测试")
class IdentityAssertionKeyGeneratorTest {

  @Test
  @DisplayName("私钥只写进文件、只有属主可读；返回的公钥 JWK Set 不含 d，kid 相同且等于指纹")
  void should_write_private_jwk_to_owner_only_file_and_return_public_jwk_set(@TempDir Path dir)
      throws IOException, ParseException, JOSEException {
    Path privateKeyFile = dir.resolve("identity-assertion-private.jwk");

    String publicJwkSet = IdentityAssertionKeyGenerator.generateTo(privateKeyFile);

    ECKey privateKey = ECKey.parse(Files.readString(privateKeyFile).strip());
    JWKSet publicSet = JWKSet.parse(publicJwkSet);
    JWK publicKey = publicSet.getKeys().getFirst();
    assertThat(privateKey.isPrivate()).isTrue();
    assertThat(privateKey.getCurve()).isEqualTo(Curve.P_256);
    assertThat(Files.getPosixFilePermissions(privateKeyFile))
        .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    assertThat(publicSet.getKeys()).hasSize(1);
    assertThat(publicKey.isPrivate()).isFalse();
    assertThat(publicKey.getKeyID())
        .isEqualTo(privateKey.getKeyID())
        .isEqualTo(privateKey.computeThumbprint().toString());
    assertThat(publicJwkSet).doesNotContain("\n").doesNotContain("\"d\"");
  }

  @Test
  @DisplayName("目标文件已存在：拒绝覆盖")
  void should_refuse_to_overwrite_existing_file(@TempDir Path dir) throws IOException {
    Path existing = Files.writeString(dir.resolve("existing.jwk"), "keep me");

    assertThatThrownBy(() -> IdentityAssertionKeyGenerator.generateTo(existing))
        .isInstanceOf(IOException.class);
    assertThat(Files.readString(existing)).isEqualTo("keep me");
  }

  @Test
  @DisplayName("每次生成的密钥都不同")
  void should_generate_fresh_key_each_time(@TempDir Path dir) throws IOException, JOSEException {
    IdentityAssertionKeyGenerator.generateTo(dir.resolve("first.jwk"));
    IdentityAssertionKeyGenerator.generateTo(dir.resolve("second.jwk"));

    assertThat(Files.readString(dir.resolve("first.jwk")))
        .isNotEqualTo(Files.readString(dir.resolve("second.jwk")));
  }
}
