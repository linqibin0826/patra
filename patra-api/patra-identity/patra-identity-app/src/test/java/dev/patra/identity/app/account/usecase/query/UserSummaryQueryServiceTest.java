package dev.patra.identity.app.account.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserSummaryReadModel;
import dev.patra.identity.domain.account.model.read.UserSummaryView;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.tool.core.error.field.FieldViolation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/// UserSummaryQueryService 单元测试：校验、去重保序、缺席省略（PAP-108 spec 5.2、5.3）。
class UserSummaryQueryServiceTest {

  private final UserReadPort reader = mock(UserReadPort.class);
  private final UserSummaryQueryService service = new UserSummaryQueryService(reader);

  @Test
  void should_reject_null_list() {
    assertRejected(null, "REQUIRED");
  }

  @Test
  void should_reject_null_element() {
    assertRejected(Arrays.asList(1L, null), "INVALID_FORMAT");
  }

  @Test
  void should_reject_more_than_100_even_if_duplicated() {
    assertRejected(Collections.nCopies(101, 7L), "TOO_MANY");
    verifyNoInteractions(reader);
  }

  @Test
  void should_accept_exactly_100() {
    List<Long> ids = LongStream.rangeClosed(1, 100).boxed().toList();
    when(reader.findSummaries(any())).thenReturn(List.of());

    assertThat(service.summaries(ids, Optional.empty())).isEmpty();
    verify(reader).findSummaries(any());
  }

  @Test
  void should_return_empty_without_reading_for_empty_list() {
    assertThat(service.summaries(List.of(), Optional.empty())).isEmpty();
    verifyNoInteractions(reader);
  }

  @Test
  void should_dedupe_and_keep_request_order() {
    when(reader.findSummaries(any())).thenReturn(List.of(user(1L), user(2L), user(3L)));

    List<UserSummaryView> result = service.summaries(List.of(3L, 1L, 3L, 2L), Optional.empty());

    assertThat(result).extracting(UserSummaryView::userId).containsExactly(3L, 1L, 2L);
    verify(reader).findSummaries(argThat(ids -> new ArrayList<>(ids).equals(List.of(3L, 1L, 2L))));
  }

  @Test
  void should_omit_unknown_ids() {
    when(reader.findSummaries(any())).thenReturn(List.of(user(1L)));

    assertThat(service.summaries(List.of(1L, 999L), Optional.empty()))
        .extracting(UserSummaryView::userId)
        .containsExactly(1L);
  }

  @Test
  void should_pass_viewer_to_view() {
    UserSummaryReadModel selfOnly =
        UserSummaryReadModel.builder()
            .userId(1L)
            .username("u-1")
            .status(UserStatus.ACTIVE)
            .emailVerified(true)
            .profileVisibility(ProfileVisibility.SELF)
            .build();
    when(reader.findSummaries(any())).thenReturn(List.of(selfOnly));

    assertThat(service.summaries(List.of(1L), Optional.of(1L)).getFirst().profileVisible())
        .isTrue();
    assertThat(service.summaries(List.of(1L), Optional.empty()).getFirst().profileVisible())
        .isFalse();
  }

  /// 断言被拒，字段 `userIds`，原因码如期。
  ///
  /// @param userIds 请求
  /// @param reason 原因码
  private void assertRejected(List<Long> userIds, String reason) {
    assertThatThrownBy(() -> service.summaries(userIds, Optional.empty()))
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e -> {
              FieldViolation violation = e.getFieldViolations().getFirst();
              assertThat(violation.field()).isEqualTo("userIds");
              assertThat(violation.code()).isEqualTo(reason);
            });
  }

  /// 正常、公开主页的用户。
  ///
  /// @param id 用户 ID
  /// @return 摘要原始数据
  private static UserSummaryReadModel user(long id) {
    return UserSummaryReadModel.builder()
        .userId(id)
        .username("u-" + id)
        .status(UserStatus.ACTIVE)
        .emailVerified(true)
        .profileVisibility(ProfileVisibility.PUBLIC)
        .build();
  }
}
