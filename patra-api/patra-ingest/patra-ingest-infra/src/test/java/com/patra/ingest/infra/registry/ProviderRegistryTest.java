package com.patra.ingest.infra.registry;

import com.patra.ingest.domain.model.DataType;
import com.patra.starter.provenance.common.provider.DataSourceProvider;
import com.patra.starter.provenance.common.provider.ProviderRequest;
import com.patra.starter.provenance.common.provider.ProviderResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ProviderRegistry 测试类
 *
 * <p>测试 ProviderRegistry 的二维索引功能：
 * <ul>
 *   <li>自动注册和发现 Provider</li>
 *   <li>二维查找 (provenanceCode, dataType) → Provider</li>
 *   <li>一对多支持（一个 Provider 支持多个 DataType）</li>
 *   <li>异常处理和边界条件</li>
 * </ul>
 *
 * @author Patra Architecture Team
 * @since v2.0
 */
@DisplayName("ProviderRegistry 二维索引测试")
class ProviderRegistryTest {

    // ========== Mock Provider 实现 ==========

    /**
     * Mock PubMed Provider（支持多种数据类型）
     */
    static class MockPubMedProvider implements DataSourceProvider {
        private static final Set<DataType> SUPPORTED_TYPES = Set.of(
            DataType.LITERATURE,
            DataType.CITATION,
            DataType.AUTHOR
        );

        @Override
        public String getProvenanceCode() {
            return "pubmed";
        }

        @Override
        public Set<DataType> getSupportedDataTypes() {
            return SUPPORTED_TYPES;
        }

        @Override
        public <T> ProviderResult<T> fetchData(
                ProviderRequest request,
                DataType dataType,
                Class<T> targetClass) {
            return ProviderResult.success(List.of(), dataType, null);
        }
    }

    /**
     * Mock DOAJ Provider（支持期刊类型）
     */
    static class MockDoajProvider implements DataSourceProvider {
        @Override
        public String getProvenanceCode() {
            return "doaj";
        }

        @Override
        public Set<DataType> getSupportedDataTypes() {
            return Set.of(DataType.JOURNAL);
        }

        @Override
        public <T> ProviderResult<T> fetchData(
                ProviderRequest request,
                DataType dataType,
                Class<T> targetClass) {
            return ProviderResult.success(List.of(), dataType, null);
        }
    }

    /**
     * Mock Crossref Provider（支持文献和引用）
     */
    static class MockCrossrefProvider implements DataSourceProvider {
        @Override
        public String getProvenanceCode() {
            return "crossref";
        }

        @Override
        public Set<DataType> getSupportedDataTypes() {
            return Set.of(DataType.LITERATURE, DataType.CITATION);
        }

