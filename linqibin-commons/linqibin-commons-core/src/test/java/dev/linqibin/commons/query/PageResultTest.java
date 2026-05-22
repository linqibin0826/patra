package dev.linqibin.commons.query;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// PageResult 单元测试。
///
/// 测试策略：
///
/// - 紧凑构造器验证（合法值、非法值、防御性拷贝）
/// - `of()` 工厂方法（自动计算 totalPages，含边界）
/// - `empty()` 空结果工厂
/// - `map()` 类型转换（保留分页元数据）
///
/// @since 0.1.0
@DisplayName("PageResult 单元测试")
class PageResultTest {

  @Nested
  @DisplayName("构造器验证")
  class ConstructorValidation {

    @Test
    @DisplayName("合法参数构造成功且防御性拷贝")
    void shouldConstructAndDefensivelyCopy() {
      // given
      var mutableList = new ArrayList<>(List.of("a", "b"));

      // when
      var result = new PageResult<>(1, 10, 2, 1, mutableList);
      mutableList.add("c");

      // then
      assertThat(result.items()).hasSize(2).containsExactly("a", "b");
    }

    @Test
    @DisplayName("page < 1 抛异常")
    void shouldRejectPageLessThanOne() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new PageResult<>(0, 10, 0, 0, List.of()));
    }

    @Test
    @DisplayName("pageSize < 1 抛异常")
    void shouldRejectPageSizeLessThanOne() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new PageResult<>(1, 0, 0, 0, List.of()));
    }

    @Test
    @DisplayName("total < 0 抛异常")
    void shouldRejectNegativeTotal() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new PageResult<>(1, 10, -1, 0, List.of()));
    }

    @Test
    @DisplayName("totalPages < 0 抛异常")
    void shouldRejectNegativeTotalPages() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new PageResult<>(1, 10, 0, -1, List.of()));
    }

    @Test
    @DisplayName("items 为 null 抛异常")
    void shouldRejectNullItems() {
      assertThatNullPointerException().isThrownBy(() -> new PageResult<>(1, 10, 0, 0, null));
    }
  }

  @Nested
  @DisplayName("of() 工厂方法")
  class OfFactory {

    @Test
    @DisplayName("自动计算 totalPages")
    void shouldCalculateTotalPages() {
      // when
      var result = PageResult.of(List.of("a", "b"), 1, 10, 25);

      // then
      assertThat(result.page()).isEqualTo(1);
      assertThat(result.pageSize()).isEqualTo(10);
      assertThat(result.total()).isEqualTo(25);
      assertThat(result.totalPages()).isEqualTo(3); // ceil(25/10) = 3
      assertThat(result.items()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("total 整除 pageSize 时 totalPages 精确")
    void shouldCalculateExactTotalPages() {
      // when
      var result = PageResult.of(List.of("a"), 1, 5, 20);

      // then
      assertThat(result.totalPages()).isEqualTo(4); // 20/5 = 4
    }

    @Test
    @DisplayName("total = 0 时 totalPages = 0")
    void shouldReturnZeroTotalPagesForEmptyResult() {
      // when
      var result = PageResult.of(List.of(), 1, 10, 0);

      // then
      assertThat(result.totalPages()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("empty() 工厂方法")
  class EmptyFactory {

    @Test
    @DisplayName("返回空结果")
    void shouldReturnEmptyResult() {
      // when
      var result = PageResult.empty(1, 20);

      // then
      assertThat(result.page()).isEqualTo(1);
      assertThat(result.pageSize()).isEqualTo(20);
      assertThat(result.total()).isZero();
      assertThat(result.totalPages()).isZero();
      assertThat(result.items()).isEmpty();
    }
  }

  @Nested
  @DisplayName("map() 类型转换")
  class MapTransformation {

    @Test
    @DisplayName("转换 items 类型，保留分页元数据")
    void shouldTransformItemsPreservingMetadata() {
      // given
      var original = PageResult.of(List.of(1, 2, 3), 2, 10, 25);

      // when
      PageResult<String> mapped = original.map(String::valueOf);

      // then
      assertThat(mapped.page()).isEqualTo(2);
      assertThat(mapped.pageSize()).isEqualTo(10);
      assertThat(mapped.total()).isEqualTo(25);
      assertThat(mapped.totalPages()).isEqualTo(3);
      assertThat(mapped.items()).containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("空列表 map 不报错")
    void shouldHandleEmptyListMap() {
      // given
      PageResult<Integer> empty = PageResult.empty(1, 10);

      // when
      PageResult<String> mapped = empty.map(String::valueOf);

      // then
      assertThat(mapped.items()).isEmpty();
      assertThat(mapped.total()).isZero();
    }
  }
}
