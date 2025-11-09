package com.patra.common.test.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * TestDataBuilder 元测试
 *
 * <p>测试策略: 纯单元测试，无需 Mock</p>
 * <p>测试目标: 验证 TestDataBuilder 抽象基类的核心功能</p>
 * <p>测试覆盖: build(), buildList(), buildAndSave() 方法</p>
 *
 * <h3>测试状态</h3>
 * <ul>
 *   <li>预期: 红灯（测试失败），因为部分功能未实现</li>
 *   <li>目的: TDD 第一步，先写测试让它们失败</li>
 * </ul>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
@DisplayName("TestDataBuilder 元测试 - 验证测试数据构建器基类")
class TestDataBuilderTest {

    // ========== 测试用具体实现类 ==========

    /**
     * 简单测试对象
     */
    static class SimpleObject {
        private final String name;
        private final int value;

        public SimpleObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * 用于测试的具体 Builder 实现
     */
    static class SimpleObjectBuilder extends TestDataBuilder<SimpleObject> {
        private String name = "default-name";
        private int value = 100;

        public SimpleObjectBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public SimpleObjectBuilder withValue(int value) {
            this.value = value;
            return this;
        }

        @Override
        public SimpleObject build() {
            return new SimpleObject(name, value);
        }
    }

    // ========== build() 方法测试 ==========

