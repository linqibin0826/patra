package dev.patra.identity.domain.session.port.repository;

import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.vo.DeviceId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

  /// 按 ID 批量查，不存在的跳过。
  ///
  /// @param ids 记录 ID
  /// @return 存在的记录，顺序不保证
  List<UserLoginRecord> findAllByIds(Collection<Long> ids);

  /// 这个账号是不是「见过」这台设备：有一条同设备的记录，它结束或过期的时刻不早于 `since`，
  /// 不是被本人在设备列表里踢掉的（`REVOKED`），也不早于这台设备任何一条被踢掉的记录：踢掉一台设备
  /// 就不再认它之前的登录，只认踢掉之后的。新设备邮件据此判定。
  ///
  /// @param userId 用户 ID
  /// @param deviceId 设备标识
  /// @param since 回看窗口的起点
  /// @return 见过时为 `true`
  boolean existsTrustedDevice(long userId, DeviceId deviceId, Instant since);

  /// 删掉「结束或过期的时刻」早于截止时间的记录，一次最多一批。
  ///
  /// @param cutoff 截止时间
  /// @param batchSize 一批最多几行
  /// @return 删了几行
  int deleteRetiredBefore(Instant cutoff, int batchSize);
}
