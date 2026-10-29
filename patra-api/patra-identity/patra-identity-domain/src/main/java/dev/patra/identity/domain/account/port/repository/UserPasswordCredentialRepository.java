package dev.patra.identity.domain.account.port.repository;

import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import java.util.Optional;

/// 前台用户密码凭据的仓储。
public interface UserPasswordCredentialRepository {

  /// 按用户 ID 查凭据。
  ///
  /// @param userId 用户 ID
  /// @return 凭据；不存在时为空
  Optional<UserPasswordCredential> findByUserId(long userId);

  /// 按用户 ID 删凭据：撤销改邮箱时作废密码用。之后登录按「凭据不存在」走假哈希路径返回 401，
  /// 重设密码时新建一行。
  ///
  /// @param userId 用户 ID
  /// @return 真的删掉了一行时为 `true`
  boolean deleteByUserId(long userId);

  /// 保存凭据。新凭据在这里分配雪花 ID。
  ///
  /// @param credential 凭据
  /// @return 保存后的凭据
  UserPasswordCredential save(UserPasswordCredential credential);
}