        @Override
        public <T> ProviderResult<T> fetchData(
                ProviderRequest request,
                DataType dataType,
                Class<T> targetClass) {
            return ProviderResult.success(List.of(), dataType, null);
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 创建测试用的 ProviderRegistry
     */
    private ProviderRegistry createTestRegistry() {
        List<DataSourceProvider> providers = List.of(
            new MockPubMedProvider(),
            new MockDoajProvider(),
            new MockCrossrefProvider()
        );
        return new ProviderRegistry(providers);
    }

    // ========== 测试用例 ==========

    @Test
    @DisplayName("自动注册 - 应该注册所有 Provider 并支持多种数据类型")
    void should_auto_register_providers_with_multiple_types() {
        // Given: 多个 Provider，每个支持不同的 DataType
        MockPubMedProvider pubmedProvider = new MockPubMedProvider();
        MockDoajProvider doajProvider = new MockDoajProvider();
        MockCrossrefProvider crossrefProvider = new MockCrossrefProvider();

        List<DataSourceProvider> providers = List.of(
            pubmedProvider,
            doajProvider,
            crossrefProvider
        );

        // When: 自动注册
        ProviderRegistry registry = new ProviderRegistry(providers);

        // Then: 验证二维索引（PubMed 支持 3 种类型）
        assertTrue(registry.supports("pubmed", DataType.LITERATURE));
        assertTrue(registry.supports("pubmed", DataType.CITATION));
        assertTrue(registry.supports("pubmed", DataType.AUTHOR));

        // Then: 验证 DOAJ 只支持 JOURNAL
        assertTrue(registry.supports("doaj", DataType.JOURNAL));
        assertFalse(registry.supports("doaj", DataType.LITERATURE));

        // Then: 验证 Crossref 支持 2 种类型
        assertTrue(registry.supports("crossref", DataType.LITERATURE));
        assertTrue(registry.supports("crossref", DataType.CITATION));
    }

    @Test
    @DisplayName("getProvider - 应该通过二维索引查找 Provider")
    void should_get_provider_by_provenance_and_data_type() {
        // Given
        ProviderRegistry registry = createTestRegistry();

        // When
        DataSourceProvider provider = registry.getProvider("pubmed", DataType.LITERATURE);

        // Then
        assertNotNull(provider);
        assertEquals("pubmed", provider.getProvenanceCode());
        assertTrue(provider.supports(DataType.LITERATURE));
    }

    @Test
    @DisplayName("getProvider - 找不到 Provider 时应该抛出异常")
    void should_throw_exception_when_provider_not_found() {
        // Given
        ProviderRegistry registry = createTestRegistry();

        // When & Then
        assertThatThrownBy(() -> registry.getProvider("unknown", DataType.LITERATURE))
            .isInstanceOf(ProviderNotFoundException.class)
            .hasMessageContaining("未找到Provider")
            .hasMessageContaining("unknown")
            .hasMessageContaining("LITERATURE");
    }

    @Test
    @DisplayName("getProvider - 数据类型不支持时应该抛出异常")
    void should_throw_exception_when_data_type_not_supported() {
        // Given
        ProviderRegistry registry = createTestRegistry();

        // When & Then: PubMed 不支持 DRUG 类型
        assertThatThrownBy(() -> registry.getProvider("pubmed", DataType.DRUG))
            .isInstanceOf(ProviderNotFoundException.class)
            .hasMessageContaining("未找到Provider")
            .hasMessageContaining("pubmed")
            .hasMessageContaining("DRUG");
    }

    @Test
    @DisplayName("findProvider - 应该返回 Optional 包装的 Provider")
    void should_find_provider_with_optional() {
        // Given
        ProviderRegistry registry = createTestRegistry();

        // When
        Optional<DataSourceProvider> provider = registry.findProvider("pubmed", DataType.LITERATURE);

        // Then
        assertTrue(provider.isPresent());
        assertEquals("pubmed", provider.get().getProvenanceCode());
    }

    @Test
    @DisplayName("findProvider - 找不到时应该返回空 Optional")
    void should_return_empty_optional_when_not_found() {
        // Given
        ProviderRegistry registry = createTestRegistry();

        // When
        Optional<DataSourceProvider> provider = registry.findProvider("unknown", DataType.LITERATURE);

        // Then
        assertFalse(provider.isPresent());
    }

    @Test
    @DisplayName("supports - 应该正确判断是否支持指定的数据源和数据类型")
    void should_check_provenance_and_data_type_support() {
        // Given
        ProviderRegistry registry = createTestRegistry();

        // Then: PubMed 支持多种类型
        assertTrue(registry.supports("pubmed", DataType.LITERATURE));
        assertTrue(registry.supports("pubmed", DataType.CITATION));
        assertTrue(registry.supports("pubmed", DataType.AUTHOR));

        // Then: PubMed 不支持 DRUG
        assertFalse(registry.supports("pubmed", DataType.DRUG));

        // Then: 未知数据源
        assertFalse(registry.supports("unknown", DataType.LITERATURE));
    }

    @Test
    @DisplayName("getSupportedTypes - 应该返回数据源支持的所有类型")
    void should_return_supported_types_for_provenance() {
        // Given
        ProviderRegistry registry = createTestRegistry();

        // When
        Set<DataType> types = registry.getSupportedTypes("pubmed");

        // Then
        assertThat(types)
            .hasSize(3)
            .contains(DataType.LITERATURE, DataType.CITATION, DataType.AUTHOR);
    }

    @Test
    @DisplayName("getSupportedTypes - 未知数据源应该返回空集合")
    void should_return_empty_set_for_unknown_provenance() {
        // Given
        ProviderRegistry registry = createTestRegistry();

        // When
        Set<DataType> types = registry.getSupportedTypes("unknown");

        // Then
        assertTrue(types.isEmpty());
    }

    @Test
    @DisplayName("getProvidersByDataType - 应该返回所有支持指定类型的 Provider")
    void should_return_all_providers_for_data_type() {
        // Given: PubMed 和 Crossref 都支持 LITERATURE
        ProviderRegistry registry = createTestRegistry();

        // When
        List<DataSourceProvider> providers = registry.getProvidersByDataType(DataType.LITERATURE);

        // Then
        assertThat(providers).hasSize(2);

        Set<String> provenanceCodes = Set.of(
            providers.get(0).getProvenanceCode(),
            providers.get(1).getProvenanceCode()
        );
        assertThat(provenanceCodes).contains("pubmed", "crossref");
    }

    @Test
    @DisplayName("getProvidersByDataType - 没有 Provider 支持时应该返回空列表")
    void should_return_empty_list_when_no_provider_supports_type() {
        // Given
        ProviderRegistry registry = createTestRegistry();

        // When
        List<DataSourceProvider> providers = registry.getProvidersByDataType(DataType.DRUG);

        // Then
        assertTrue(providers.isEmpty());
    }

    @Test
    @DisplayName("getAllProviders - 应该返回所有注册的 Provider")
    void should_return_all_registered_providers() {
        // Given
        ProviderRegistry registry = createTestRegistry();

        // When
        List<DataSourceProvider> providers = registry.getAllProviders();

        // Then
        assertThat(providers).hasSize(3);
    }

    @Test
    @DisplayName("重复注册检测 - 相同 provenanceCode 应该只保留第一个")
    void should_detect_duplicate_provenance_code() {
        // Given: 两个 Provider 使用相同的 provenanceCode
        MockPubMedProvider provider1 = new MockPubMedProvider();
        MockPubMedProvider provider2 = new MockPubMedProvider();

        List<DataSourceProvider> providers = List.of(provider1, provider2);

        // When: 注册应该成功但输出警告
        ProviderRegistry registry = new ProviderRegistry(providers);

        // Then: 只保留第一个注册的 Provider
        DataSourceProvider provider = registry.getProvider("pubmed", DataType.LITERATURE);
        assertSame(provider1, provider);

        // Then: 所有 Provider 列表只应该包含一个
        List<DataSourceProvider> allProviders = registry.getAllProviders();
        assertThat(allProviders).hasSize(1);
    }

    @Test
    @DisplayName("空列表 - 传入空列表应该创建空注册表")
    void should_handle_empty_provider_list() {
        // Given
        ProviderRegistry registry = new ProviderRegistry(List.of());

        // When
        List<DataSourceProvider> providers = registry.getAllProviders();

        // Then
        assertTrue(providers.isEmpty());
    }

    @Test
    @DisplayName("null 列表 - 传入 null 应该创建空注册表")
    void should_handle_null_provider_list() {
        // Given & When
        ProviderRegistry registry = new ProviderRegistry(null);

        // When
        List<DataSourceProvider> providers = registry.getAllProviders();

        // Then
        assertTrue(providers.isEmpty());
    }

    @Test
    @DisplayName("大小写不敏感 - provenanceCode 查找应该忽略大小写")
    void should_ignore_case_when_lookup_provenance_code() {
        // Given
        ProviderRegistry registry = createTestRegistry();

        // When & Then: 大写查找
        assertTrue(registry.supports("PUBMED", DataType.LITERATURE));
        DataSourceProvider provider1 = registry.getProvider("PUBMED", DataType.LITERATURE);
        assertNotNull(provider1);

        // When & Then: 混合大小写
        assertTrue(registry.supports("PubMed", DataType.LITERATURE));
        DataSourceProvider provider2 = registry.getProvider("PubMed", DataType.LITERATURE);
        assertNotNull(provider2);

        // Then: 应该返回同一个 Provider 实例
        assertSame(provider1, provider2);
    }
}
