package dev.patra.identity.app.account.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserSummaryReadModel;
import dev.patra.identity.domain.account.model.read.UserSummaryView;
import dev.patra.identity.domain.account.model.vo.DisplayNamePrefix;
import dev.patra.identity.domain.account.model.vo.UsernamePrefix;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.tool.core.error.field.FieldViolation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// UserSuggestionQueryService 单元测试：校验、前缀语法、排除本人与不可见、保序截断（PAP-118 spec 4.2、4.3）。
class UserSuggestionQueryServiceTest {

  private final UserReadPort reader = mock(UserReadPort.class);
  private final UserSuggestionQueryService service = new UserSuggestionQueryService(reader);

  @Test
  void should_reject_missing_prefix() {
    assertRejected(null, 10, "prefix", "REQUIRED");
  }

  @Test
  void should_reject_missing_limit() {
    assertRejected("chen", null, "limit", "REQUIRED");
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 21})
  void should_reject_limit_out_of_range(int limit) {
    assertRejected("chen", limit, "limit", "INVALID_FORMAT");
    verifyNoInteractions(reader);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 20})
  void should_accept_limit_bounds(int limit) {
    when(reader.findActiveSummariesByUsernamePrefix(any(), anyInt())).thenReturn(List.of());

    assertThat(service.suggest("chen", Optional.empty(), limit)).isEmpty();
  }

  @Test
  void should_report_missing_prefix_and_limit_together() {
    assertThatThrownBy(() -> service.suggest(null, Optional.empty(), null))
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .extracting(FieldViolation::field)
                    .containsExactly("prefix", "limit"));
  }

  @Test
  void should_read_normalized_prefix_with_candidate_cap() {
    when(reader.findActiveSummariesByUsernamePrefix(any(), anyInt())).thenReturn(List.of());

    service.suggest(" Chen ", Optional.of(7L), 10);

    verify(reader)
        .findActiveSummariesByUsernamePrefix(UsernamePrefix.parse("chen").orElseThrow(), 200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "a\u0007", ""})
  void should_return_empty_without_reading_for_impossible_prefix(String prefix) {
    assertThat(service.suggest(prefix, Optional.of(7L), 10)).isEmpty();
    verifyNoInteractions(reader);
  }

  @Test
  void should_query_only_display_names_for_chinese_prefix() {
    service.suggest("张", Optional.of(7L), 10);

    verify(reader)
        .findActiveSummariesByDisplayNamePrefix(DisplayNamePrefix.parse("张").orElseThrow(), 200);
    verify(reader, never()).findActiveSummariesByUsernamePrefix(any(), anyInt());
  }

  @Test
  void should_query_only_display_names_for_prefix_with_space() {
    service.suggest(" Chen Yu", Optional.of(7L), 10);

    verify(reader)
        .findActiveSummariesByDisplayNamePrefix(
            DisplayNamePrefix.parse("chen yu").orElseThrow(), 200);
    verify(reader, never()).findActiveSummariesByUsernamePrefix(any(), anyInt());
  }

  @Test
  void should_put_username_hits_first_and_dedupe_by_user_id() {
    List<UserSummaryReadModel> byUsername =
        List.of(user(1L, ProfileVisibility.PUBLIC, true), user(2L, ProfileVisibility.PUBLIC, true));
    List<UserSummaryReadModel> byDisplayName =
        List.of(user(3L, ProfileVisibility.PUBLIC, true), user(1L, ProfileVisibility.PUBLIC, true));
    when(reader.findActiveSummariesByUsernamePrefix(any(), anyInt())).thenReturn(byUsername);
    when(reader.findActiveSummariesByDisplayNamePrefix(any(), anyInt())).thenReturn(byDisplayName);

    assertThat(service.suggest("chen", Optional.empty(), 10))
        .extracting(UserSummaryView::userId)
        .containsExactly(1L, 2L, 3L);
  }

  @Test
  void should_not_query_display_names_when_usernames_fill_the_limit() {
    List<UserSummaryReadModel> byUsername =
        List.of(user(1L, ProfileVisibility.PUBLIC, true), user(2L, ProfileVisibility.PUBLIC, true));
    when(reader.findActiveSummariesByUsernamePrefix(any(), anyInt())).thenReturn(byUsername);

    assertThat(service.suggest("chen", Optional.empty(), 2))
        .extracting(UserSummaryView::userId)
        .containsExactly(1L, 2L);
    verify(reader, never()).findActiveSummariesByDisplayNamePrefix(any(), anyInt());
  }

  @Test
  void should_drop_viewer_and_accounts_whose_profile_the_viewer_cannot_see() {
    List<UserSummaryReadModel> candidates =
        List.of(
            user(7L, ProfileVisibility.PUBLIC, true),
            user(8L, ProfileVisibility.SELF, true),
            user(9L, ProfileVisibility.PUBLIC, false),
            user(10L, ProfileVisibility.SIGNED_IN, true),
            user(11L, ProfileVisibility.PUBLIC, true));
    when(reader.findActiveSummariesByUsernamePrefix(any(), anyInt())).thenReturn(candidates);

    assertThat(service.suggest("u", Optional.of(7L), 10))
        .extracting(UserSummaryView::userId)
        .containsExactly(10L, 11L);
    assertThat(service.suggest("u", Optional.empty(), 10))
        .extracting(UserSummaryView::userId)
        .containsExactly(7L, 11L);
  }

  @Test
  void should_keep_reader_order_and_cut_to_limit() {
    List<UserSummaryReadModel> candidates =
        List.of(
            user(3L, ProfileVisibility.PUBLIC, true),
            user(1L, ProfileVisibility.PUBLIC, true),
            user(2L, ProfileVisibility.PUBLIC, true));
    when(reader.findActiveSummariesByUsernamePrefix(any(), anyInt())).thenReturn(candidates);

    assertThat(service.suggest("u", Optional.empty(), 2))
        .extracting(UserSummaryView::userId)
        .containsExactly(3L, 1L);
  }

  /// 断言被拒，第一条违例的字段与原因码如期。
  ///
  /// @param prefix 前缀
  /// @param limit 条数上限
  /// @param field 字段名
  /// @param code 原因码
  private void assertRejected(String prefix, Integer limit, String field, String code) {
    assertThatThrownBy(() -> service.suggest(prefix, Optional.empty(), limit))
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e -> {
              FieldViolation violation = e.getFieldViolations().getFirst();
              assertThat(violation.field()).isEqualTo(field);
              assertThat(violation.code()).isEqualTo(code);
            });
  }

  /// 正常账号，用户名 `u-{id}`。
  ///
  /// @param id 用户 ID
  /// @param visibility 主页可见性
  /// @param emailVerified 邮箱是否已验证
  /// @return 摘要原始数据
  private static UserSummaryReadModel user(
      long id, ProfileVisibility visibility, boolean emailVerified) {
    return UserSummaryReadModel.builder()
        .userId(id)
        .username("u-" + id)
        .status(UserStatus.ACTIVE)
        .emailVerified(emailVerified)
        .profileVisibility(visibility)
        .build();
  }
}
