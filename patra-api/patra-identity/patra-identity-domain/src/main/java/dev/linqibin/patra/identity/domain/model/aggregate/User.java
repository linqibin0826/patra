package dev.linqibin.patra.identity.domain.model.aggregate;

import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

/// 前台用户聚合根：账号本身和封禁。密码在 {@link UserPasswordCredential} 里，不跟着用户走。
@Getter
public final class User {

  private Long id;
  private EmailAddress email;
  private UserStatus status;
  private Instant bannedAt;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  /// 只能经工厂方法创建。
  private User() {}

  /// 注册一个新用户，状态为正常。ID 由仓储在保存时分配。
  ///
  /// @param email 规范化之后的邮箱
  /// @return 新用户
  public static User register(EmailAddress email) {
    User user = new User();
    user.email = Objects.requireNonNull(email, "email 不能为 null");
    user.status = UserStatus.ACTIVE;
    return user;
  }

  /// 从库里恢复。
  ///
  /// @param id 用户 ID
  /// @param email 邮箱
  /// @param status 状态
  /// @param bannedAt 封禁时间，未封禁时为 `null`
  /// @param version 乐观锁版本
  /// @param createdAt 创建时间
  /// @param updatedAt 更新时间
  /// @return 用户
  public static User restore(
      Long id,
      EmailAddress email,
      UserStatus status,
      Instant bannedAt,
      Long version,
      Instant createdAt,
      Instant updatedAt) {
    User user = new User();
    user.id = Objects.requireNonNull(id, "id 不能为 null");
    user.email = Objects.requireNonNull(email, "email 不能为 null");
    user.status = Objects.requireNonNull(status, "status 不能为 null");
    user.bannedAt = bannedAt;
    user.version = version;
    user.createdAt = createdAt;
    user.updatedAt = updatedAt;
    return user;
  }

  /// 封禁。已经封禁时什么都不做，保留原来的封禁时间。
  ///
  /// @param now 当前时间
  public void ban(Instant now) {
    Objects.requireNonNull(now, "now 不能为 null");
    if (status == UserStatus.BANNED) {
      return;
    }
    status = UserStatus.BANNED;
    bannedAt = now;
  }

  /// 解封。已经正常时什么都不做。
  public void unban() {
    if (status == UserStatus.ACTIVE) {
      return;
    }
    status = UserStatus.ACTIVE;
    bannedAt = null;
  }

  /// 是否已封禁。
  ///
  /// @return 封禁时为 `true`
  public boolean isBanned() {
    return status == UserStatus.BANNED;
  }

  /// 只输出 ID 和状态，不输出邮箱。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "User[id=" + id + ", status=" + status + "]";
  }
}
