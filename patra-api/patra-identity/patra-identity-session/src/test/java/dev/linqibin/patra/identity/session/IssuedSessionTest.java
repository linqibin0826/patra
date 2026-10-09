package dev.linqibin.patra.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// IssuedSession 单元测试。
@DisplayName("IssuedSession 单元测试")
class IssuedSessionTest {

  @Test
  @DisplayName("被挤掉的 ID 列表是防御性拷贝，toString 不带令牌原文")
  void should_copy_replaced_ids_and_mask_token() {
    SessionToken token = SessionToken.generate(AccountType.USER, new SecureRandom());
    List<Long> replaced = new ArrayList<>(List.of(1L, 2L));

    IssuedSession issued = IssuedSession.of(token, replaced);
    replaced.add(3L);

    assertThat(issued.replacedSessionIds()).containsExactly(1L, 2L);
    assertThat(IssuedSession.of(token, null).replacedSessionIds()).isEmpty();
    assertThat(issued.toString()).doesNotContain(token.value()).contains("patra_user_***");
  }
}
