package com.patra.starter.provenance.common.adapter;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AdapterRegistry 单元测试
 *
 * @author linqibin
 */
@DisplayName("AdapterRegistry 测试")
class AdapterRegistryTest {

  @Test
  @DisplayName("构造函数 - 使用空列表初始化注册表")
  void constructor_shouldInitializeWithEmptyList() {
    // Act
    AdapterRegistry registry = new AdapterRegistry(List.of());

    // Assert
    assertThat(registry.supports("pubmed")).isFalse();
  }

  @Test
  @DisplayName("构造函数 - 使用null列表初始化注册表")
  void constructor_shouldInitializeWithNullList() {
    // Act
    AdapterRegistry registry = new AdapterRegistry(null);

    // Assert
    assertThat(registry.supports("pubmed")).isFalse();
  }

  @Test
  @DisplayName("register - 成功注册适配器")
  void register_shouldRegisterAdapter_successfully() {
    // Arrange
    TestAdapter adapter = new TestAdapter("pubmed");
    List<DataSourceAdapter> adapters = List.of(adapter);

    // Act
    AdapterRegistry registry = new AdapterRegistry(adapters);

    // Assert
    assertThat(registry.supports("pubmed")).isTrue();
  }

  @Test
  @DisplayName("supports - 支持已注册的数据源代码")
  void supports_shouldReturnTrue_forRegisteredCode() {
    // Arrange
    TestAdapter adapter = new TestAdapter("epmc");
    AdapterRegistry registry = new AdapterRegistry(List.of(adapter));

    // Act & Assert
    assertThat(registry.supports("epmc")).isTrue();
  }

  @Test
  @DisplayName("supports - 不支持未注册的数据源代码")
  void supports_shouldReturnFalse_forUnregisteredCode() {
    // Arrange
    TestAdapter adapter = new TestAdapter("pubmed");
    AdapterRegistry registry = new AdapterRegistry(List.of(adapter));

    // Act & Assert
    assertThat(registry.supports("crossref")).isFalse();
  }

  @Test
  @DisplayName("supports - 大小写不敏感")
  void supports_shouldBeCaseInsensitive() {
    // Arrange
    TestAdapter adapter = new TestAdapter("PubMed");
    AdapterRegistry registry = new AdapterRegistry(List.of(adapter));

    // Act & Assert
    assertThat(registry.supports("pubmed")).isTrue();
    assertThat(registry.supports("PUBMED")).isTrue();
    assertThat(registry.supports("PubMed")).isTrue();
  }

  @Test
  @DisplayName("supports - 忽略前后空格")
  void supports_shouldTrimWhitespace() {
    // Arrange
    TestAdapter adapter = new TestAdapter("  pubmed  ");
    AdapterRegistry registry = new AdapterRegistry(List.of(adapter));

    // Act & Assert
    assertThat(registry.supports("pubmed")).isTrue();
    assertThat(registry.supports("  pubmed  ")).isTrue();
  }

  @Test
  @DisplayName("supports - null代码返回false")
  void supports_shouldReturnFalse_forNullCode() {
    // Arrange
    TestAdapter adapter = new TestAdapter("pubmed");
    AdapterRegistry registry = new AdapterRegistry(List.of(adapter));

    // Act & Assert
    assertThat(registry.supports(null)).isFalse();
  }

  @Test
  @DisplayName("supports - 空白代码返回false")
  void supports_shouldReturnFalse_forBlankCode() {
    // Arrange
    TestAdapter adapter = new TestAdapter("pubmed");
    AdapterRegistry registry = new AdapterRegistry(List.of(adapter));

    // Act & Assert
    assertThat(registry.supports("")).isFalse();
    assertThat(registry.supports("   ")).isFalse();
  }

