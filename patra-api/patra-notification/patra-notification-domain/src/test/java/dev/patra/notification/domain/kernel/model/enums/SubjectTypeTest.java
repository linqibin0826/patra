package dev.patra.notification.domain.kernel.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/// 对象类型按 interaction 给的枚举名解析；不认识的不抛异常（PAP-141 契约 5.1）。
class SubjectTypeTest {

  /// 认识的名字。
  @Test
  void should_parse_known_names() {
    assertThat(SubjectType.parse("POST")).contains(SubjectType.POST);
    assertThat(SubjectType.parse("PUBLICATION")).contains(SubjectType.PUBLICATION);
  }

  /// 不认识的、空的：空。
  @Test
  void should_return_empty_for_unknown() {
    assertThat(SubjectType.parse("JOURNAL")).isEmpty();
    assertThat(SubjectType.parse(null)).isEmpty();
  }
}
