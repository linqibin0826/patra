package dev.patra.identity.domain.account.model.aggregate;

import dev.patra.identity.domain.account.model.vo.PasswordHash;
import java.util.Objects;
import lombok.Getter;

/// 前台用户的密码凭据聚合根。注册、登录、再认证、改密码、重设密码时加载。
@Getter
public final class UserPasswordCredential {

  private Long id;
  private Long userId;
  private PasswordHash passwordHash;
  private Long version;

  /// 只能经工厂方法创建。
  private UserPasswordCredential() {}

  /// 给用户新建一份密码凭据。ID 由仓储在保存时分配。
  ///
  /// @param userId 用户 ID
  /// @param passwordHash 密码哈希
  /// @return 凭据
  public static UserPasswordCredential create(long userId, PasswordHash passwordHash) {
    UserPasswordCredential credential = new UserPasswordCredential();
    credential.userId = userId;
    credential.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash 不能为 null");
    return credential;
  }

  /// 从库里恢复。
  ///
  /// @param id 凭据 ID
  /// @param userId 用户 ID
  /// @param passwordHash 密码哈希
  /// @param version 乐观锁版本
  /// @return 凭据
  public static UserPasswordCredential restore(
      Long id, Long userId, PasswordHash passwordHash, Long version) {
    UserPasswordCredential credential = new UserPasswordCredential();
    credential.id = Objects.requireNonNull(id, "id 不能为 null");
    credential.userId = Objects.requireNonNull(userId, "userId 不能为 null");
    credential.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash 不能为 null");
    credential.version = version;
    return credential;
  }

  /// 换哈希。旧哈希不保留：不存历史密码。
  ///
  /// @param next 新密码的哈希
  public void rotate(PasswordHash next) {
    passwordHash = Objects.requireNonNull(next, "next 不能为 null");
  }

  /// 不输出哈希。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "UserPasswordCredential[id=" + id + ", userId=" + userId + "]";
  }
}
