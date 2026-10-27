package dev.patra.interaction.app.subject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.port.source.SubjectSourcePort;
import java.util.List;
import org.junit.jupiter.api.Test;

/// 每种外部对象恰好一个来源策略，本地对象不能有；缺失、重复或给本地对象配了策略都让应用启动失败。
class SubjectSourcesTest {

  @Test
  void should_index_ports_by_type() {
    SubjectSourcePort publication = port(SubjectType.PUBLICATION);
    SubjectSourcePort journal = port(SubjectType.JOURNAL);

    SubjectSources sources = new SubjectSources(List.of(publication, journal));

    assertThat(sources.of(SubjectType.PUBLICATION)).isSameAs(publication);
    assertThat(sources.of(SubjectType.JOURNAL)).isSameAs(journal);
  }

  @Test
  void should_fail_when_a_type_has_no_port() {
    assertThatThrownBy(() -> new SubjectSources(List.of(port(SubjectType.PUBLICATION))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JOURNAL");
  }

  /// 本地对象没有来源，不要求策略；向它要策略是程序错误，直接抛异常而不是返回 null。
  @Test
  void should_not_require_port_for_local_type() {
    SubjectSources sources =
        new SubjectSources(List.of(port(SubjectType.PUBLICATION), port(SubjectType.JOURNAL)));

    assertThatThrownBy(() -> sources.of(SubjectType.POST))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("POST");
  }

  /// 给本地对象配来源策略是程序错误。
  @Test
  void should_fail_when_local_type_has_port() {
    List<SubjectSourcePort> ports =
        List.of(port(SubjectType.PUBLICATION), port(SubjectType.JOURNAL), port(SubjectType.POST));

    assertThatThrownBy(() -> new SubjectSources(ports))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("POST");
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
