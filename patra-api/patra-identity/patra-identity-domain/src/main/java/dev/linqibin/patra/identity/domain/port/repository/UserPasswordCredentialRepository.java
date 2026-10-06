package dev.linqibin.patra.identity.domain.port.repository;

import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import java.util.Optional;

/// 前台用户密码凭据的仓储。
public interface UserPasswordCredentialRepository {

  /// 按用户 ID 查凭据。
  ///
  /// @param userId 用户 ID
  /// @return 凭据；不存在时为空
  Optional<UserPasswordCredential> findByUserId(long userId);

  /// 保存凭据。新凭据在这里分配雪花 ID。
  ///
  /// @param credential 凭据
  /// @return 保存后的凭据
  UserPasswordCredential save(UserPasswordCredential credential);
}
