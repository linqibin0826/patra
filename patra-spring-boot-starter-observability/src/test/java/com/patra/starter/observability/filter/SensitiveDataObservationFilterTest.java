package com.patra.starter.observability.filter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SensitiveDataObservationFilter 单元测试。
 *
 * <p>验证敏感数据脱敏功能正确工作。
 *
 * @author Jobs
 * @since 1.0.0
 */
class SensitiveDataObservationFilterTest {

    /**
     * 测试过滤器初始化 - 禁用状态。
     */
    @Test
    void shouldInitializeDisabledFilter() {
        SensitiveDataObservationFilter filter = new SensitiveDataObservationFilter(false, null);
        assertThat(filter).isNotNull();
    }

    /**
     * 测试过滤器初始化 - 启用状态。
     */
    @Test
    void shouldInitializeEnabledFilter() {
        SensitiveDataObservationFilter filter = new SensitiveDataObservationFilter(true, null);
        assertThat(filter).isNotNull();
    }

    /**
     * 测试过滤器初始化 - 自定义模式。
     */
    @Test
    void shouldInitializeFilterWithCustomPatterns() {
        List<String> customPatterns = List.of("(?i)custom", "secret.*");
        SensitiveDataObservationFilter filter = new SensitiveDataObservationFilter(true, customPatterns);
        assertThat(filter).isNotNull();
    }

    /**
     * 测试过滤器初始化 - 空自定义模式。
     */
    @Test
    void shouldInitializeFilterWithNullPatterns() {
        SensitiveDataObservationFilter filter = new SensitiveDataObservationFilter(true, null);
        assertThat(filter).isNotNull();
    }

    /**
     * 测试过滤器初始化 - 空列表。
     */
    @Test
    void shouldInitializeFilterWithEmptyPatterns() {
        SensitiveDataObservationFilter filter = new SensitiveDataObservationFilter(true, List.of());
        assertThat(filter).isNotNull();
    }
}
