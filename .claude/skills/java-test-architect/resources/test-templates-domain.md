# Domain 层测试模板

纯 Java 单元测试，无需 Spring 上下文，无需 Mock，执行速度快。

## 值对象 (Value Object) 测试

### Record 类型测试模板

```java
package com.patra.{service}.domain.model.vo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.*;

@DisplayName("ProvenanceCode 值对象测试")
class ProvenanceCodeTest {

    @Nested
    @DisplayName("创建验证")
    class Creation {

        @Test
        @DisplayName("应该使用有效值创建 ProvenanceCode")
        void shouldCreateWithValidValue() {
            // Act
            ProvenanceCode code = new ProvenanceCode("PUBMED");

            // Assert
            assertThat(code.value()).isEqualTo("PUBMED");
        }

        @Test
        @DisplayName("应该拒绝 null 值")
        void shouldRejectNullValue() {
            // Act & Assert
            assertThatThrownBy(() -> new ProvenanceCode(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Code cannot be null");
        }

        @Test
        @DisplayName("应该拒绝空字符串")
        void shouldRejectEmptyString() {
            // Act & Assert
            assertThatThrownBy(() -> new ProvenanceCode(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Code cannot be empty");
        }
    }

    @Nested
    @DisplayName("相等性测试")
    class Equality {

        @Test
        @DisplayName("相同值的两个实例应该相等")
        void shouldBeEqualForSameValue() {
            // Arrange
            ProvenanceCode code1 = new ProvenanceCode("PUBMED");
            ProvenanceCode code2 = new ProvenanceCode("PUBMED");

            // Assert
            assertThat(code1).isEqualTo(code2);
            assertThat(code1.hashCode()).isEqualTo(code2.hashCode());
        }

        @Test
        @DisplayName("不同值的两个实例不应该相等")
        void shouldNotBeEqualForDifferentValues() {
            // Arrange
            ProvenanceCode code1 = new ProvenanceCode("PUBMED");
            ProvenanceCode code2 = new ProvenanceCode("EPMC");

            // Assert
            assertThat(code1).isNotEqualTo(code2);
        }
    }
}
```

### Sealed Interface 测试模板

```java
package com.patra.{service}.domain.model.vo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

@DisplayName("WindowSpec 密封接口测试")
class WindowSpecTest {

    @Test
    @DisplayName("应该创建单一窗口规格")
    void shouldCreateSingleWindowSpec() {
        // Act
        WindowSpec spec = WindowSpec.ofSingle();

        // Assert
        assertThat(spec).isInstanceOf(WindowSpec.Single.class);
        assertThat(spec.getType()).isEqualTo("SINGLE");
        assertThat(spec.getLabel()).isEqualTo("单一窗口");
    }

    @Test
    @DisplayName("应该创建滑动窗口规格")
    void shouldCreateSlidingWindowSpec() {
        // Act
        WindowSpec spec = WindowSpec.ofSliding(100, 10);

        // Assert
        assertThat(spec).isInstanceOf(WindowSpec.Sliding.class);
        WindowSpec.Sliding sliding = (WindowSpec.Sliding) spec;
        assertThat(sliding.windowSize()).isEqualTo(100);
        assertThat(sliding.stepSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("应该拒绝无效的窗口参数")
    void shouldRejectInvalidWindowParameters() {
        assertThatThrownBy(() -> WindowSpec.ofSliding(0, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Window size must be positive");

        assertThatThrownBy(() -> WindowSpec.ofSliding(100, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Step size must be positive");
    }

    @ParameterizedTest
    @DisplayName("应该正确序列化为 JSON")
    @MethodSource("windowSpecProvider")
    void shouldSerializeToJson(WindowSpec spec, String expectedJson) {
        // Act
        String json = spec.toJson();

        // Assert
        assertThat(json).isEqualTo(expectedJson);
    }

    private static Stream<Arguments> windowSpecProvider() {
        return Stream.of(
            Arguments.of(WindowSpec.ofSingle(), "{\"type\":\"SINGLE\"}"),
            Arguments.of(WindowSpec.ofSliding(100, 10),
                "{\"type\":\"SLIDING\",\"windowSize\":100,\"stepSize\":10}")
        );
    }
}
```

## 聚合根 (Aggregate Root) 测试

