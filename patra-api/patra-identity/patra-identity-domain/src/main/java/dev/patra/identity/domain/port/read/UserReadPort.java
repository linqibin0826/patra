package dev.patra.identity.domain.port.read;

import dev.patra.identity.domain.model.read.PublicUserReadModel;
import dev.patra.identity.domain.model.read.UserAccountReadModel;
import dev.patra.identity.domain.model.vo.Username;
import java.util.Optional;

/// 前台用户的读端口。读模型把用户表和资料表拼起来，昵称为空时回退成用户名。
public interface UserReadPort {

  /// 按 ID 读账号信息。
  ///
  /// @param userId 用户 ID
  /// @return 账号信息；不存在时为空
  Optional<UserAccountReadModel> findAccount(long userId);

  /// 按用户名读公开身份卡，包括已注销、已封禁的用户：可见性由调用方判。
  ///
  /// @param username 用户名
  /// @return 身份卡；不存在时为空
  Optional<PublicUserReadModel> findPublicIdentity(Username username);

  /// 按用户 ID 读公开身份卡，给旧名跳转取原主的当前身份卡。
  ///
  /// @param userId 用户 ID
  /// @return 身份卡；不存在时为空
  Optional<PublicUserReadModel> findPublicIdentityByUserId(long userId);
}
