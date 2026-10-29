package dev.patra.identity.app.account.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.model.aggregate.FormerUsername;
import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserSummaryReadModel;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.FormerUsernameRepository;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.tool.core.error.field.FieldViolation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/// UserResolutionQueryService 单元测试：校验、跳过不合语法的、先当前名再保留期内旧名、回显原文（PAP-118 spec 5.2、
/// 5.3）。
class UserResolutionQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");

  private final UserReadPort reader = mock(UserReadPort.class);
  private final FormerUsernameRepository formerUsernames = mock(FormerUsernameRepository.class);
  private final UserResolutionQueryService service =
      new UserResolutionQueryService(reader, formerUsernames, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void should_reject_null_list() {
    assertRejected(null, "REQUIRED");
  }

  @Test
  void should_reject_more_than_100_even_if_duplicated() {
    assertRejected(Collections.nCopies(101, "chen"), "TOO_MANY");
    verifyNoInteractions(reader, formerUsernames);
  }

  @Test
  void should_return_empty_without_reading_when_nothing_is_a_username() {
    assertThat(service.resolveByUsernames(Arrays.asList(null, "", "ab", "-x", "陈", "a b", "@chen")))
        .isEmpty();
    assertThat(service.resolveByUsernames(List.of())).isEmpty();
    verifyNoInteractions(reader, formerUsernames);
  }

  @Test
  void should_resolve_current_usernames_with_their_status() {
    List<UserSummaryReadModel> current =
        List.of(user(1L, "chen-yu", UserStatus.ACTIVE), user(2L, "wang", UserStatus.BANNED));
    when(reader.findSummariesByUsernames(any())).thenReturn(current);

    assertThat(service.resolveByUsernames(List.of("chen-yu", "wang")))
        .containsExactly(
            ResolvedUser.of("chen-yu", 1L, UserStatus.ACTIVE),
            ResolvedUser.of("wang", 2L, UserStatus.BANNED));
    verifyNoInteractions(formerUsernames);
  }

  @Test
  void should_fall_back_to_reserved_former_username_with_owner_status() {
    when(reader.findSummariesByUsernames(any())).thenReturn(List.of());
    FormerUsername former =
        FormerUsername.restore(
            100L,
            3L,
            Username.of("old-name"),
            NOW.minus(Duration.ofDays(1)),
            NOW.plus(Duration.ofDays(89)),
            0L);
    when(formerUsernames.findAllReserved(any(), any())).thenReturn(List.of(former));
    List<UserSummaryReadModel> owners = List.of(user(3L, "new-name", UserStatus.PENDING_DELETION));
    when(reader.findSummaries(any())).thenReturn(owners);

    assertThat(service.resolveByUsernames(List.of("old-name")))
        .containsExactly(ResolvedUser.of("old-name", 3L, UserStatus.PENDING_DELETION));
    verify(reader).findSummaries(argThat(ids -> new ArrayList<>(ids).equals(List.of(3L))));
  }

  @Test
  void should_look_up_former_names_only_for_misses_at_clock_instant() {
    List<UserSummaryReadModel> current = List.of(user(1L, "chen-yu", UserStatus.ACTIVE));
    when(reader.findSummariesByUsernames(any())).thenReturn(current);
    when(formerUsernames.findAllReserved(any(), any())).thenReturn(List.of());

    assertThat(service.resolveByUsernames(List.of("chen-yu", "nobody")))
        .containsExactly(ResolvedUser.of("chen-yu", 1L, UserStatus.ACTIVE));
    verify(formerUsernames)
        .findAllReserved(
            argThat(names -> new ArrayList<>(names).equals(List.of(Username.of("nobody")))),
            eq(NOW));
    verify(reader, never()).findSummaries(any());
  }

  @Test
  void should_keep_request_order_dedupe_exact_text_and_echo_each_spelling() {
    List<UserSummaryReadModel> current =
        List.of(user(1L, "chen-yu", UserStatus.ACTIVE), user(2L, "wang", UserStatus.ACTIVE));
    when(reader.findSummariesByUsernames(any())).thenReturn(current);

    assertThat(service.resolveByUsernames(List.of("wang", "Chen-Yu", "wang", " chen-yu ")))
        .containsExactly(
            ResolvedUser.of("wang", 2L, UserStatus.ACTIVE),
            ResolvedUser.of("Chen-Yu", 1L, UserStatus.ACTIVE),
            ResolvedUser.of(" chen-yu ", 1L, UserStatus.ACTIVE));
    verify(reader)
        .findSummariesByUsernames(
            argThat(
                names ->
                    new ArrayList<>(names)
                        .equals(List.of(Username.of("wang"), Username.of("chen-yu")))));
  }

  /// 断言被拒，字段 `usernames`，原因码如期。
  ///
  /// @param usernames 请求
  /// @param code 原因码
  private void assertRejected(List<String> usernames, String code) {
    assertThatThrownBy(() -> service.resolveByUsernames(usernames))
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e -> {
              FieldViolation violation = e.getFieldViolations().getFirst();
              assertThat(violation.field()).isEqualTo("usernames");
              assertThat(violation.code()).isEqualTo(code);
            });
  }

  /// 邮箱已验证、主页公开的用户。
  ///
  /// @param id 用户 ID
  /// @param username 当前用户名
  /// @param status 状态
  /// @return 摘要原始数据
  private static UserSummaryReadModel user(long id, String username, UserStatus status) {
    return UserSummaryReadModel.builder()
        .userId(id)
        .username(username)
        .status(status)
        .emailVerified(true)
        .profileVisibility(ProfileVisibility.PUBLIC)
        .build();
  }
}
