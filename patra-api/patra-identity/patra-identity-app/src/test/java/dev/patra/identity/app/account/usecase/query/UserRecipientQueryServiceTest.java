package dev.patra.identity.app.account.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserRecipientReadModel;
import dev.patra.identity.domain.account.model.read.UserRecipientView;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/// 批量收件信息（PAP-142 spec 第 13 节）：校验同批量摘要、去重保序、不认识的省略、输出已按状态处理。
class UserRecipientQueryServiceTest {

  private final UserReadPort reader = mock(UserReadPort.class);
  private final UserRecipientQueryService service = new UserRecipientQueryService(reader);

  /// 上限按去重前的长度算，超了不读库。
  @Test
  void should_reject_more_than_100() {
    assertThatThrownBy(() -> service.recipients(Collections.nCopies(101, 7L)))
        .isInstanceOf(InvalidUserFieldsException.class);
    verifyNoInteractions(reader);
  }

  /// 空列表直接返回空，不读库。
  @Test
  void should_return_empty_without_reading_for_empty_list() {
    assertThat(service.recipients(List.of())).isEmpty();
    verifyNoInteractions(reader);
  }

  /// 按去重后的请求顺序；交给读端口的也是去重后的。
  @Test
  void should_dedupe_and_keep_request_order() {
    when(reader.findRecipients(any())).thenReturn(List.of(active(1L), active(2L), active(3L)));

    List<UserRecipientView> result = service.recipients(List.of(3L, 1L, 3L, 2L));

    assertThat(result).extracting(UserRecipientView::userId).containsExactly(3L, 1L, 2L);
    verify(reader).findRecipients(argThat(ids -> new ArrayList<>(ids).equals(List.of(3L, 1L, 2L))));
  }

  /// identity 不认识的 ID 省略。
  @Test
  void should_omit_unknown_ids() {
    when(reader.findRecipients(any())).thenReturn(List.of(active(1L)));

    assertThat(service.recipients(List.of(1L, 999L)))
        .extracting(UserRecipientView::userId)
        .containsExactly(1L);
  }

  /// 输出已按状态处理：已注销不带用户名。
  @Test
  void should_output_views() {
    when(reader.findRecipients(any()))
        .thenReturn(
            List.of(
                UserRecipientReadModel.builder()
                    .userId(1L)
                    .status(UserStatus.DELETED)
                    .username("u-1")
                    .build()));

    assertThat(service.recipients(List.of(1L)).getFirst().username()).isNull();
  }

  /// 正常、已验证的用户。
  ///
  /// @param id 用户 ID
  /// @return 原始数据
  private static UserRecipientReadModel active(long id) {
    return UserRecipientReadModel.builder()
        .userId(id)
        .email("u-" + id + "@example.com")
        .emailVerified(true)
        .status(UserStatus.ACTIVE)
        .username("u-" + id)
        .build();
  }
}
