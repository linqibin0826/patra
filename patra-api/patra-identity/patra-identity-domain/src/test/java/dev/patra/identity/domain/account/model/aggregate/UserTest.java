package dev.patra.identity.domain.account.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.exception.InvalidUserStateTransitionException;
import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.exception.UsernameChangeCooldownException;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.policy.UsernameChangePolicy;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// User 单元测试：注册、四态状态机、改名、邮箱验证、恢复。
@DisplayName("User 单元测试")
class UserTest {

  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final Username USERNAME = Username.of("chen-yu");
  private static final Instant T0 = Instant.parse("2026-10-20T08:00:00Z");
  private static final Duration COOLING = Duration.ofDays(15);
  private static final UsernameChangePolicy POLICY =
      UsernameChangePolicy.of(Duration.ofDays(30), Duration.ofDays(90));

  @Test
  @DisplayName("注册出来的用户是正常状态、邮箱未验证、没改过名")
  void should_register_active_unverified_user() {
    User user = User.register(EMAIL, USERNAME);

    assertThat(user.getId()).isNull();
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getEmail()).contains(EMAIL);
    assertThat(user.isEmailVerified()).isFalse();
    assertThat(user.getEmailVerifiedAt()).isNull();
    assertThat(user.getUsername()).isEqualTo(USERNAME);
    assertThat(user.getUsernameChangedAt()).isNull();
    assertThat(user.isActive()).isTrue();
  }

  @Test
  @DisplayName("ACTIVE → BANNED → ACTIVE；重复封禁保留第一次时间，重复解封不变")
  void should_walk_active_banned_active() {
    User user = User.register(EMAIL, USERNAME);

    user.ban(T0);
    assertThat(user.getStatus()).isEqualTo(UserStatus.BANNED);
    assertThat(user.isBanned()).isTrue();
    assertThat(user.getBannedAt()).isEqualTo(T0);

    user.ban(T0.plus(Duration.ofHours(1)));
    assertThat(user.getBannedAt()).isEqualTo(T0);

    user.unban();
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getBannedAt()).isNull();

    assertThatCode(user::unban).doesNotThrowAnyException();
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  @DisplayName("ACTIVE → PENDING_DELETION → ACTIVE；重复申请保留原到期时间，重复取消不变")
  void should_walk_active_pending_deletion_active() {
    User user = User.register(EMAIL, USERNAME);

    user.requestDeletion(T0, COOLING);
    assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_DELETION);
    assertThat(user.isPendingDeletion()).isTrue();
    assertThat(user.getDeletionDueAt()).isEqualTo(T0.plus(COOLING));

    user.requestDeletion(T0.plus(Duration.ofDays(1)), COOLING);
    assertThat(user.getDeletionDueAt()).isEqualTo(T0.plus(COOLING));

    user.cancelDeletion();
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getDeletionDueAt()).isNull();

    assertThatCode(user::cancelDeletion).doesNotThrowAnyException();
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  @DisplayName("PENDING_DELETION → DELETED：清邮箱、验证时间、到期时间，留用户名；重复执行幂等")
  void should_walk_pending_deletion_to_deleted_and_clear_identity() {
    User user = User.register(EMAIL, USERNAME);
    user.markEmailVerified(T0);
    user.requestDeletion(T0, COOLING);

    user.completeDeletion(T0.plus(COOLING));

    assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(user.isDeleted()).isTrue();
    assertThat(user.getEmail()).isEmpty();
    assertThat(user.getEmailVerifiedAt()).isNull();
    assertThat(user.getDeletionDueAt()).isNull();
    assertThat(user.getBannedAt()).isNull();
    assertThat(user.getDeletedAt()).isEqualTo(T0.plus(COOLING));
    assertThat(user.getUsername()).isEqualTo(USERNAME);

    user.completeDeletion(T0.plus(Duration.ofDays(16)));
    assertThat(user.getDeletedAt()).isEqualTo(T0.plus(COOLING));
  }

  @Test
  @DisplayName("BANNED → DELETED 可走（后台用）；PENDING_DELETION 被封禁时注销申请作废")
  void should_allow_banned_to_deleted_and_ban_to_void_pending_deletion() {
    User banned = User.register(EMAIL, USERNAME);
    banned.ban(T0);
    banned.completeDeletion(T0.plus(Duration.ofDays(1)));
    assertThat(banned.getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(banned.getBannedAt()).isNull();
    assertThat(banned.getEmail()).isEmpty();

    User pending = User.register(EMAIL, USERNAME);
    pending.requestDeletion(T0, COOLING);
    pending.ban(T0.plus(Duration.ofDays(1)));
    assertThat(pending.getStatus()).isEqualTo(UserStatus.BANNED);
    assertThat(pending.getDeletionDueAt()).isNull();
    assertThat(pending.getBannedAt()).isEqualTo(T0.plus(Duration.ofDays(1)));
  }

  @Test
  @DisplayName("DELETED 不能回退：每个迁移都被拒")
  void should_reject_every_transition_out_of_deleted() {
    User user = deletedUser();

    assertThatThrownBy(() -> user.ban(T0)).isInstanceOf(InvalidUserStateTransitionException.class);
    assertThatThrownBy(user::unban).isInstanceOf(InvalidUserStateTransitionException.class);
    assertThatThrownBy(() -> user.requestDeletion(T0, COOLING))
        .isInstanceOf(InvalidUserStateTransitionException.class);
    assertThatThrownBy(user::cancelDeletion)
        .isInstanceOf(InvalidUserStateTransitionException.class);
    assertThatThrownBy(() -> user.markEmailVerified(T0))
        .isInstanceOf(InvalidUserStateTransitionException.class);
    assertThatThrownBy(() -> user.changeUsername(Username.of("other"), T0, POLICY))
        .isInstanceOf(AuthenticationRequiredException.class);
    assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
  }

  @Test
  @DisplayName("ACTIVE 不能直接 DELETED；BANNED 不能申请或取消注销")
  void should_reject_invalid_transitions_from_active_and_banned() {
    User active = User.register(EMAIL, USERNAME);
    assertThatThrownBy(() -> active.completeDeletion(T0))
        .isInstanceOf(InvalidUserStateTransitionException.class);

    User banned = User.register(EMAIL, USERNAME);
    banned.ban(T0);
    assertThatThrownBy(() -> banned.requestDeletion(T0, COOLING))
        .isInstanceOf(InvalidUserStateTransitionException.class);
    assertThatThrownBy(banned::cancelDeletion)
        .isInstanceOf(InvalidUserStateTransitionException.class);
    assertThat(banned.getStatus()).isEqualTo(UserStatus.BANNED);
  }

  @Test
  @DisplayName("写接口的状态守卫：ACTIVE 通过，PENDING_DELETION 409，BANNED 和 DELETED 401")
  void should_guard_self_service_writes_by_status() {
    User active = User.register(EMAIL, USERNAME);
    assertThatCode(active::requireActive).doesNotThrowAnyException();

    User pending = User.register(EMAIL, USERNAME);
    pending.requestDeletion(T0, COOLING);
    assertThatThrownBy(pending::requireActive).isInstanceOf(UserPendingDeletionException.class);

    User banned = User.register(EMAIL, USERNAME);
    banned.ban(T0);
    assertThatThrownBy(banned::requireActive).isInstanceOf(AuthenticationRequiredException.class);

    assertThatThrownBy(deletedUser()::requireActive)
        .isInstanceOf(AuthenticationRequiredException.class);
  }

  @Test
  @DisplayName("第一次改名没有冷却：换名、记时间、返回旧名")
  void should_change_username_without_cooldown_on_first_change() {
    User user = User.register(EMAIL, USERNAME);

    Username previous = user.changeUsername(Username.of("chen-yu-2"), T0, POLICY);

    assertThat(previous).isEqualTo(USERNAME);
    assertThat(user.getUsername().value()).isEqualTo("chen-yu-2");
    assertThat(user.getUsernameChangedAt()).isEqualTo(T0);
  }

  @Test
  @DisplayName("冷却期内再改被拒并给出剩余时长；到期当刻可改，改回旧名也一样")
  void should_reject_username_change_within_cooldown_with_remaining_time() {
    User user = User.register(EMAIL, USERNAME);
    user.changeUsername(Username.of("chen-yu-2"), T0, POLICY);

    assertThatThrownBy(() -> user.changeUsername(USERNAME, T0.plus(Duration.ofDays(10)), POLICY))
        .isInstanceOf(UsernameChangeCooldownException.class);
    assertThat(user.getUsername().value()).isEqualTo("chen-yu-2");

    user.changeUsername(USERNAME, T0.plus(Duration.ofDays(30)), POLICY);
    assertThat(user.getUsername()).isEqualTo(USERNAME);
    assertThat(user.getUsernameChangedAt()).isEqualTo(T0.plus(Duration.ofDays(30)));
  }

  @Test
  @DisplayName("新名与当前相同（含大小写不同）是 422 的字段错误 UNCHANGED，不消耗冷却")
  void should_reject_unchanged_username() {
    User user = User.register(EMAIL, USERNAME);

    assertThatThrownBy(() -> user.changeUsername(Username.of("CHEN-YU"), T0, POLICY))
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .singleElement()
                    .extracting(FieldViolation::code)
                    .isEqualTo("UNCHANGED"));
    assertThat(user.getUsernameChangedAt()).isNull();
  }

  @Test
  @DisplayName("邮箱验证时间只记第一次")
  void should_keep_first_email_verification_time() {
    User user = User.register(EMAIL, USERNAME);

    user.markEmailVerified(T0);
    user.markEmailVerified(T0.plus(Duration.ofDays(1)));

    assertThat(user.isEmailVerified()).isTrue();
    assertThat(user.getEmailVerifiedAt()).isEqualTo(T0);
  }

  @Test
  @DisplayName("从库里恢复用构建器，保留全部字段；DELETED 允许没有邮箱，其他状态不允许")
  void should_restore_all_fields_via_builder() {
    Instant createdAt = Instant.parse("2026-10-01T00:00:00Z");
    Instant updatedAt = Instant.parse("2026-10-02T00:00:00Z");

    User user =
        User.restore()
            .id(42L)
            .email(EMAIL)
            .emailVerifiedAt(T0)
            .username(USERNAME)
            .usernameChangedAt(T0)
            .status(UserStatus.BANNED)
            .bannedAt(T0)
            .version(3L)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();

    assertThat(user.getId()).isEqualTo(42L);
    assertThat(user.getEmail()).contains(EMAIL);
    assertThat(user.getEmailVerifiedAt()).isEqualTo(T0);
    assertThat(user.getUsername()).isEqualTo(USERNAME);
    assertThat(user.getUsernameChangedAt()).isEqualTo(T0);
    assertThat(user.getStatus()).isEqualTo(UserStatus.BANNED);
    assertThat(user.getBannedAt()).isEqualTo(T0);
    assertThat(user.getDeletionDueAt()).isNull();
    assertThat(user.getDeletedAt()).isNull();
    assertThat(user.getVersion()).isEqualTo(3L);
    assertThat(user.getCreatedAt()).isEqualTo(createdAt);
    assertThat(user.getUpdatedAt()).isEqualTo(updatedAt);

    User deleted =
        User.restore()
            .id(43L)
            .username(USERNAME)
            .status(UserStatus.DELETED)
            .deletedAt(T0)
            .version(0L)
            .build();
    assertThat(deleted.getEmail()).isEmpty();
    assertThat(deleted.isDeleted()).isTrue();

    assertThatThrownBy(
            () -> User.restore().id(44L).username(USERNAME).status(UserStatus.ACTIVE).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 一个已经走完注销的用户。
  ///
  /// @return DELETED 状态的用户
  private static User deletedUser() {
    User user = User.register(EMAIL, USERNAME);
    user.requestDeletion(T0, COOLING);
    user.completeDeletion(T0.plus(COOLING));
    return user;
  }

  @Test
  @DisplayName("改邮箱：换地址并把验证时间记成现在")
  void should_change_email_and_mark_verified_now() {
    User user = User.register(EMAIL, USERNAME);

    user.changeEmail(EmailAddress.of("new@example.com"), T0);

    assertThat(user.getEmail()).contains(EmailAddress.of("new@example.com"));
    assertThat(user.getEmailVerifiedAt()).isEqualTo(T0);
  }

  @Test
  @DisplayName("已注销的用户不能改邮箱")
  void should_reject_change_email_when_deleted() {
    User user = User.register(EMAIL, USERNAME);
    user.requestDeletion(T0, COOLING);
    user.completeDeletion(T0.plus(COOLING));

    assertThatThrownBy(() -> user.changeEmail(EmailAddress.of("new@example.com"), T0))
        .isInstanceOf(InvalidUserStateTransitionException.class);
  }

  /// 冷静期内的用户，到期时间是 `T0 + 15 天`。
  ///
  /// @return PENDING_DELETION 状态的用户
  private static User pendingUser() {
    User user = User.register(EMAIL, USERNAME);
    user.requestDeletion(T0, COOLING);
    return user;
  }

  @Test
  @DisplayName("注销提醒标记只记第一次")
  void should_mark_reminder_once_and_keep_first_time() {
    User user = pendingUser();

    user.markDeletionReminded(T0.plus(Duration.ofDays(12)));
    user.markDeletionReminded(T0.plus(Duration.ofDays(13)));

    assertThat(user.getDeletionRemindedAt()).isEqualTo(T0.plus(Duration.ofDays(12)));
  }

  @Test
  @DisplayName("不在冷静期不能记注销提醒")
  void should_reject_reminder_mark_outside_pending_deletion() {
    User active = User.register(EMAIL, USERNAME);
    User banned = User.register(EMAIL, USERNAME);
    banned.ban(T0);

    assertThatThrownBy(() -> active.markDeletionReminded(T0))
        .isInstanceOf(InvalidUserStateTransitionException.class);
    assertThatThrownBy(() -> banned.markDeletionReminded(T0))
        .isInstanceOf(InvalidUserStateTransitionException.class);
    assertThatThrownBy(() -> deletedUser().markDeletionReminded(T0))
        .isInstanceOf(InvalidUserStateTransitionException.class);
  }

  @Test
  @DisplayName("取消、封禁、完成注销都清掉提醒标记")
  void should_clear_reminder_on_cancel_ban_and_complete() {
    User cancelled = pendingUser();
    cancelled.markDeletionReminded(T0);
    cancelled.cancelDeletion();

    User banned = pendingUser();
    banned.markDeletionReminded(T0);
    banned.ban(T0);

    User completed = pendingUser();
    completed.markDeletionReminded(T0);
    completed.completeDeletion(T0.plus(COOLING));

    assertThat(cancelled.getDeletionRemindedAt()).isNull();
    assertThat(banned.getDeletionRemindedAt()).isNull();
    assertThat(completed.getDeletionRemindedAt()).isNull();
  }

  @Test
  @DisplayName("显式清提醒标记：冷静期内清空，其他状态什么都不做")
  void should_clear_reminder_explicitly_only_while_pending() {
    User pending = pendingUser();
    pending.markDeletionReminded(T0);
    User active = User.register(EMAIL, USERNAME);

    pending.clearDeletionReminder();

    assertThat(pending.getDeletionRemindedAt()).isNull();
    assertThatCode(active::clearDeletionReminder).doesNotThrowAnyException();
    assertThat(active.getDeletionRemindedAt()).isNull();
  }

  @Test
  @DisplayName("到期判定：恰好到期时刻就算到期，早一秒不算；正常状态永远不到期")
  void should_be_due_exactly_at_due_time() {
    User user = pendingUser();
    Instant due = T0.plus(COOLING);

    assertThat(user.isDeletionDue(due)).isTrue();
    assertThat(user.isDeletionDue(due.minusSeconds(1))).isFalse();
    assertThat(User.register(EMAIL, USERNAME).isDeletionDue(due)).isFalse();
  }

  @Test
  @DisplayName("要不要提醒：到期前提前量之内、还没到期、没提醒过")
  void should_need_reminder_only_inside_lead_window() {
    User user = pendingUser();
    Duration lead = Duration.ofDays(3);
    Instant windowStart = T0.plus(Duration.ofDays(12));

    assertThat(user.needsDeletionReminder(windowStart, lead)).isTrue();
    assertThat(user.needsDeletionReminder(windowStart.minusSeconds(1), lead)).isFalse();
    assertThat(user.needsDeletionReminder(T0.plus(COOLING), lead)).isFalse();
    user.markDeletionReminded(windowStart);
    assertThat(user.needsDeletionReminder(windowStart, lead)).isFalse();
    assertThat(User.register(EMAIL, USERNAME).needsDeletionReminder(windowStart, lead)).isFalse();
  }
}
