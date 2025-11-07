package com.patra.starter.web.resp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 测试 {@link PageResult} 的分页元数据计算和边界条件 */
@DisplayName("PageResult 单元测试")
class PageResultTest {

  @Test
  @DisplayName("of() 应该正确计算总页数")
  void of_shouldCalculateTotalPagesCorrectly() {
    // Arrange
    List<String> records = Arrays.asList("item1", "item2", "item3");

    // Act
    PageResult<String> result = PageResult.of(10, 1, 3, records);

    // Assert
    assertThat(result.getTotal()).isEqualTo(10);
    assertThat(result.getCurrent()).isEqualTo(1);
    assertThat(result.getSize()).isEqualTo(3);
    assertThat(result.getPages()).isEqualTo(4); // 10 / 3 = 4 页 (向上取整)
    assertThat(result.getRecords()).hasSize(3);
  }

  @Test
  @DisplayName("of() 当 size 为 0 时应该将 pages 设为 0")
  void of_withZeroSize_shouldSetPagesToZero() {
    // Act
    PageResult<String> result = PageResult.of(10, 1, 0, Collections.emptyList());

    // Assert
    assertThat(result.getPages()).isEqualTo(0);
    assertThat(result.getTotal()).isEqualTo(10);
  }

  @Test
  @DisplayName("of() 当 size 为负数时应该将 pages 设为 0")
  void of_withNegativeSize_shouldSetPagesToZero() {
    // Act
    PageResult<String> result = PageResult.of(10, 1, -5, Collections.emptyList());

    // Assert
    assertThat(result.getPages()).isEqualTo(0);
  }

  @Test
  @DisplayName("of() 当 total 为 0 时应该返回空页面")
  void of_withZeroTotal_shouldReturnEmptyPage() {
    // Act
    PageResult<String> result = PageResult.of(0, 1, 10, Collections.emptyList());

    // Assert
    assertThat(result.getTotal()).isEqualTo(0);
    assertThat(result.getPages()).isEqualTo(0);
    assertThat(result.getRecords()).isEmpty();
  }

  @Test
  @DisplayName("of() 当 total 刚好是 size 的倍数时应该正确计算页数")
  void of_whenTotalIsMultipleOfSize_shouldCalculatePagesCorrectly() {
    // Act
    PageResult<String> result = PageResult.of(20, 1, 10, Collections.emptyList());

    // Assert
    assertThat(result.getPages()).isEqualTo(2); // 20 / 10 = 2 页
  }

  @Test
  @DisplayName("of() 当 total 不是 size 的倍数时应该向上取整")
  void of_whenTotalIsNotMultipleOfSize_shouldRoundUpPages() {
    // 测试各种不能整除的情况
    PageResult<String> result1 = PageResult.of(21, 1, 10, Collections.emptyList());
    assertThat(result1.getPages()).isEqualTo(3); // 21 / 10 = 2.1 → 3 页

    PageResult<String> result2 = PageResult.of(15, 1, 7, Collections.emptyList());
    assertThat(result2.getPages()).isEqualTo(3); // 15 / 7 = 2.14 → 3 页

    PageResult<String> result3 = PageResult.of(1, 1, 10, Collections.emptyList());
    assertThat(result3.getPages()).isEqualTo(1); // 1 / 10 = 0.1 → 1 页
  }

  @Test
  @DisplayName("of() 当 records 为 null 时应该使用空列表")
  void of_withNullRecords_shouldUseEmptyList() {
    // Act
    PageResult<String> result = PageResult.of(10, 1, 5, null);

    // Assert
    assertThat(result.getRecords()).isNotNull();
    assertThat(result.getRecords()).isEmpty();
  }

  @Test
  @DisplayName("of() 应该保留提供的 records 列表")
  void of_shouldPreserveProvidedRecords() {
    // Arrange
    List<Integer> records = Arrays.asList(1, 2, 3, 4, 5);

    // Act
    PageResult<Integer> result = PageResult.of(100, 2, 5, records);

    // Assert
    assertThat(result.getRecords()).containsExactly(1, 2, 3, 4, 5);
    assertThat(result.getCurrent()).isEqualTo(2);
  }

  @Test
  @DisplayName("of() 应该正确设置 current 页码")
  void of_shouldSetCurrentPageCorrectly() {
    // Act
    PageResult<String> firstPage = PageResult.of(100, 1, 10, Collections.emptyList());
    PageResult<String> middlePage = PageResult.of(100, 5, 10, Collections.emptyList());
    PageResult<String> lastPage = PageResult.of(100, 10, 10, Collections.emptyList());

    // Assert
    assertThat(firstPage.getCurrent()).isEqualTo(1);
    assertThat(middlePage.getCurrent()).isEqualTo(5);
    assertThat(lastPage.getCurrent()).isEqualTo(10);
  }

  @Test
  @DisplayName("of() 边界条件：单条记录单页")
  void of_boundaryCondition_singleRecordSinglePage() {
    // Arrange
    List<String> records = Collections.singletonList("only-item");

    // Act
    PageResult<String> result = PageResult.of(1, 1, 10, records);

    // Assert
    assertThat(result.getTotal()).isEqualTo(1);
    assertThat(result.getPages()).isEqualTo(1);
    assertThat(result.getRecords()).hasSize(1);
  }

  @Test
  @DisplayName("of() 边界条件：大数据集大页面")
  void of_boundaryCondition_largeDatasetLargePage() {
    // Act
    PageResult<String> result = PageResult.of(1000000, 1, 1000, Collections.emptyList());

    // Assert
    assertThat(result.getPages()).isEqualTo(1000); // 1000000 / 1000 = 1000 页
  }

  @Test
  @DisplayName("of() 边界条件：total 小于 size")
  void of_boundaryCondition_totalLessThanSize() {
    // Arrange
    List<String> records = Arrays.asList("item1", "item2", "item3");

    // Act
    PageResult<String> result = PageResult.of(3, 1, 10, records);

    // Assert
    assertThat(result.getPages()).isEqualTo(1); // 3 / 10 = 0.3 → 1 页
    assertThat(result.getRecords()).hasSize(3);
  }

  @Test
  @DisplayName("默认构造的 PageResult 应该有空 records")
  void defaultConstructor_shouldHaveEmptyRecords() {
    // Act
    PageResult<String> result = new PageResult<>();

    // Assert
    assertThat(result.getRecords()).isNotNull();
    assertThat(result.getRecords()).isEmpty();
  }

  @Test
  @DisplayName("of() 应该处理各种类型参数")
  void of_shouldHandleVariousTypes() {
    // 测试不同的泛型类型
    PageResult<String> stringResult = PageResult.of(10, 1, 5, Arrays.asList("a", "b"));
    PageResult<Integer> intResult = PageResult.of(10, 1, 5, Arrays.asList(1, 2, 3));
    PageResult<Object> objectResult = PageResult.of(10, 1, 5, Arrays.asList(new Object()));

    assertThat(stringResult.getRecords()).hasSize(2);
    assertThat(intResult.getRecords()).hasSize(3);
    assertThat(objectResult.getRecords()).hasSize(1);
  }
}