    @Test
    @DisplayName("应该能够构建单个对象 - 使用默认值")
    void shouldBuildSingleObjectWithDefaultValues() {
        // Given: 创建构建器实例
        SimpleObjectBuilder builder = new SimpleObjectBuilder();

        // When: 调用 build() 方法
        SimpleObject result = builder.build();

        // Then: 验证构建的对象使用默认值
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("default-name");
        assertThat(result.getValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("应该能够构建单个对象 - 使用自定义值")
    void shouldBuildSingleObjectWithCustomValues() {
        // Given: 创建构建器并设置自定义值
        SimpleObjectBuilder builder = new SimpleObjectBuilder()
            .withName("custom-name")
            .withValue(999);

        // When: 调用 build() 方法
        SimpleObject result = builder.build();

        // Then: 验证构建的对象使用自定义值
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("custom-name");
        assertThat(result.getValue()).isEqualTo(999);
    }

    @Test
    @DisplayName("应该支持链式调用")
    void shouldSupportFluentAPI() {
        // Given & When: 使用链式调用构建对象
        SimpleObject result = new SimpleObjectBuilder()
            .withName("fluent-name")
            .withValue(777)
            .build();

        // Then: 验证链式调用正常工作
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("fluent-name");
        assertThat(result.getValue()).isEqualTo(777);
    }

    // ========== buildList() 方法测试 ==========

    @Test
    @DisplayName("应该能够批量构建指定数量的对象")
    void shouldBuildListWithSpecifiedCount() {
        // Given: 创建构建器实例
        SimpleObjectBuilder builder = new SimpleObjectBuilder()
            .withName("batch-object")
            .withValue(500);

        // When: 调用 buildList(5) 构建 5 个对象
        List<SimpleObject> results = builder.buildList(5);

        // Then: 验证生成了 5 个对象
        assertThat(results)
            .hasSize(5)
            .allMatch(obj -> obj.getName().equals("batch-object"))
            .allMatch(obj -> obj.getValue() == 500);
    }

    @Test
    @DisplayName("应该能够构建大量对象")
    void shouldBuildLargeList() {
        // Given: 创建构建器实例
        SimpleObjectBuilder builder = new SimpleObjectBuilder();

        // When: 构建 100 个对象
        List<SimpleObject> results = builder.buildList(100);

        // Then: 验证生成了 100 个对象
        assertThat(results).hasSize(100);
    }

    @Test
    @DisplayName("应该能够构建单元素列表")
    void shouldBuildSingleElementList() {
        // Given: 创建构建器实例
        SimpleObjectBuilder builder = new SimpleObjectBuilder()
            .withName("single-element");

        // When: 构建只有 1 个元素的列表
        List<SimpleObject> results = builder.buildList(1);

        // Then: 验证列表大小为 1
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("single-element");
    }

    @Test
    @DisplayName("应该拒绝构建零个对象")
    void shouldRejectBuildListWithZeroCount() {
        // Given: 创建构建器实例
        SimpleObjectBuilder builder = new SimpleObjectBuilder();

        // When & Then: 验证 count = 0 会抛出异常
        assertThatThrownBy(() -> builder.buildList(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("构建数量必须大于 0");
    }

    @Test
    @DisplayName("应该拒绝构建负数个对象")
    void shouldRejectBuildListWithNegativeCount() {
        // Given: 创建构建器实例
        SimpleObjectBuilder builder = new SimpleObjectBuilder();

        // When & Then: 验证 count < 0 会抛出异常
        assertThatThrownBy(() -> builder.buildList(-5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("构建数量必须大于 0");
    }

    // ========== buildAndSave() 方法测试 ==========

    @Test
    @DisplayName("应该在未实现时抛出 UnsupportedOperationException")
    void shouldThrowUnsupportedOperationExceptionWhenBuildAndSaveNotImplemented() {
        // Given: 创建构建器实例（未覆盖 buildAndSave）
        SimpleObjectBuilder builder = new SimpleObjectBuilder();

        // When & Then: 验证调用 buildAndSave() 会抛出异常
        assertThatThrownBy(() -> builder.buildAndSave(null))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("子类需要实现 buildAndSave() 方法");
    }

    @Test
    @DisplayName("应该允许子类覆盖 buildAndSave() 方法")
    void shouldAllowSubclassToOverrideBuildAndSave() {
        // Given: 创建覆盖了 buildAndSave() 的构建器
        class CustomBuilderWithSave extends SimpleObjectBuilder {
            private boolean savedCalled = false;

            @Override
            public SimpleObject buildAndSave(Object repository) {
                savedCalled = true;
                return build();
            }

            public boolean isSavedCalled() {
                return savedCalled;
            }
        }

        CustomBuilderWithSave builder = new CustomBuilderWithSave();

        // When: 调用 buildAndSave()
        SimpleObject result = builder.buildAndSave("mock-repository");

        // Then: 验证方法被正确调用
        assertThat(result).isNotNull();
        assertThat(builder.isSavedCalled()).isTrue();
    }

    // ========== 边界情况测试 ==========

    @Test
    @DisplayName("应该能够多次调用 build() 且每次返回新对象")
    void shouldReturnNewObjectOnEachBuildCall() {
        // Given: 创建构建器实例
        SimpleObjectBuilder builder = new SimpleObjectBuilder()
            .withName("test")
            .withValue(123);

        // When: 多次调用 build()
        SimpleObject obj1 = builder.build();
        SimpleObject obj2 = builder.build();

        // Then: 验证每次返回新对象（不是同一个引用）
        assertThat(obj1).isNotSameAs(obj2);
        // 但内容应该相同（因为构建器状态未改变）
        assertThat(obj1.getName()).isEqualTo(obj2.getName());
        assertThat(obj1.getValue()).isEqualTo(obj2.getValue());
    }

    @Test
    @DisplayName("应该允许修改构建器状态后再次构建")
    void shouldAllowRebuildWithModifiedState() {
        // Given: 创建构建器并构建第一个对象
        SimpleObjectBuilder builder = new SimpleObjectBuilder()
            .withName("first")
            .withValue(100);
        SimpleObject first = builder.build();

        // When: 修改构建器状态并构建第二个对象
        builder.withName("second").withValue(200);
        SimpleObject second = builder.build();

        // Then: 验证第一个对象未被修改
        assertThat(first.getName()).isEqualTo("first");
        assertThat(first.getValue()).isEqualTo(100);

        // Then: 验证第二个对象使用新状态
        assertThat(second.getName()).isEqualTo("second");
        assertThat(second.getValue()).isEqualTo(200);
    }
}
