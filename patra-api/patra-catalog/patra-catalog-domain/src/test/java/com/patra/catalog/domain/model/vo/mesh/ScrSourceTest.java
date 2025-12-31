package com.patra.catalog.domain.model.vo.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// ScrSource 值对象单元测试。
///
/// 测试 SCR 数据来源值对象。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ScrSource 值对象测试")
class ScrSourceTest {

  @Nested
  @DisplayName("创建测试")
  class CreationTests {

    @Test
    @DisplayName("应该成功创建来源")
    void shouldCreateSource() {
      // when
      ScrSource source = ScrSource.of("NCI2004_11_17");

      // then
      assertThat(source.source()).isEqualTo("NCI2004_11_17");
      assertThat(source.orderNum()).isNull();
    }

    @Test
    @DisplayName("应该成功创建带排序号的来源")
    void shouldCreateSourceWithOrder() {
      // when
      ScrSource source = ScrSource.of("FDA SRS (2023)", 1);

      // then
      assertThat(source.source()).isEqualTo("FDA SRS (2023)");
      assertThat(source.orderNum()).isEqualTo(1);
    }

    @Test
    @DisplayName("source 为 null 应该抛出异常")
    void shouldRejectNullSource() {
      assertThatThrownBy(() -> ScrSource.of(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("来源不能为空");
    }

    @Test
    @DisplayName("source 为空白应该抛出异常")
    void shouldRejectBlankSource() {
      assertThatThrownBy(() -> ScrSource.of("   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("来源不能为空");
    }
  }

  @Nested
  @DisplayName("相等性测试")
  class EqualityTests {

    @Test
    @DisplayName("相同来源应该相等")
    void sameSourceShouldBeEqual() {
      ScrSource source1 = ScrSource.of("NCI2004_11_17", 1);
      ScrSource source2 = ScrSource.of("NCI2004_11_17", 1);

      assertThat(source1).isEqualTo(source2);
      assertThat(source1.hashCode()).isEqualTo(source2.hashCode());
    }

    @Test
    @DisplayName("不同来源应该不相等")
    void differentSourceShouldNotBeEqual() {
      ScrSource source1 = ScrSource.of("NCI2004_11_17");
      ScrSource source2 = ScrSource.of("FDA SRS (2023)");

      assertThat(source1).isNotEqualTo(source2);
    }
  }
}