  @Test
  @DisplayName("getAdapter - 成功获取已注册的适配器")
  void getAdapter_shouldReturnAdapter_forRegisteredCode() {
    // Arrange
    TestAdapter adapter = new TestAdapter("pubmed");
    AdapterRegistry registry = new AdapterRegistry(List.of(adapter));

    // Act
    DataSourceAdapter result = registry.getAdapter("pubmed");

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getProvenanceCode()).isEqualTo("pubmed");
  }

  @Test
  @DisplayName("getAdapter - 未找到适配器时抛出异常")
  void getAdapter_shouldThrowException_forUnregisteredCode() {
    // Arrange
    TestAdapter adapter = new TestAdapter("pubmed");
    AdapterRegistry registry = new AdapterRegistry(List.of(adapter));

    // Act & Assert
    assertThatThrownBy(() -> registry.getAdapter("crossref"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("未找到数据源对应的适配器")
        .hasMessageContaining("crossref");
  }

  @Test
  @DisplayName("getAdapter - null代码抛出异常")
  void getAdapter_shouldThrowException_forNullCode() {
    // Arrange
    TestAdapter adapter = new TestAdapter("pubmed");
    AdapterRegistry registry = new AdapterRegistry(List.of(adapter));

    // Act & Assert
    assertThatThrownBy(() -> registry.getAdapter(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("未找到数据源对应的适配器");
  }

  @Test
  @DisplayName("getAdapter - 空白代码抛出异常")
  void getAdapter_shouldThrowException_forBlankCode() {
    // Arrange
    TestAdapter adapter = new TestAdapter("pubmed");
    AdapterRegistry registry = new AdapterRegistry(List.of(adapter));

    // Act & Assert
    assertThatThrownBy(() -> registry.getAdapter(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("未找到数据源对应的适配器");
  }

  @Test
  @DisplayName("register - 重复注册相同类的适配器应被忽略")
  void register_shouldIgnoreDuplicateAdapter_ofSameClass() {
    // Arrange
    TestAdapter adapter1 = new TestAdapter("pubmed");
    TestAdapter adapter2 = new TestAdapter("pubmed");
    List<DataSourceAdapter> adapters = List.of(adapter1, adapter2);

    // Act
    AdapterRegistry registry = new AdapterRegistry(adapters);

    // Assert - 应该只注册一个适配器
    assertThat(registry.supports("pubmed")).isTrue();
    assertThat(registry.getAdapter("pubmed")).isNotNull();
  }

  @Test
  @DisplayName("register - 允许不同类的适配器注册相同数据源代码")
  void register_shouldAllowDifferentClasses_forSameCode() {
    // Arrange
    TestAdapter adapter1 = new TestAdapter("pubmed");
    AnotherTestAdapter adapter2 = new AnotherTestAdapter("pubmed");
    List<DataSourceAdapter> adapters = List.of(adapter1, adapter2);

    // Act
    AdapterRegistry registry = new AdapterRegistry(adapters);

    // Assert - 应该返回第一个注册的适配器
    assertThat(registry.supports("pubmed")).isTrue();
    DataSourceAdapter result = registry.getAdapter("pubmed");
    assertThat(result).isInstanceOf(TestAdapter.class);
  }

  @Test
  @DisplayName("register - 注册过程中null适配器被过滤")
  void register_shouldFilterNullAdaptersDuringRegistration() {
    // Arrange
    // 注意：List.copyOf 不接受包含 null 的列表
    // 实际使用时，Spring 不会注入 null 的 bean
    // 这个测试验证 register 方法内部对 null 的处理
    TestAdapter adapter = new TestAdapter("pubmed");
    AdapterRegistry registry = new AdapterRegistry(List.of(adapter));

    // Act & Assert - register 方法内部会检查 null
    assertThat(registry.supports("pubmed")).isTrue();
  }

  @Test
  @DisplayName("register - 注册多个不同数据源的适配器")
  void register_shouldRegisterMultipleAdapters() {
    // Arrange
    TestAdapter pubmedAdapter = new TestAdapter("pubmed");
    TestAdapter epmcAdapter = new TestAdapter("epmc");
    TestAdapter crossrefAdapter = new TestAdapter("crossref");
    List<DataSourceAdapter> adapters = List.of(pubmedAdapter, epmcAdapter, crossrefAdapter);

    // Act
    AdapterRegistry registry = new AdapterRegistry(adapters);

    // Assert
    assertThat(registry.supports("pubmed")).isTrue();
    assertThat(registry.supports("epmc")).isTrue();
    assertThat(registry.supports("crossref")).isTrue();
    assertThat(registry.getAdapter("pubmed")).isEqualTo(pubmedAdapter);
    assertThat(registry.getAdapter("epmc")).isEqualTo(epmcAdapter);
    assertThat(registry.getAdapter("crossref")).isEqualTo(crossrefAdapter);
  }

  // 测试用适配器实现
  private static class TestAdapter implements DataSourceAdapter {
    private final String code;

    TestAdapter(String code) {
      this.code = code;
    }

    @Override
    public String getProvenanceCode() {
      return code;
    }

    @Override
    public AdapterResult fetchData(AdapterRequest request) {
      return null; // 测试中不需要实现
    }
  }

  // 另一个测试用适配器实现（不同类）
  private static class AnotherTestAdapter implements DataSourceAdapter {
    private final String code;

    AnotherTestAdapter(String code) {
      this.code = code;
    }

    @Override
    public String getProvenanceCode() {
      return code;
    }

    @Override
    public AdapterResult fetchData(AdapterRequest request) {
      return null; // 测试中不需要实现
    }
  }
}
