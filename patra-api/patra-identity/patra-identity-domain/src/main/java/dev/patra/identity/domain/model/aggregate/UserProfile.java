package dev.patra.identity.domain.model.aggregate;

import dev.patra.identity.domain.model.vo.DisplayName;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;

/// 资料聚合根，与用户一对一，注册时随用户一起建。
///
/// 本 Issue 只有昵称：为空表示「跟随用户名」，读模型把它回退成当前用户名。简介、链接、专业背景、
/// 关注领域和隐私开关由 PAP-95 加在这个聚合上。
@Getter
public final class UserProfile {

  private Long id;
  private Long userId;

  @Getter(AccessLevel.NONE)
  private DisplayName displayName;

  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  /// 只能经工厂方法创建。
  private UserProfile() {}

  /// 给新用户建一份空资料：没有昵称。ID 由仓储在保存时分配。
  ///
  /// @param userId 用户 ID
  /// @return 资料
  public static UserProfile create(long userId) {
    UserProfile profile = new UserProfile();
    profile.userId = userId;
    return profile;
  }

  /// 从库里恢复。
  ///
  /// @param id 资料 ID
  /// @param userId 用户 ID
  /// @param displayName 昵称，没有时为 `null`
  /// @param version 乐观锁版本
  /// @param createdAt 创建时间
  /// @param updatedAt 更新时间
  /// @return 资料
  public static UserProfile restore(
      Long id,
      Long userId,
      DisplayName displayName,
      Long version,
      Instant createdAt,
      Instant updatedAt) {
    UserProfile profile = new UserProfile();
    profile.id = Objects.requireNonNull(id, "id 不能为 null");
    profile.userId = Objects.requireNonNull(userId, "userId 不能为 null");
    profile.displayName = displayName;
    profile.version = version;
    profile.createdAt = createdAt;
    profile.updatedAt = updatedAt;
    return profile;
  }

  /// 昵称。
  ///
  /// @return 昵称；没有设过时为空，展示时用用户名
  public Optional<DisplayName> getDisplayName() {
    return Optional.ofNullable(displayName);
  }

  /// 改昵称。
  ///
  /// @param displayName 新昵称
  public void rename(DisplayName displayName) {
    this.displayName = Objects.requireNonNull(displayName, "displayName 不能为 null");
  }

  /// 只输出 ID 和用户 ID。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "UserProfile[id=" + id + ", userId=" + userId + "]";
  }
}
