package com.patra.common.test.base;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BaseUnitTest 使用示例
 *
 * <p>测试策略: 展示 BaseUnitTest 基类的正确使用方式</p>
 * <p>测试目标: 验证 BaseUnitTest 基类的功能和生命周期</p>
 * <p>测试覆盖: Mock 交互验证、Mock 重置、测试生命周期</p>
 *
 * <h3>测试状态</h3>
 * <ul>
 *   <li>预期: 绿灯（BaseUnitTest 已实现，本测试作为使用示例）</li>
 *   <li>目的: 演示如何正确继承和使用 BaseUnitTest</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>领域层单元测试: 测试纯领域逻辑</li>
 *   <li>应用层 Mock 测试: 测试编排器和协调器</li>
 *   <li>基础设施层转换器测试: 测试 MapStruct 映射</li>
 * </ul>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BaseUnitTest 使用示例 - 验证基类功能")
class BaseUnitTestExample extends BaseUnitTest {

    // ========== 测试用接口和类 ==========

    /**
     * 测试用服务接口
     */
    interface TestService {
        String processData(String input);
        void saveData(String data);
        int calculateResult(int a, int b);
    }

    /**
     * 测试用实体类
     */
    static class TestEntity {
        private final String id;
        private final String value;

        public TestEntity(String id, String value) {
            this.id = id;
            this.value = value;
        }

