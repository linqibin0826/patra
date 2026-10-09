package dev.linqibin.patra.identity.domain.port.read;

import dev.linqibin.patra.identity.domain.model.read.UserAccountReadModel;
import java.util.Optional;

/// 前台用户的读端口。
public interface UserReadPort {

  /// 按 ID 读账号信息。
  ///
  /// @param userId 用户 ID
  /// @return 账号信息；不存在时为空
  Optional<UserAccountReadModel> findAccount(long userId);
}
