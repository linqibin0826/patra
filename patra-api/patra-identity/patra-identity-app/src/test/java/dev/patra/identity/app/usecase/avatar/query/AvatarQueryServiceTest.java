package dev.patra.identity.app.usecase.avatar.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.exception.AvatarNotFoundException;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.read.AvatarOwner;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.domain.model.vo.avatar.Avatar;
import dev.patra.identity.domain.model.vo.avatar.AvatarSize;
import dev.patra.identity.domain.model.vo.avatar.StoredAvatar;
import dev.patra.identity.domain.port.avatar.AvatarStoragePort;
import dev.patra.identity.domain.port.read.UserReadPort;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/// AvatarQueryService 单元测试：spec 8.3 的 404 / 命中矩阵。
class AvatarQueryServiceTest {

  private static final String HASH = "0123456789abcdef";
  private static final Avatar AVATAR = Avatar.of(42L, HASH);

  private final UserReadPort reader = mock(UserReadPort.class);
  private final AvatarStoragePort storage = mock(AvatarStoragePort.class);
  private final AvatarQueryService service = new AvatarQueryService(reader, storage);

  /// 默认：chen-yu 正常且有头像。
  @BeforeEach
  void stubOwner() {
    owner(UserStatus.ACTIVE, AVATAR);
  }

  @Test
  void should_locate_current_avatar() {
    AvatarRef ref = service.locate("chen-yu", "128", HASH);

    assertThat(ref).isEqualTo(AvatarRef.of(AVATAR, AvatarSize.SMALL));
    assertThat(ref.etag()).isEqualTo("\"0123456789abcdef-128\"");
  }

  @Test
  void should_normalize_username() {
    assertThat(service.locate(" Chen-Yu ", "512", HASH).size()).isEqualTo(AvatarSize.LARGE);
  }

  @Test
  void should_serve_during_pending_deletion() {
    owner(UserStatus.PENDING_DELETION, AVATAR);

    assertThat(service.locate("chen-yu", "512", HASH).avatar()).isEqualTo(AVATAR);
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "NULL",
      value = {
        "x!,512,0123456789abcdef",
        "chen-yu,256,0123456789abcdef",
        "chen-yu,NULL,0123456789abcdef",
        "chen-yu,512,NULL",
        "chen-yu,512,0123456789ABCDEF",
        "chen-yu,512,0123456789abcde"
      })
  void should_404_on_malformed_request(String username, String size, String version) {
    assertThatThrownBy(() -> service.locate(username, size, version))
        .isInstanceOf(AvatarNotFoundException.class);
  }

  @Test
  void should_404_when_user_missing() {
    when(reader.findAvatarOwner(Username.of("nobody"))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.locate("nobody", "512", HASH))
        .isInstanceOf(AvatarNotFoundException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = UserStatus.class,
      names = {"BANNED", "DELETED"})
  void should_404_for_banned_or_deleted(UserStatus status) {
    owner(status, AVATAR);

    assertThatThrownBy(() -> service.locate("chen-yu", "512", HASH))
        .isInstanceOf(AvatarNotFoundException.class);
  }

  @Test
  void should_404_without_avatar() {
    owner(UserStatus.ACTIVE, null);

    assertThatThrownBy(() -> service.locate("chen-yu", "512", HASH))
        .isInstanceOf(AvatarNotFoundException.class);
  }

  @Test
  void should_404_when_version_is_stale() {
    assertThatThrownBy(() -> service.locate("chen-yu", "512", "fedcba9876543210"))
        .isInstanceOf(AvatarNotFoundException.class);
  }

  @Test
  void should_open_through_storage() {
    StoredAvatar stored = StoredAvatar.of(new ByteArrayInputStream(new byte[] {1}), 1);
    when(storage.open(AVATAR, AvatarSize.SMALL)).thenReturn(stored);

    assertThat(service.open(AvatarRef.of(AVATAR, AvatarSize.SMALL))).isSameAs(stored);
  }

  @Test
  void should_propagate_missing_object() {
    when(storage.open(AVATAR, AvatarSize.SMALL)).thenThrow(new AvatarNotFoundException());

    assertThatThrownBy(() -> service.open(AvatarRef.of(AVATAR, AvatarSize.SMALL)))
        .isInstanceOf(AvatarNotFoundException.class);
  }

  /// 让 chen-yu 有给定状态与头像。
  ///
  /// @param status 状态
  /// @param avatar 头像，可空
  private void owner(UserStatus status, Avatar avatar) {
    when(reader.findAvatarOwner(Username.of("chen-yu")))
        .thenReturn(Optional.of(AvatarOwner.of(42L, status, avatar)));
  }
}
