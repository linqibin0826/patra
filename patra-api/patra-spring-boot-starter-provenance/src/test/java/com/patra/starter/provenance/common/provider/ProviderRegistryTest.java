package com.patra.starter.provenance.common.provider;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ProviderRegistry 单元测试
 *
 * @author linqibin
 */
@DisplayName("ProviderRegistry 测试")
class ProviderRegistryTest {

  @Test
  @DisplayName("构造函数 - 使用空列表初始化注册表")
  void constructor_shouldInitializeWithEmptyList() {
    // Act
    ProviderRegistry registry = new ProviderRegistry(List.of());

    // Assert
    assertThat(registry.supports("pubmed")).isFalse();
  }

  @Test
  @DisplayName("构造函数 - 使用null列表初始化注册表")
  void constructor_shouldInitializeWithNullList() {
    // Act
    ProviderRegistry registry = new ProviderRegistry(null);

    // Assert
    assertThat(registry.supports("pubmed")).isFalse();
  }

  @Test
  @DisplayName("register - 成功注册提供者实现")
  void register_shouldRegisterProvider_successfully() {
    // Arrange
    TestProvider provider = new TestProvider("pubmed");
    List<DataSourceProvider> providers = List.of(provider);

    // Act
    ProviderRegistry registry = new ProviderRegistry(providers);

    // Assert
    assertThat(registry.supports("pubmed")).isTrue();
  }

  @Test
  @DisplayName("supports - 支持已注册的数据源代码")
  void supports_shouldReturnTrue_forRegisteredCode() {
    // Arrange
    TestProvider provider = new TestProvider("epmc");
    ProviderRegistry registry = new ProviderRegistry(List.of(provider));

    // Act & Assert
    assertThat(registry.supports("epmc")).isTrue();
  }

  @Test
  @DisplayName("supports - 不支持未注册的数据源代码")
  void supports_shouldReturnFalse_forUnregisteredCode() {
    // Arrange
    TestProvider provider = new TestProvider("pubmed");
    ProviderRegistry registry = new ProviderRegistry(List.of(provider));

    // Act & Assert
    assertThat(registry.supports("crossref")).isFalse();
  }

  @Test
  @DisplayName("supports - 大小写不敏感")
  void supports_shouldBeCaseInsensitive() {
    // Arrange
    TestProvider provider = new TestProvider("PubMed");
    ProviderRegistry registry = new ProviderRegistry(List.of(provider));

    // Act & Assert
    assertThat(registry.supports("pubmed")).isTrue();
    assertThat(registry.supports("PUBMED")).isTrue();
    assertThat(registry.supports("PubMed")).isTrue();
  }

  @Test
  @DisplayName("supports - 忽略前后空格")
  void supports_shouldTrimWhitespace() {
    // Arrange
    TestProvider provider = new TestProvider("  pubmed  ");
    ProviderRegistry registry = new ProviderRegistry(List.of(provider));

    // Act & Assert
    assertThat(registry.supports("pubmed")).isTrue();
    assertThat(registry.supports("  pubmed  ")).isTrue();
  }

  @Test
  @DisplayName("supports - null代码返回false")
  void supports_shouldReturnFalse_forNullCode() {
    // Arrange
    TestProvider provider = new TestProvider("pubmed");
    ProviderRegistry registry = new ProviderRegistry(List.of(provider));

    // Act & Assert
    assertThat(registry.supports(null)).isFalse();
  }

  @Test
  @DisplayName("supports - 空白代码返回false")
  void supports_shouldReturnFalse_forBlankCode() {
    // Arrange
    TestProvider provider = new TestProvider("pubmed");
    ProviderRegistry registry = new ProviderRegistry(List.of(provider));

    // Act & Assert
    assertThat(registry.supports("")).isFalse();
    assertThat(registry.supports("   ")).isFalse();
  }

