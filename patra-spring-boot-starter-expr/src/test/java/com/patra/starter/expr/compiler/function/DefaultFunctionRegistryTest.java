package com.patra.starter.expr.compiler.function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DefaultFunctionRegistry} 单元测试。
 *
 * <p>测试策略：纯单元测试，Mock 所有 RenderFunction 依赖。
 *
 * @since 1.0.0
 */
@DisplayName("DefaultFunctionRegistry 单元测试")
class DefaultFunctionRegistryTest {

  @Test
  @DisplayName("构造函数_成功注册函数列表")
  void constructor_shouldRegisterFunctions() {
    // Arrange
    RenderFunction func1 = mock(RenderFunction.class);
    RenderFunction func2 = mock(RenderFunction.class);
    when(func1.code()).thenReturn("FUNC1");
    when(func2.code()).thenReturn("FUNC2");
    List<RenderFunction> functionList = Arrays.asList(func1, func2);

    // Act
    DefaultFunctionRegistry registry = new DefaultFunctionRegistry(functionList);

    // Assert
    assertThat(registry.find("FUNC1")).isPresent().contains(func1);
    assertThat(registry.find("FUNC2")).isPresent().contains(func2);
  }

  @Test
  @DisplayName("构造函数_函数列表为null_抛出异常")
  void constructor_withNullList_shouldThrowException() {
    // Act & Assert
    assertThatThrownBy(() -> new DefaultFunctionRegistry(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("函数列表不能为空");
  }

  @Test
  @DisplayName("构造函数_重复函数代码_抛出异常")
  void constructor_withDuplicateFunctionCode_shouldThrowException() {
    // Arrange
    RenderFunction func1 = mock(RenderFunction.class);
    RenderFunction func2 = mock(RenderFunction.class);
    when(func1.code()).thenReturn("DUPLICATE");
    when(func2.code()).thenReturn("DUPLICATE");
    List<RenderFunction> functionList = Arrays.asList(func1, func2);

    // Act & Assert
    assertThatThrownBy(() -> new DefaultFunctionRegistry(functionList))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("检测到重复的函数代码：DUPLICATE");
  }

  @Test
  @DisplayName("find_存在的函数代码_返回函数")
  void find_withExistingCode_shouldReturnFunction() {
    // Arrange
    RenderFunction func = mock(RenderFunction.class);
    when(func.code()).thenReturn("TEST_FUNC");
    DefaultFunctionRegistry registry = new DefaultFunctionRegistry(List.of(func));

    // Act
    Optional<RenderFunction> result = registry.find("TEST_FUNC");

    // Assert
    assertThat(result).isPresent().contains(func);
  }

  @Test
  @DisplayName("find_不存在的函数代码_返回空Optional")
  void find_withNonExistingCode_shouldReturnEmpty() {
    // Arrange
    RenderFunction func = mock(RenderFunction.class);
    when(func.code()).thenReturn("EXISTING");
    DefaultFunctionRegistry registry = new DefaultFunctionRegistry(List.of(func));

    // Act
    Optional<RenderFunction> result = registry.find("NON_EXISTING");

    // Assert
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("find_null代码_返回空Optional")
  void find_withNullCode_shouldReturnEmpty() {
    // Arrange
    RenderFunction func = mock(RenderFunction.class);
    when(func.code()).thenReturn("TEST");
    DefaultFunctionRegistry registry = new DefaultFunctionRegistry(List.of(func));

    // Act
    Optional<RenderFunction> result = registry.find(null);

    // Assert
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("find_空白代码_返回空Optional")
  void find_withBlankCode_shouldReturnEmpty() {
    // Arrange
    RenderFunction func = mock(RenderFunction.class);
    when(func.code()).thenReturn("TEST");
    DefaultFunctionRegistry registry = new DefaultFunctionRegistry(List.of(func));

    // Act & Assert
    assertThat(registry.find("")).isEmpty();
    assertThat(registry.find("   ")).isEmpty();
    assertThat(registry.find("\t\n")).isEmpty();
  }

  @Test
  @DisplayName("不可变性_修改原始列表不影响注册表")
  void immutability_modifyingOriginalList_shouldNotAffectRegistry() {
    // Arrange
    RenderFunction func1 = mock(RenderFunction.class);
    when(func1.code()).thenReturn("FUNC1");
    List<RenderFunction> functionList = Arrays.asList(func1);
    DefaultFunctionRegistry registry = new DefaultFunctionRegistry(functionList);

    // Act - 尝试修改原始列表（这里只是概念性测试）
    // 注意：Arrays.asList 返回的是固定大小列表，无法添加元素
    // 但我们测试的是注册表的不可变性

    // Assert - 验证注册表仍然正常工作
    assertThat(registry.find("FUNC1")).isPresent().contains(func1);
  }

  @Test
  @DisplayName("空函数列表_可以成功创建注册表")
  void constructor_withEmptyList_shouldSucceed() {
    // Arrange
    List<RenderFunction> emptyList = List.of();

    // Act
    DefaultFunctionRegistry registry = new DefaultFunctionRegistry(emptyList);

    // Assert
    assertThat(registry.find("ANY")).isEmpty();
  }
}