        public String getId() {
            return id;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 测试用业务逻辑类
     */
    static class BusinessLogic {
        private final TestService testService;

        public BusinessLogic(TestService testService) {
            this.testService = testService;
        }

        public String executeWorkflow(String input) {
            String processed = testService.processData(input);
            testService.saveData(processed);
            return processed;
        }

        public int calculateSum(int a, int b) {
            return testService.calculateResult(a, b);
        }
    }

    // ========== Mock 对象声明 ==========

    @Mock
    private TestService mockService;

    // ========== 基本 Mock 测试示例 ==========

    @Test
    @DisplayName("示例: 应该能够使用 @Mock 注解创建 Mock 对象")
    void shouldUseMockAnnotation() {
        // Given: Mock 对象已通过 @Mock 注解创建
        assertThat(mockService).isNotNull();

        // When: 配置 Mock 行为
        when(mockService.processData("test")).thenReturn("processed");

        // Then: 验证 Mock 行为
        String result = mockService.processData("test");
        assertThat(result).isEqualTo("processed");
    }

    @Test
    @DisplayName("示例: 应该能够验证 Mock 交互")
    void shouldVerifyMockInteraction() {
        // Given: 创建业务逻辑实例
        BusinessLogic logic = new BusinessLogic(mockService);
        when(mockService.processData(any())).thenReturn("result");

        // When: 执行业务逻辑
        logic.executeWorkflow("input");

        // Then: 验证 Mock 方法被调用
        verify(mockService, times(1)).processData("input");
        verify(mockService, times(1)).saveData("result");
    }

    @Test
    @DisplayName("示例: 应该能够验证 Mock 方法调用顺序")
    void shouldVerifyMockInvocationOrder() {
        // Given: 创建业务逻辑实例
        BusinessLogic logic = new BusinessLogic(mockService);
        when(mockService.processData(any())).thenReturn("result");

        // When: 执行业务逻辑
        logic.executeWorkflow("input");

        // Then: 验证方法调用顺序
        var inOrder = inOrder(mockService);
        inOrder.verify(mockService).processData("input");
        inOrder.verify(mockService).saveData("result");
    }

    @Test
    @DisplayName("示例: 应该能够验证 Mock 未被调用")
    void shouldVerifyMockNeverCalled() {
        // Given: 创建业务逻辑实例（但不执行任何操作）
        BusinessLogic logic = new BusinessLogic(mockService);

        // When: 不执行任何业务逻辑

        // Then: 验证 Mock 方法未被调用
        verify(mockService, never()).processData(any());
        verify(mockService, never()).saveData(any());
    }

    // ========== 参数匹配器测试示例 ==========

    @Test
    @DisplayName("示例: 应该能够使用 ArgumentMatchers")
    void shouldUseArgumentMatchers() {
        // Given: 配置 Mock 使用参数匹配器
        when(mockService.processData(anyString())).thenReturn("matched");

        // When: 调用方法
        String result1 = mockService.processData("any-input");
        String result2 = mockService.processData("another-input");

        // Then: 验证匹配器工作
        assertThat(result1).isEqualTo("matched");
        assertThat(result2).isEqualTo("matched");
    }

    @Test
    @DisplayName("示例: 应该能够使用精确参数匹配")
    void shouldUseExactArgumentMatching() {
        // Given: 配置 Mock 使用精确参数
        when(mockService.processData("exact-input")).thenReturn("exact-result");
        when(mockService.processData("other-input")).thenReturn("other-result");

        // When: 调用方法
        String result1 = mockService.processData("exact-input");
        String result2 = mockService.processData("other-input");

        // Then: 验证精确匹配
        assertThat(result1).isEqualTo("exact-result");
        assertThat(result2).isEqualTo("other-result");
    }

    // ========== 异常处理测试示例 ==========

    @Test
    @DisplayName("示例: 应该能够 Mock 抛出异常")
    void shouldMockExceptionThrowing() {
        // Given: 配置 Mock 抛出异常
        when(mockService.processData("error"))
            .thenThrow(new IllegalArgumentException("Invalid input"));

        // When & Then: 验证异常抛出
        assertThatThrownBy(() -> mockService.processData("error"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid input");
    }

    @Test
    @DisplayName("示例: 应该能够 Mock void 方法抛出异常")
    void shouldMockVoidMethodExceptionThrowing() {
        // Given: 配置 void 方法抛出异常
        doThrow(new RuntimeException("Save failed"))
            .when(mockService).saveData("bad-data");

        // When & Then: 验证异常抛出
        assertThatThrownBy(() -> mockService.saveData("bad-data"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Save failed");
    }

    // ========== 返回值链式配置示例 ==========

    @Test
    @DisplayName("示例: 应该能够配置多次调用返回不同结果")
    void shouldConfigureMultipleInvocations() {
        // Given: 配置多次调用返回不同值
        when(mockService.calculateResult(1, 2))
            .thenReturn(3)
            .thenReturn(5)
            .thenReturn(7);

        // When: 多次调用
        int result1 = mockService.calculateResult(1, 2);
        int result2 = mockService.calculateResult(1, 2);
        int result3 = mockService.calculateResult(1, 2);

        // Then: 验证每次返回不同值
        assertThat(result1).isEqualTo(3);
        assertThat(result2).isEqualTo(5);
        assertThat(result3).isEqualTo(7);
    }

    // ========== 参数捕获器测试示例 ==========

    @Test
    @DisplayName("示例: 应该能够使用 ArgumentCaptor 捕获参数")
    void shouldUseArgumentCaptor() {
        // Given: 创建参数捕获器
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);

        // When: 执行业务逻辑
        mockService.saveData("captured-data");

        // Then: 捕获并验证参数
        verify(mockService).saveData(captor.capture());
        assertThat(captor.getValue()).isEqualTo("captured-data");
    }

    // ========== Spy 对象测试示例 ==========

    @Test
    @DisplayName("示例: 应该能够使用 Spy 部分 Mock 真实对象")
    void shouldUseSpyForPartialMocking() {
        // Given: 创建 Spy 对象（部分 Mock）
        var realLogic = new BusinessLogic(mockService);
        var spyLogic = spy(realLogic);

        when(mockService.processData("test")).thenReturn("processed");

        // When: 调用 Spy 对象的方法
        String result = spyLogic.executeWorkflow("test");

        // Then: 验证 Spy 调用了真实方法
        assertThat(result).isEqualTo("processed");
        verify(mockService).processData("test");
        verify(mockService).saveData("processed");
    }

    // ========== 测试生命周期示例 ==========

    @Test
    @DisplayName("示例: 应该在每个测试中独立初始化 Mock")
    void shouldInitializeMocksIndependently_Test1() {
        // Given: 配置 Mock
        when(mockService.processData("test1")).thenReturn("result1");

        // When & Then: 验证配置
        assertThat(mockService.processData("test1")).isEqualTo("result1");
    }

    @Test
    @DisplayName("示例: 应该在每个测试中独立初始化 Mock（验证隔离）")
    void shouldInitializeMocksIndependently_Test2() {
        // Given: 前一个测试的配置不影响此测试

        // When: 调用未配置的方法
        String result = mockService.processData("test1");

        // Then: 验证返回默认值（null）
        assertThat(result).isNull();
    }

    // ========== AssertJ 流式断言示例 ==========

    @Test
    @DisplayName("示例: 应该能够使用 AssertJ 流式断言")
    void shouldUseAssertJFluentAssertions() {
        // Given: 创建测试实体
        TestEntity entity = new TestEntity("id-123", "test-value");

        // When & Then: 使用流式断言验证
        assertThat(entity)
            .isNotNull()
            .extracting(TestEntity::getId, TestEntity::getValue)
            .containsExactly("id-123", "test-value");
    }

    @Test
    @DisplayName("示例: 应该能够使用 AssertJ 验证集合")
    void shouldUseAssertJForCollections() {
        // Given: 创建实体列表
        var entities = java.util.List.of(
            new TestEntity("id-1", "value-1"),
            new TestEntity("id-2", "value-2"),
            new TestEntity("id-3", "value-3")
        );

        // When & Then: 使用流式断言验证集合
        assertThat(entities)
            .hasSize(3)
            .extracting(TestEntity::getId)
            .containsExactly("id-1", "id-2", "id-3");
    }

    // ========== 复杂场景测试示例 ==========

    @Test
    @DisplayName("示例: 应该能够测试复杂的业务场景")
    void shouldTestComplexBusinessScenario() {
        // Given: 准备复杂场景
        BusinessLogic logic = new BusinessLogic(mockService);

        // 配置 Mock 行为
        when(mockService.processData("complex-input"))
            .thenReturn("processed-output");

        // When: 执行复杂业务逻辑
        String result = logic.executeWorkflow("complex-input");

        // Then: 验证结果和交互
        assertThat(result).isEqualTo("processed-output");

        // 验证方法调用
        verify(mockService).processData("complex-input");
        verify(mockService).saveData("processed-output");

        // 验证没有其他交互
        verifyNoMoreInteractions(mockService);
    }

    @Test
    @DisplayName("示例: 应该能够重置 Mock 对象（如果需要）")
    void shouldResetMockIfNeeded() {
        // Given: 配置并使用 Mock
        when(mockService.processData("test")).thenReturn("result");
        mockService.processData("test");

        // When: 重置 Mock
        reset(mockService);

        // Then: 验证 Mock 被重置
        String result = mockService.processData("test");
        assertThat(result).isNull(); // 未配置返回 null
    }
}