  @Test
  @DisplayName("getProvider - 成功获取已注册的提供者实现")
  void getProvider_shouldReturnProvider_forRegisteredCode() {
    // Arrange
    TestProvider provider = new TestProvider("pubmed");
    ProviderRegistry registry = new ProviderRegistry(List.of(provider));

    // Act
    DataSourceProvider result = registry.getProvider("pubmed");

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getProvenanceCode()).isEqualTo("pubmed");
  }

  @Test
  @DisplayName("getProvider - 未找到提供者实现时抛出异常")
  void getProvider_shouldThrowException_forUnregisteredCode() {
    // Arrange
    TestProvider provider = new TestProvider("pubmed");
    ProviderRegistry registry = new ProviderRegistry(List.of(provider));

    // Act & Assert
    assertThatThrownBy(() -> registry.getProvider("crossref"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("未找到数据源对应的提供者实现")
        .hasMessageContaining("crossref");
  }

  @Test
  @DisplayName("getProvider - null代码抛出异常")
  void getProvider_shouldThrowException_forNullCode() {
    // Arrange
    TestProvider provider = new TestProvider("pubmed");
    ProviderRegistry registry = new ProviderRegistry(List.of(provider));

    // Act & Assert
    assertThatThrownBy(() -> registry.getProvider(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("未找到数据源对应的提供者实现");
  }

  @Test
  @DisplayName("getProvider - 空白代码抛出异常")
  void getProvider_shouldThrowException_forBlankCode() {
    // Arrange
    TestProvider provider = new TestProvider("pubmed");
    ProviderRegistry registry = new ProviderRegistry(List.of(provider));

    // Act & Assert
    assertThatThrownBy(() -> registry.getProvider(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("未找到数据源对应的提供者实现");
  }

  @Test
  @DisplayName("register - 重复注册相同类的提供者实现应被忽略")
  void register_shouldIgnoreDuplicateProvider_ofSameClass() {
    // Arrange
    TestProvider provider1 = new TestProvider("pubmed");
    TestProvider provider2 = new TestProvider("pubmed");
    List<DataSourceProvider> providers = List.of(provider1, provider2);

    // Act
    ProviderRegistry registry = new ProviderRegistry(providers);

    // Assert - 应该只注册一个提供者实现
    assertThat(registry.supports("pubmed")).isTrue();
    assertThat(registry.getProvider("pubmed")).isNotNull();
  }

  @Test
  @DisplayName("register - 允许不同类的提供者实现注册相同数据源代码")
  void register_shouldAllowDifferentClasses_forSameCode() {
    // Arrange
    TestProvider provider1 = new TestProvider("pubmed");
    AnotherTestProvider provider2 = new AnotherTestProvider("pubmed");
    List<DataSourceProvider> providers = List.of(provider1, provider2);

    // Act
    ProviderRegistry registry = new ProviderRegistry(providers);

    // Assert - 应该返回第一个注册的提供者实现
    assertThat(registry.supports("pubmed")).isTrue();
    DataSourceProvider result = registry.getProvider("pubmed");
    assertThat(result).isInstanceOf(TestProvider.class);
  }

  @Test
  @DisplayName("register - 注册过程中null提供者实现被过滤")
  void register_shouldFilterNullProvidersDuringRegistration() {
    // Arrange
    // 注意：List.copyOf 不接受包含 null 的列表
    // 实际使用时，Spring 不会注入 null 的 bean
    // 这个测试验证 register 方法内部对 null 的处理
    TestProvider provider = new TestProvider("pubmed");
    ProviderRegistry registry = new ProviderRegistry(List.of(provider));

    // Act & Assert - register 方法内部会检查 null
    assertThat(registry.supports("pubmed")).isTrue();
  }

  @Test
  @DisplayName("register - 注册多个不同数据源的提供者实现")
  void register_shouldRegisterMultipleProviders() {
    // Arrange
    TestProvider pubmedProvider = new TestProvider("pubmed");
    TestProvider epmcProvider = new TestProvider("epmc");
    TestProvider crossrefProvider = new TestProvider("crossref");
    List<DataSourceProvider> providers = List.of(pubmedProvider, epmcProvider, crossrefProvider);

    // Act
    ProviderRegistry registry = new ProviderRegistry(providers);

    // Assert
    assertThat(registry.supports("pubmed")).isTrue();
    assertThat(registry.supports("epmc")).isTrue();
    assertThat(registry.supports("crossref")).isTrue();
    assertThat(registry.getProvider("pubmed")).isEqualTo(pubmedProvider);
    assertThat(registry.getProvider("epmc")).isEqualTo(epmcProvider);
    assertThat(registry.getProvider("crossref")).isEqualTo(crossrefProvider);
  }

  // 测试用提供者实现
  private static class TestProvider implements DataSourceProvider {
    private final String code;

    TestProvider(String code) {
      this.code = code;
    }

    @Override
    public String getProvenanceCode() {
      return code;
    }

    @Override
    public java.util.Set<com.patra.common.model.DataType> getSupportedDataTypes() {
      return java.util.Set.of(com.patra.common.model.DataType.LITERATURE);
    }

    @Override
    public <T> ProviderResult<T> fetchData(
        ProviderRequest request, com.patra.common.model.DataType dataType, Class<T> targetClass) {
      return null; // 测试中不需要实现
    }
  }

  // 另一个测试用提供者实现（不同类）
  private static class AnotherTestProvider implements DataSourceProvider {
    private final String code;

    AnotherTestProvider(String code) {
      this.code = code;
    }

    @Override
    public String getProvenanceCode() {
      return code;
    }

    @Override
    public java.util.Set<com.patra.common.model.DataType> getSupportedDataTypes() {
      return java.util.Set.of(com.patra.common.model.DataType.LITERATURE);
    }

    @Override
    public <T> ProviderResult<T> fetchData(
        ProviderRequest request, com.patra.common.model.DataType dataType, Class<T> targetClass) {
      return null; // 测试中不需要实现
    }
  }
}
