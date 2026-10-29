package dev.patra.identity.domain.account.port.repository;

import dev.patra.identity.domain.account.model.aggregate.FormerUsername;
import dev.patra.identity.domain.account.model.vo.Username;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/// 旧用户名仓储。保留期按传入的时刻判断，不读系统时钟。
public interface FormerUsernameRepository {

  /// 保存旧名记录。新记录在这里分配雪花 ID。
  ///
  /// @param formerUsername 旧名记录
  /// @return 保存后的记录，带 ID 和版本
  FormerUsername save(FormerUsername formerUsername);

  /// 查某个名字在此刻仍在保留期内的记录，多条时取改名时间最近的一条。
  ///
  /// @param username 名字
  /// @param now 时刻
  /// @return 记录；没有或都已到期时为空
  Optional<FormerUsername> findReserved(Username username, Instant now);

  /// 批量查一组名字在此刻仍在保留期内的记录（用户名批量解析，PAP-118），每个名字最多一条：多条时取改名
  /// 时间最近的。
  ///
  /// @param usernames 名字
  /// @param now 时刻
  /// @return 记录，不保证顺序；没有或都已到期的名字不在其中
  List<FormerUsername> findAllReserved(Collection<Username> usernames, Instant now);

  /// 某个名字在此刻是否被任何人保留着。注册时用。
  ///
  /// @param username 名字
  /// @param now 时刻
  /// @return 保留中为 `true`
  boolean existsReserved(Username username, Instant now);

  /// 某个名字在此刻是否被别人保留着。改名时用：自己的旧名可以改回。
  ///
  /// @param username 名字
  /// @param userId 本人的用户 ID
  /// @param now 时刻
  /// @return 被别人保留中为 `true`
  boolean existsReservedByOther(Username username, long userId, Instant now);
}
