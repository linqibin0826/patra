package dev.patra.identity.infra.account.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.exception.AvatarNotFoundException;
import dev.patra.identity.domain.account.exception.AvatarStorageException;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRenditions;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarSize;
import dev.patra.identity.domain.account.model.vo.avatar.StoredAvatar;
import dev.patra.identity.infra.config.IdentityITMinIOContainerInitializer;
import dev.patra.tool.objectstorage.ObjectStorageOperations;
import dev.patra.tool.objectstorage.autoconfigure.ObjectStorageAutoConfiguration;
import dev.patra.tool.objectstorage.domain.ObjectInfo;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `AvatarStorageAdapter` 集成测试（真实 MinIO）。
@SpringBootTest(classes = AvatarStorageAdapter.class)
@ImportAutoConfiguration(ObjectStorageAutoConfiguration.class)
@EnableConfigurationProperties(AvatarStorageProperties.class)
@ContextConfiguration(initializers = IdentityITMinIOContainerInitializer.class)
@ActiveProfiles("test")
class AvatarStorageAdapterIT {

  private static final String BUCKET = "patra-identity-avatar";
  private static final AvatarRenditions RENDITIONS =
      AvatarRenditions.of(new byte[] {1, 2, 3, 4}, new byte[] {5, 6});

  @Autowired private AvatarStorageAdapter adapter;
  @Autowired private ObjectStorageOperations storage;

  @Test
  void should_store_both_sizes_as_webp() {
    Avatar avatar = Avatar.of(101L, "0123456789abcdef");

    adapter.store(avatar, RENDITIONS);

    ObjectInfo large = storage.statObject(BUCKET, avatar.objectKey(AvatarSize.LARGE)).orElseThrow();
    ObjectInfo small = storage.statObject(BUCKET, avatar.objectKey(AvatarSize.SMALL)).orElseThrow();
    assertThat(large.getContentType()).isEqualTo("image/webp");
    assertThat(large.getContentLength()).isEqualTo(4);
    assertThat(small.getContentType()).isEqualTo("image/webp");
    assertThat(small.getContentLength()).isEqualTo(2);
  }

  @Test
  void should_open_stored_bytes() throws Exception {
    Avatar avatar = Avatar.of(102L, "0123456789abcdef");
    adapter.store(avatar, RENDITIONS);

    StoredAvatar stored = adapter.open(avatar, AvatarSize.LARGE);

    try (InputStream content = stored.content()) {
      assertThat(content.readAllBytes()).containsExactly(1, 2, 3, 4);
    }
    assertThat(stored.length()).isEqualTo(4);
  }

  @Test
  void should_throw_not_found_when_missing() {
    assertThatThrownBy(() -> adapter.open(Avatar.of(103L, "0123456789abcdef"), AvatarSize.SMALL))
        .isInstanceOf(AvatarNotFoundException.class);
  }

  @Test
  void should_delete_both_sizes() {
    Avatar avatar = Avatar.of(104L, "0123456789abcdef");
    adapter.store(avatar, RENDITIONS);

    adapter.delete(avatar);

    assertThat(storage.statObject(BUCKET, avatar.objectKey(AvatarSize.LARGE))).isEmpty();
    assertThat(storage.statObject(BUCKET, avatar.objectKey(AvatarSize.SMALL))).isEmpty();
  }

  @Test
  void should_delete_all_only_for_that_user() {
    Avatar one = Avatar.of(1L, "0123456789abcdef");
    Avatar ten = Avatar.of(10L, "0123456789abcdef");
    adapter.store(one, RENDITIONS);
    adapter.store(Avatar.of(1L, "fedcba9876543210"), RENDITIONS);
    adapter.store(ten, RENDITIONS);

    adapter.deleteAll(1L);

    assertThat(storage.statObject(BUCKET, one.objectKey(AvatarSize.LARGE))).isEmpty();
    assertThat(storage.statObject(BUCKET, ten.objectKey(AvatarSize.LARGE))).isPresent();
  }

  @Test
  void should_wrap_storage_failure() {
    AvatarStorageAdapter broken =
        new AvatarStorageAdapter(storage, new AvatarStorageProperties("Bad_Bucket"));

    assertThatThrownBy(() -> broken.store(Avatar.of(105L, "0123456789abcdef"), RENDITIONS))
        .isInstanceOf(AvatarStorageException.class);
  }
}
