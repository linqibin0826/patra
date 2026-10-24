package dev.patra.interaction.app.subject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.port.source.SubjectSourcePort;
import java.util.List;
import org.junit.jupiter.api.Test;

/// 每种对象恰好一个来源策略，缺失或重复都让应用启动失败。
class SubjectSourcesTest {

  @Test
  void should_index_ports_by_type() {
    SubjectSourcePort publication = port(SubjectType.PUBLICATION);

    assertThat(new SubjectSources(List.of(publication)).of(SubjectType.PUBLICATION))
        .isSameAs(publication);
  }

  @Test
  void should_fail_when_a_type_has_no_port() {
    assertThatThrownBy(() -> new SubjectSources(List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PUBLICATION");
  }

  @Test
  void should_fail_on_duplicate_ports() {
    List<SubjectSourcePort> ports =
        List.of(port(SubjectType.PUBLICATION), port(SubjectType.PUBLICATION));

    assertThatThrownBy(() -> new SubjectSources(ports))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PUBLICATION");
  }

  /// 负责某类型的来源策略。
  ///
  /// @param type 对象种类
  /// @return mock
  private static SubjectSourcePort port(SubjectType type) {
    SubjectSourcePort port = mock(SubjectSourcePort.class);
    when(port.type()).thenReturn(type);
    return port;
  }
}
