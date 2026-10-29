package dev.patra.identity.domain.account.model.aggregate;

import dev.patra.identity.domain.account.exception.InvalidUserStateTransitionException;
import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.exception.UsernameChangeCooldownException;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.policy.UsernameChangePolicy;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/// 前台用户聚合根：账号本身、用户名、邮箱验证状态和四态状态机。
/// 密码在 {@link UserPasswordCredential}，昵称在 {@link UserProfile}，都不跟着用户走。
///
/// 状态迁移：`ACTIVE ⇄ BANNED`（后台）；`ACTIVE → PENDING_DELETION → ACTIVE / DELETED`（本人申请、
/// 取消、到期执行）；`BANNED → DELETED`（后台）；`PENDING_DELETION → BANNED` 作废注销申请；
/// `DELETED` 不能回退。进入 `DELETED` 时邮箱、验证时间、到期时间、封禁时间全部清空，用户名和 ID 留着。
/// 注销提醒标记（到期前提醒发过没有）只在冷静期有值，取消、封禁、完成注销都清掉它。
@Getter
public final class User {

  private Long id;

  @Getter(AccessLevel.NONE)
  private EmailAddress email;

  private Instant emailVerifiedAt;
  private Username username;
  private Instant usernameChangedAt;
  private UserStatus status;
  private Instant bannedAt;
  private Instant deletionDueAt;
  private Instant deletionRemindedAt;
  private Instant deletedAt;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  /// 只能经工厂方法创建。
  private User() {}