```java
package com.patra.{service}.domain.model.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.*;

@DisplayName("BatchPlan 聚合根测试")
class BatchPlanTest {

    private BatchPlan plan;

    @BeforeEach
    void setUp() {
        plan = BatchPlan.builder()
            .id(new BatchPlanId(1L))
            .provenanceCode("PUBMED")
            .status(PlanStatus.DRAFT)
            .slices(new ArrayList<>())
            .build();
    }

    @Nested
    @DisplayName("状态转换")
    class StateTransitions {

        @Test
        @DisplayName("应该从 DRAFT 转换到 READY")
        void shouldTransitionFromDraftToReady() {
            // Arrange
            assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);

            // Act
            plan.markAsReady();

            // Assert
            assertThat(plan.getStatus()).isEqualTo(PlanStatus.READY);
        }

        @Test
        @DisplayName("应该在所有切片完成时标记为完成")
        void shouldMarkAsCompletedWhenAllSlicesComplete() {
            // Arrange
            plan.addSlice(createSlice(SliceStatus.COMPLETED));
            plan.addSlice(createSlice(SliceStatus.COMPLETED));

            // Act
            plan.updateCompletionStatus();

            // Assert
            assertThat(plan.getStatus()).isEqualTo(PlanStatus.COMPLETED);
        }

        @Test
        @DisplayName("不应该从 CANCELLED 状态转换")
        void shouldNotTransitionFromCancelled() {
            // Arrange
            plan.cancel();

            // Act & Assert
            assertThatThrownBy(() -> plan.markAsReady())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition from CANCELLED");
        }
    }

    @Nested
    @DisplayName("业务规则")
    class BusinessRules {

        @Test
        @DisplayName("应该验证至少有一个切片")
        void shouldRequireAtLeastOneSlice() {
            // Act & Assert
            assertThatThrownBy(() -> plan.validate())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Plan must have at least one slice");
        }

        @Test
        @DisplayName("应该限制最大切片数量")
        void shouldLimitMaximumSlices() {
            // Arrange
            for (int i = 0; i < 100; i++) {
                plan.addSlice(createSlice());
            }

            // Act & Assert
            assertThatThrownBy(() -> plan.addSlice(createSlice()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Maximum slices exceeded");
        }
    }

    @Nested
    @DisplayName("领域事件")
    class DomainEvents {

        @Test
        @DisplayName("应该在状态改变时发布事件")
        void shouldPublishEventOnStatusChange() {
            // Act
            var events = plan.markAsReady();

            // Assert
            assertThat(events).hasSize(1);
            assertThat(events.get(0))
                .isInstanceOf(PlanStatusChangedEvent.class)
                .satisfies(event -> {
                    var statusEvent = (PlanStatusChangedEvent) event;
                    assertThat(statusEvent.getPlanId()).isEqualTo(plan.getId());
                    assertThat(statusEvent.getNewStatus()).isEqualTo(PlanStatus.READY);
                });
        }
    }

    // Helper methods
    private Slice createSlice() {
        return createSlice(SliceStatus.PENDING);
    }

    private Slice createSlice(SliceStatus status) {
        return Slice.builder()
            .id(new SliceId(UUID.randomUUID().toString()))
            .status(status)
            .build();
    }
}
```

## 领域服务 (Domain Service) 测试

```java
package com.patra.{service}.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@DisplayName("SliceStatusCalculator 领域服务测试")
class SliceStatusCalculatorTest {

    private SliceStatusCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new SliceStatusCalculator();
    }

    @Test
    @DisplayName("应该计算切片完成百分比")
    void shouldCalculateCompletionPercentage() {
        // Arrange
        List<Slice> slices = List.of(
            createSlice(SliceStatus.COMPLETED),
            createSlice(SliceStatus.COMPLETED),
            createSlice(SliceStatus.RUNNING),
            createSlice(SliceStatus.PENDING)
        );

        // Act
        BigDecimal percentage = calculator.calculateCompletionPercentage(slices);

        // Assert
        assertThat(percentage).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("应该识别所有切片完成")
    void shouldIdentifyAllSlicesCompleted() {
        // Arrange
        List<Slice> slices = List.of(
            createSlice(SliceStatus.COMPLETED),
            createSlice(SliceStatus.COMPLETED)
        );

        // Act
        boolean allCompleted = calculator.areAllSlicesCompleted(slices);

        // Assert
        assertThat(allCompleted).isTrue();
    }

    @Test
    @DisplayName("应该处理空切片列表")
    void shouldHandleEmptySliceList() {
        // Arrange
        List<Slice> slices = Collections.emptyList();

        // Act
        BigDecimal percentage = calculator.calculateCompletionPercentage(slices);

        // Assert
        assertThat(percentage).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("应该根据优先级排序切片")
    void shouldSortSlicesByPriority() {
        // Arrange
        List<Slice> slices = List.of(
            createSliceWithPriority(3),
            createSliceWithPriority(1),
            createSliceWithPriority(2)
        );

        // Act
        List<Slice> sorted = calculator.sortByPriority(slices);

        // Assert
        assertThat(sorted)
            .extracting(Slice::getPriority)
            .containsExactly(1, 2, 3);
    }
}
```

## 测试最佳实践

### 1. 使用 @Nested 组织测试

```java
class EntityTest {
    @Nested
    @DisplayName("创建")
    class Creation { }

    @Nested
    @DisplayName("验证")
    class Validation { }

    @Nested
    @DisplayName("状态转换")
    class StateTransitions { }
}
```

### 2. 参数化测试

```java
@ParameterizedTest
@ValueSource(strings = {"", " ", "  "})
@DisplayName("应该拒绝空白字符串")
void shouldRejectBlankStrings(String value) {
    assertThatThrownBy(() -> new Name(value))
        .isInstanceOf(IllegalArgumentException.class);
}
```

### 3. 测试数据构造器

```java
class TestDataBuilder {
    public static BatchPlan.BatchPlanBuilder aDefaultPlan() {
        return BatchPlan.builder()
            .id(new BatchPlanId(1L))
            .provenanceCode("TEST")
            .status(PlanStatus.DRAFT);
    }

    public static BatchPlan aPlanWithSlices(int count) {
        var builder = aDefaultPlan();
        for (int i = 0; i < count; i++) {
            builder.slice(createSlice());
        }
        return builder.build();
    }
}
```

## 常见断言模式

```java
// 不可变性测试
assertThat(record1).isEqualTo(record2);
assertThat(record1.hashCode()).isEqualTo(record2.hashCode());

// 状态验证
assertThat(entity.getStatus()).isEqualTo(expectedStatus);

// 集合验证
assertThat(list)
    .hasSize(3)
    .containsExactlyInAnyOrder(item1, item2, item3)
    .doesNotContain(item4);

// 异常验证
assertThatThrownBy(() -> method())
    .isInstanceOf(BusinessException.class)
    .hasMessageContaining("specific error")
    .hasFieldOrPropertyWithValue("code", "ERROR_001");

// 可选值验证
assertThat(optional).isPresent();
assertThat(optional).isEmpty();
assertThat(optional).hasValue(expectedValue);
```