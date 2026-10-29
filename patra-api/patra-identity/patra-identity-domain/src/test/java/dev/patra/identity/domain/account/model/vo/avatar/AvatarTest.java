package dev.patra.identity.domain.account.model.vo.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AvatarTest {

  private static final String HASH = "0123456789abcdef";

  @Test
  void should_build_object_keys_for_both_sizes() {
    Avatar avatar = Avatar.of(42L, HASH);

    assertThat(avatar.objectKey(AvatarSize.LARGE)).isEqualTo("avatar/42/0123456789abcdef-512.webp");
    assertThat(avatar.objectKey(AvatarSize.SMALL)).isEqualTo("avatar/42/0123456789abcdef-128.webp");
  }

  @Test
  void should_build_quoted_etag() {
    assertThat(Avatar.of(42L, HASH).etag(AvatarSize.SMALL)).isEqualTo("\"0123456789abcdef-128\"");
  }

  @Test
  void should_build_user_prefix() {
    assertThat(Avatar.prefix(42L)).isEqualTo("avatar/42/");
  }

  @Test
  void should_reject_malformed_hash() {
    assertThatThrownBy(() -> Avatar.of(42L, "0123456789ABCDEF"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Avatar.of(42L, "0123456789abcde"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Avatar.of(42L, null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_parse_large_object_key() {
    assertThat(Avatar.parse(42L, "avatar/42/0123456789abcdef-512.webp"))
        .isEqualTo(Avatar.of(42L, HASH));
  }

  @Test
  void should_reject_key_of_another_user_or_wrong_shape() {
    assertThatThrownBy(() -> Avatar.parse(42L, "avatar/43/0123456789abcdef-512.webp"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Avatar.parse(42L, "avatar/42/0123456789abcdef-128.webp"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Avatar.parse(42L, "avatar/42/0123456789ABCDEF-512.webp"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Avatar.parse(42L, "avatar/42/x-512.webp"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_hash_content_with_sha256_prefix() {
    assertThat(Avatar.forContent(1L, "abc".getBytes(StandardCharsets.UTF_8)).hash())
        .isEqualTo("ba7816bf8f01cfea");
  }

  @Test
  void should_parse_size_from_pixels() {
    assertThat(AvatarSize.fromPixels("512")).contains(AvatarSize.LARGE);
    assertThat(AvatarSize.fromPixels("128")).contains(AvatarSize.SMALL);
    assertThat(AvatarSize.fromPixels("256")).isEmpty();
    assertThat(AvatarSize.fromPixels("abc")).isEmpty();
    assertThat(AvatarSize.fromPixels(null)).isEmpty();
  }
}
