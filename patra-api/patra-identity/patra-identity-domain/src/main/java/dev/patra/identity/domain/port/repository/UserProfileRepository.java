package dev.patra.identity.domain.port.repository;

import dev.patra.identity.domain.exception.UserModifiedConcurrentlyException;
import dev.patra.identity.domain.model.aggregate.UserProfile;
import java.util.Optional;

/// 资料仓储。
public interface UserProfileRepository {

  /// 按用户 ID 查资料。
  ///
  /// @param userId 用户 ID
  /// @return 资料；不存在时为空
  Optional<UserProfile> findByUserId(long userId);

  /// 保存资料。新资料在这里分配雪花 ID。
  ///
  /// @param profile 资料
  /// @return 保存后的资料，带 ID 和版本
  /// @throws UserModifiedConcurrentlyException 撞上乐观锁
  UserProfile save(UserProfile profile);
}
