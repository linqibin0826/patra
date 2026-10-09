package dev.linqibin.patra.identity.domain.port.repository;

import dev.linqibin.patra.identity.domain.model.aggregate.UserLoginRecord;
import java.util.Optional;

/// 登录记录仓储。
public interface UserLoginRecordRepository {

  /// 保存。新记录分配雪花 ID。
  ///
  /// @param login 记录
  /// @return 保存后的记录，带 ID 和版本
  UserLoginRecord save(UserLoginRecord login);

  /// 按 ID 查，ID 就是会话 ID。
  ///
  /// @param id 记录 ID
  /// @return 记录
  Optional<UserLoginRecord> findById(long id);
}
