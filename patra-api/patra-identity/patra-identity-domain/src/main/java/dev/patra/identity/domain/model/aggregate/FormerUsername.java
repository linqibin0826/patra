package dev.patra.identity.domain.model.aggregate;

import dev.patra.identity.domain.model.vo.Username;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

/// 一个用户改名后留下的旧名。
///
/// `reservedUntil` 之前只有原主能改回，别人不能注册或改成它，`/users/旧名` 跳到新名；到期后行留作审计，
/// 不再起任何作用。行只插不改，所以没有更新时间。
@Getter
public final class FormerUsername {

  private Long id;
  private Long userId;
  private Username username;
  private Instant replacedAt;
  private Instant reservedUntil;
  private Long version;

  /// 只能经工厂方法创建。
  private FormerUsername() {}

  /// 改名时把旧名保留起来。ID 由仓储在保存时分配。
  ///
  /// @param userId 原主的用户 ID
  /// @param username 旧名
  /// @param replacedAt 改名时间
  /// @param reservationPeriod 保留多久
  /// @return 旧名记录
  public static FormerUsername reserve(
      long userId, Username username, Instant replacedAt, Duration reservationPeriod) {
    FormerUsername former = new FormerUsername();
    former.userId = userId;
    former.username = Objects.requireNonNull(username, "username 不能为 null");
    former.replacedAt = Objects.requireNonNull(replacedAt, "replacedAt 不能为 null");
    former.reservedUntil =
        replacedAt.plus(Objects.requireNonNull(reservationPeriod, "reservationPeriod 不能为 null"));
    return former;
  }

  /// 从库里恢复。
  ///
  /// @param id 记录 ID
  /// @param userId 原主的用户 ID
  /// @param username 旧名
  /// @param replacedAt 改名时间
  /// @param reservedUntil 保留到期时间
  /// @param version 乐观锁版本
  /// @return 旧名记录
  public static FormerUsername restore(
      Long id,
      Long userId,
      Username username,
      Instant replacedAt,
      Instant reservedUntil,
      Long version) {
    FormerUsername former = new FormerUsername();
    former.id = Objects.requireNonNull(id, "id 不能为 null");
    former.userId = Objects.requireNonNull(userId, "userId 不能为 null");
    former.username = Objects.requireNonNull(username, "username 不能为 null");
    former.replacedAt = Objects.requireNonNull(replacedAt, "replacedAt 不能为 null");
    former.reservedUntil = Objects.requireNonNull(reservedUntil, "reservedUntil 不能为 null");
    former.version = version;
    return former;
  }

  /// 在某个时刻是否仍在保留期内。到期当刻不再保留。
  ///
  /// @param now 时刻
  /// @return 仍保留时为 `true`
  public boolean isReservedAt(Instant now) {
    return Objects.requireNonNull(now, "now 不能为 null").isBefore(reservedUntil);
  }

  /// 描述。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "FormerUsername[id="
        + id
        + ", userId="
        + userId
        + ", username="
        + username.value()
        + ", reservedUntil="
        + reservedUntil
        + "]";
  }
}
