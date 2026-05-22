package dev.linqibin.commons.query;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// PagingParams 单元测试。
///
/// 测试策略：
///
/// - 紧凑构造器验证（合法值、边界值、非法值）
/// - `of()` 静态工厂方法
/// - `normalize()` 归一化工厂（null、非法值、超限值、合法值）
///
/// @since 0.1.0
@DisplayName("PagingParams 单元测试")
class PagingParamsTest {

  @Nested
  @DisplayName("构造器验证")
  class ConstructorValidation {

    @Test
    @DisplayName("合法参数构造成功")
    void shouldConstructWithValidParams() {
      // when
      var params = new PagingParams(1, 20);

      // then
      assertThat(params.page()).isEqualTo(1);
      assertThat(params.pageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("page < 1 抛异常")
    void shouldRejectPageLessThanOne() {
      assertThatIllegalArgumentException().isThrownBy(() -> new PagingParams(0, 20));
      assertThatIllegalArgumentException().isThrownBy(() -> new PagingParams(-1, 20));
    }

    @Test
    @DisplayName("pageSize < 1 抛异常")
    void shouldRejectPageSizeLessThanOne() {
      assertThatIllegalArgumentException().isThrownBy(() -> new PagingParams(1, 0));
      assertThatIllegalArgumentException().isThrownBy(() -> new PagingParams(1, -1));
    }
  }

  @Nested
  @DisplayName("of() 工厂方法")
  class OfFactory {

    @Test
    @DisplayName("等价于构造器调用")
    void shouldCreateSameAsConstructor() {
      // when
      var params = PagingParams.of(3, 50);

      // then
      assertThat(params).isEqualTo(new PagingParams(3, 50));
    }
  }

  @Nested
  @DisplayName("normalize() 归一化工厂")
  class NormalizeFactory {

    @Test
    @DisplayName("null 参数使用默认值")
    void shouldUseDefaultsForNull() {
      // when
      var params = PagingParams.normalize(null, null, 20, 100);

      // then
      assertThat(params.page()).isEqualTo(1);
      assertThat(params.pageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("非法值使用默认值")
    void shouldUseDefaultsForInvalidValues() {
      // when
      var params = PagingParams.normalize(0, -1, 20, 100);

      // then
      assertThat(params.page()).isEqualTo(1);
      assertThat(params.pageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("合法值原样保留")
    void shouldPreserveValidValues() {
      // when
      var params = PagingParams.normalize(3, 50, 20, 100);

      // then
      assertThat(params.page()).isEqualTo(3);
      assertThat(params.pageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("pageSize 超限值截断为 maxSize")
    void shouldClampPageSizeToMax() {
      // when
      var params = PagingParams.normalize(1, 500, 20, 100);

      // then
      assertThat(params.page()).isEqualTo(1);
      assertThat(params.pageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("两参数重载使用全局默认值")
    void shouldUseGlobalDefaultsWithTwoArgOverload() {
      // when
      var params = PagingParams.normalize(null, null);

      // then
      assertThat(params.page()).isEqualTo(1);
      assertThat(params.pageSize()).isEqualTo(PagingParams.DEFAULT_PAGE_SIZE);
    }

    @Test
    @DisplayName("两参数重载超限值截断为全局上限")
    void shouldClampToGlobalMaxWithTwoArgOverload() {
      // when
      var params = PagingParams.normalize(1, 999);

      // then
      assertThat(params.pageSize()).isEqualTo(PagingParams.MAX_PAGE_SIZE);
    }
  }
}