  /// 从库里恢复。构建器名叫 `restore`：`User.restore().id(…).username(…).status(…).build()`。
  ///
  /// @param id 用户 ID
  /// @param email 邮箱，只有 `DELETED` 可以为 `null`
  /// @param emailVerifiedAt 邮箱验证时间，未验证时为 `null`
  /// @param username 用户名
  /// @param usernameChangedAt 最近一次改名时间，没改过为 `null`
  /// @param status 状态
  /// @param bannedAt 封禁时间
  /// @param deletionDueAt 注销到期时间
  /// @param deletionRemindedAt 到期前提醒的发送时间，没发过为 `null`
  /// @param deletedAt 注销完成时间
  /// @param version 乐观锁版本
  /// @param createdAt 创建时间
  /// @param updatedAt 更新时间
  @Builder(builderMethodName = "restore")
  private User(
      Long id,
      EmailAddress email,
      Instant emailVerifiedAt,
      Username username,
      Instant usernameChangedAt,
      UserStatus status,
      Instant bannedAt,
      Instant deletionDueAt,
      Instant deletionRemindedAt,
      Instant deletedAt,
      Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id 不能为 null");
    this.username = Objects.requireNonNull(username, "username 不能为 null");
    this.status = Objects.requireNonNull(status, "status 不能为 null");
    if (status != UserStatus.DELETED && email == null) {
      throw new IllegalArgumentException("只有已注销的用户可以没有邮箱");
    }
    this.email = email;
    this.emailVerifiedAt = emailVerifiedAt;
    this.usernameChangedAt = usernameChangedAt;
    this.bannedAt = bannedAt;
    this.deletionDueAt = deletionDueAt;
    this.deletionRemindedAt = deletionRemindedAt;
    this.deletedAt = deletedAt;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /// 注册一个新用户：正常状态、邮箱未验证。ID 由仓储在保存时分配。
  ///
  /// @param email 规范化之后的邮箱
  /// @param username 规范化之后的用户名
  /// @return 新用户
  public static User register(EmailAddress email, Username username) {
    User user = new User();
    user.email = Objects.requireNonNull(email, "email 不能为 null");
    user.username = Objects.requireNonNull(username, "username 不能为 null");
    user.status = UserStatus.ACTIVE;
    return user;
  }

  /// 邮箱。
  ///
  /// @return 邮箱；已注销的用户没有
  public Optional<EmailAddress> getEmail() {
    return Optional.ofNullable(email);
  }

  /// 邮箱是否已验证。
  ///
  /// @return 已验证时为 `true`
  public boolean isEmailVerified() {
    return emailVerifiedAt != null;
  }

  /// 是否正常。
  ///
  /// @return 正常时为 `true`
  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  /// 是否已封禁。
  ///
  /// @return 封禁时为 `true`
  public boolean isBanned() {
    return status == UserStatus.BANNED;
  }

  /// 是否在注销冷静期。
  ///
  /// @return 冷静期内为 `true`
  public boolean isPendingDeletion() {
    return status == UserStatus.PENDING_DELETION;
  }

  /// 是否已注销。
  ///
  /// @return 已注销时为 `true`
  public boolean isDeleted() {
    return status == UserStatus.DELETED;
  }

  /// 封禁。已经封禁时什么都不做，保留原来的封禁时间；冷静期内被封禁，注销申请作废（连同提醒标记）。
  ///
  /// @param now 当前时间
  /// @throws InvalidUserStateTransitionException 已注销
  public void ban(Instant now) {
    Objects.requireNonNull(now, "now 不能为 null");
    switch (status) {
      case BANNED -> {}
      case DELETED -> throw transition("ban");
      case ACTIVE, PENDING_DELETION -> {
        status = UserStatus.BANNED;
        bannedAt = now;
        deletionDueAt = null;
        deletionRemindedAt = null;
      }
    }
  }

  /// 解封。没被封禁时什么都不做。
  ///
  /// @throws InvalidUserStateTransitionException 已注销
  public void unban() {
    switch (status) {
      case ACTIVE, PENDING_DELETION -> {}
      case DELETED -> throw transition("unban");
      case BANNED -> {
        status = UserStatus.ACTIVE;
        bannedAt = null;
      }
    }
  }

  /// 申请注销：进入冷静期，到期时间是现在加冷静期。已在冷静期内什么都不做，保留原到期时间。
  ///
  /// @param now 当前时间
  /// @param coolingPeriod 冷静期
  /// @throws InvalidUserStateTransitionException 已封禁（不能自助注销）或已注销
  public void requestDeletion(Instant now, Duration coolingPeriod) {
    Objects.requireNonNull(now, "now 不能为 null");
    Objects.requireNonNull(coolingPeriod, "coolingPeriod 不能为 null");
    switch (status) {
      case PENDING_DELETION -> {}
      case BANNED, DELETED -> throw transition("requestDeletion");
      case ACTIVE -> {
        status = UserStatus.PENDING_DELETION;
        deletionDueAt = now.plus(coolingPeriod);
      }
    }
  }

  /// 取消注销：回到正常，清掉到期时间和提醒标记。本来就正常时什么都不做。
  ///
  /// @throws InvalidUserStateTransitionException 已封禁或已注销
  public void cancelDeletion() {
    switch (status) {
      case ACTIVE -> {}
      case BANNED, DELETED -> throw transition("cancelDeletion");
      case PENDING_DELETION -> {
        status = UserStatus.ACTIVE;
        deletionDueAt = null;
        deletionRemindedAt = null;
      }
    }
  }

  /// 完成注销：清掉邮箱、验证时间、到期时间、提醒标记、封禁时间，记下完成时间；用户名和 ID 留着。
  /// 已注销时什么都不做（任务重跑幂等）。
  ///
  /// @param now 当前时间
  /// @throws InvalidUserStateTransitionException 正常状态（必须先申请）
  public void completeDeletion(Instant now) {
    Objects.requireNonNull(now, "now 不能为 null");
    switch (status) {
      case DELETED -> {}
      case ACTIVE -> throw transition("completeDeletion");
      case PENDING_DELETION, BANNED -> {
        status = UserStatus.DELETED;
        email = null;
        emailVerifiedAt = null;
        deletionDueAt = null;
        deletionRemindedAt = null;
        bannedAt = null;
        deletedAt = now;
      }
    }
  }

  /// 记下到期前提醒已发出。已记过时什么都不做，保留第一次的时间。
  ///
  /// @param now 当前时间
  /// @throws InvalidUserStateTransitionException 不在冷静期
  public void markDeletionReminded(Instant now) {
    Objects.requireNonNull(now, "now 不能为 null");
    if (status != UserStatus.PENDING_DELETION) {
      throw transition("markDeletionReminded");
    }
    if (deletionRemindedAt == null) {
      deletionRemindedAt = now;
    }
  }

  /// 清掉到期前提醒的标记，让下一次提醒任务重发（这封被发信执行器拒收时用）。不在冷静期时什么都不做。
  public void clearDeletionReminder() {
    if (status == UserStatus.PENDING_DELETION) {
      deletionRemindedAt = null;
    }
  }

  /// 注销是否已到期：冷静期内且到期时间不晚于现在（恰好等于也算）。
  ///
  /// @param now 当前时间
  /// @return 到期时为 `true`
  public boolean isDeletionDue(Instant now) {
    Objects.requireNonNull(now, "now 不能为 null");
    return status == UserStatus.PENDING_DELETION && !deletionDueAt.isAfter(now);
  }

  /// 是否该发到期前提醒：冷静期内、还没提醒过、还没到期，且到期时间落在「现在 + 提前量」之内。
  ///
  /// @param now 当前时间
  /// @param lead 提前量
  /// @return 该提醒时为 `true`
  public boolean needsDeletionReminder(Instant now, Duration lead) {
    Objects.requireNonNull(now, "now 不能为 null");
    Objects.requireNonNull(lead, "lead 不能为 null");
    return status == UserStatus.PENDING_DELETION
        && deletionRemindedAt == null
        && deletionDueAt.isAfter(now)
        && !deletionDueAt.isAfter(now.plus(lead));
  }

  /// 记下邮箱已验证。已验证时什么都不做，保留第一次的时间。
  ///
  /// @param now 当前时间
  /// @throws InvalidUserStateTransitionException 已注销
  public void markEmailVerified(Instant now) {
    Objects.requireNonNull(now, "now 不能为 null");
    if (status == UserStatus.DELETED) {
      throw transition("markEmailVerified");
    }
    if (emailVerifiedAt == null) {
      emailVerifiedAt = now;
    }
  }

  /// 改邮箱：换地址并把验证时间记成现在——点确认链接或撤销链接的人证明了对这个地址的控制。
  /// 其他状态不拦，调用方自己判。
  ///
  /// @param next 规范化之后的新邮箱
  /// @param now 当前时间
  /// @throws InvalidUserStateTransitionException 已注销
  public void changeEmail(EmailAddress next, Instant now) {
    Objects.requireNonNull(next, "next 不能为 null");
    Objects.requireNonNull(now, "now 不能为 null");
    if (status == UserStatus.DELETED) {
      throw transition("changeEmail");
    }
    email = next;
    emailVerifiedAt = now;
  }

  /// `/me/**` 写接口的状态守卫：正常放行；冷静期内 409 并带 `PENDING_DELETION` 特征；
  /// 已封禁、已注销 401（这个会话不再有效，和「当前用户」接口一致）。
  ///
  /// @throws UserPendingDeletionException 冷静期内
  /// @throws AuthenticationRequiredException 已封禁或已注销
  public void requireActive() {
    switch (status) {
      case ACTIVE -> {}
      case PENDING_DELETION -> throw new UserPendingDeletionException();
      case BANNED, DELETED -> throw new AuthenticationRequiredException();
    }
  }

  /// 改用户名：先过状态守卫，新名与当前相同是字段错误 `UNCHANGED`，冷却期内给剩余时长；
  /// 否则换名、记下时间，返回旧名给调用方保留。占用与保留由调用方查。
  ///
  /// @param next 新用户名
  /// @param now 当前时间
  /// @param policy 改名规则
  /// @return 旧用户名
  /// @throws InvalidUserFieldsException 新名与当前相同
  /// @throws UsernameChangeCooldownException 冷却期内
  public Username changeUsername(Username next, Instant now, UsernameChangePolicy policy) {
    Objects.requireNonNull(next, "next 不能为 null");
    Objects.requireNonNull(now, "now 不能为 null");
    Objects.requireNonNull(policy, "policy 不能为 null");
    requireActive();
    if (next.equals(username)) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.usernameUnchanged()));
    }
    if (policy.remainingCooldown(usernameChangedAt, now).isPresent()) {
      throw new UsernameChangeCooldownException();
    }
    Username previous = username;
    username = next;
    usernameChangedAt = now;
    return previous;
  }

  /// 当前状态不允许的迁移。
  ///
  /// @param action 动作名
  /// @return 异常
  private InvalidUserStateTransitionException transition(String action) {
    return new InvalidUserStateTransitionException(status, action);
  }

  /// 只输出 ID、用户名和状态，不输出邮箱。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "User[id=" + id + ", username=" + username.value() + ", status=" + status + "]";
  }
}
