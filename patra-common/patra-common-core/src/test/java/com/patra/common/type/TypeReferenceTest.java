package com.patra.common.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * TypeReference 单元测试
 *
 * <p>测试策略：</p>
 * <ul>
 *   <li>泛型类型捕获测试（简单类型、参数化类型、嵌套泛型）</li>
 *   <li>类型提取测试（getRawType、getType）</li>
 *   <li>类型兼容性检查测试</li>
 *   <li>equals 和 hashCode 测试</li>
 *   <li>错误处理测试（未使用匿名内部类）</li>
 *   <li>边界条件测试</li>
 * </ul>
 *
 * @since 0.1.0
 */
@DisplayName("TypeReference 单元测试")
class TypeReferenceTest {

    // ========== 测试数据类 ==========

    /**
     * 简单测试实体
     */
    static class TestEntity {
        private Long id;
        private String name;

        public TestEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /**
     * 继承测试实体
     */
    static class ExtendedTestEntity extends TestEntity {
        public ExtendedTestEntity(Long id, String name) {
            super(id, name);
        }
    }

    // ========== 泛型类型捕获测试 ==========

    @Nested
    @DisplayName("泛型类型捕获测试")
    class GenericTypeCaptureTest {

        @Test
        @DisplayName("应该正确捕获简单泛型类型")
        void should_capture_simple_generic_type() {
            // given
            TypeReference<String> typeRef = new TypeReference<>() {};

            // when
            Class<?> rawType = typeRef.getRawType();
            Type type = typeRef.getType();

            // then
            assertThat(rawType).isEqualTo(String.class);
            assertThat(type).isEqualTo(String.class);
        }

        @Test
        @DisplayName("应该正确捕获参数化泛型类型 - List<T>")
        void should_capture_parameterized_generic_type_list() {
            // given
            TypeReference<List<String>> typeRef = new TypeReference<>() {};

            // when
            Class<?> rawType = typeRef.getRawType();
            Type type = typeRef.getType();

            // then
            assertThat(rawType).isEqualTo(List.class);
            assertThat(type).isInstanceOf(ParameterizedType.class);

            ParameterizedType paramType = (ParameterizedType) type;
            assertThat(paramType.getRawType()).isEqualTo(List.class);
            assertThat(paramType.getActualTypeArguments()).hasSize(1);
            assertThat(paramType.getActualTypeArguments()[0]).isEqualTo(String.class);
        }

        @Test
        @DisplayName("应该正确捕获嵌套泛型类型 - Map<K, V>")
        void should_capture_nested_generic_type_map() {
            // given
            TypeReference<Map<String, List<Integer>>> typeRef = new TypeReference<>() {};

            // when
            Class<?> rawType = typeRef.getRawType();
            Type type = typeRef.getType();

            // then
            assertThat(rawType).isEqualTo(Map.class);
            assertThat(type).isInstanceOf(ParameterizedType.class);

            ParameterizedType paramType = (ParameterizedType) type;
            Type[] actualTypeArgs = paramType.getActualTypeArguments();
            assertThat(actualTypeArgs).hasSize(2);

            // 验证第一个类型参数：String
            assertThat(actualTypeArgs[0]).isEqualTo(String.class);

            // 验证第二个类型参数：List<Integer>
            assertThat(actualTypeArgs[1]).isInstanceOf(ParameterizedType.class);
            ParameterizedType listType = (ParameterizedType) actualTypeArgs[1];
            assertThat(listType.getRawType()).isEqualTo(List.class);
            assertThat(listType.getActualTypeArguments()[0]).isEqualTo(Integer.class);
        }

        @Test
        @DisplayName("应该支持自定义测试类")
        void should_support_custom_test_class() {
            // given
            TypeReference<TestEntity> typeRef = new TypeReference<>() {};

            // when
            Class<?> rawType = typeRef.getRawType();

            // then
            assertThat(rawType).isEqualTo(TestEntity.class);
        }
    }

    // ========== 类型提取测试 ==========

    @Nested
    @DisplayName("类型提取测试")
    class TypeExtractionTest {

        @Test
        @DisplayName("getRawType() 应该提取原始类型（简单类型）")
        void should_extract_raw_type_for_simple_type() {
            // given
            TypeReference<String> typeRef = new TypeReference<>() {};

            // when
            Class<?> rawType = typeRef.getRawType();

            // then
            assertThat(rawType).isEqualTo(String.class);
        }

        @Test
        @DisplayName("getRawType() 应该提取原始类型（参数化类型）")
        void should_extract_raw_type_for_parameterized_type() {
            // given
            TypeReference<List<String>> typeRef = new TypeReference<>() {};

            // when
            Class<?> rawType = typeRef.getRawType();

            // then
            assertThat(rawType).isEqualTo(List.class);
        }

        @Test
        @DisplayName("getType() 应该返回完整类型信息（包含泛型参数）")
        void should_return_full_type_info() {
            // given
            TypeReference<List<String>> typeRef = new TypeReference<>() {};

            // when
            Type type = typeRef.getType();

            // then
            assertThat(type).isInstanceOf(ParameterizedType.class);
            assertThat(type.getTypeName()).contains("java.util.List");
            assertThat(type.getTypeName()).contains("String");
        }
    }

    // ========== 类型兼容性检查测试 ==========

    @Nested
    @DisplayName("类型兼容性检查测试")
    class TypeCompatibilityTest {

        @Test
        @DisplayName("isAssignableFrom() - 应该支持相同类型")
        void should_check_assignable_for_same_type() {
            // given
            TypeReference<String> typeRef = new TypeReference<>() {};

            // when & then
            assertThat(typeRef.isAssignableFrom(String.class)).isTrue();
        }

        @Test
        @DisplayName("isAssignableFrom() - 应该支持子类")
        void should_check_assignable_for_subclass() {
            // given
            TypeReference<TestEntity> typeRef = new TypeReference<>() {};

            // when & then
            assertThat(typeRef.isAssignableFrom(ExtendedTestEntity.class)).isTrue();
        }

        @Test
        @DisplayName("isAssignableFrom() - 应该拒绝不兼容类型")
        void should_reject_incompatible_type() {
            // given
            TypeReference<String> typeRef = new TypeReference<>() {};

            // when & then
            assertThat(typeRef.isAssignableFrom(Integer.class)).isFalse();
            assertThat(typeRef.isAssignableFrom(TestEntity.class)).isFalse();
        }

        @Test
        @DisplayName("isAssignableFrom() - 应该抛出异常当类为 null")
        void should_throw_exception_when_class_is_null() {
            // given
            TypeReference<String> typeRef = new TypeReference<>() {};

            // when & then
            assertThatThrownBy(() -> typeRef.isAssignableFrom(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("类不能为空");
        }
    }

    // ========== equals 和 hashCode 测试 ==========

    @Nested
    @DisplayName("equals 和 hashCode 测试")
    class EqualsAndHashCodeTest {

        @Test
        @DisplayName("相同类型的 TypeReference 应该相等")
        void should_be_equal_for_same_type() {
            // given
            TypeReference<String> ref1 = new TypeReference<>() {};
            TypeReference<String> ref2 = new TypeReference<>() {};

            // when & then
            assertThat(ref1).isEqualTo(ref2);
            assertThat(ref1.hashCode()).isEqualTo(ref2.hashCode());
        }

        @Test
        @DisplayName("不同类型的 TypeReference 不应该相等")
        void should_not_be_equal_for_different_type() {
            // given
            TypeReference<String> ref1 = new TypeReference<>() {};
            TypeReference<TestEntity> ref2 = new TypeReference<>() {};

            // when & then
            assertThat(ref1).isNotEqualTo(ref2);
        }

        @Test
        @DisplayName("相同参数化类型应该相等")
        void should_be_equal_for_same_parameterized_type() {
            // given
            TypeReference<List<String>> ref1 = new TypeReference<>() {};
            TypeReference<List<String>> ref2 = new TypeReference<>() {};

            // when & then
            assertThat(ref1).isEqualTo(ref2);
            assertThat(ref1.hashCode()).isEqualTo(ref2.hashCode());
        }

        @Test
        @DisplayName("不同参数化类型不应该相等")
        void should_not_be_equal_for_different_parameterized_type() {
            // given
            TypeReference<List<String>> ref1 = new TypeReference<>() {};
            TypeReference<List<TestEntity>> ref2 = new TypeReference<>() {};

            // when & then
            assertThat(ref1).isNotEqualTo(ref2);
        }

        @Test
        @DisplayName("equals 应该满足自反性")
        void equals_should_satisfy_reflexive() {
            // given
            TypeReference<String> ref = new TypeReference<>() {};

            // when & then
            assertThat(ref).isEqualTo(ref);
        }

        @Test
        @DisplayName("equals 应该处理 null")
        void equals_should_handle_null() {
            // given
            TypeReference<String> ref = new TypeReference<>() {};

            // when & then
            assertThat(ref).isNotEqualTo(null);
        }

        @Test
        @DisplayName("equals 应该处理不同类型的对象")
        void equals_should_handle_different_class() {
            // given
            TypeReference<String> ref = new TypeReference<>() {};
            String other = "not a TypeReference";

            // when & then
            assertThat(ref).isNotEqualTo(other);
        }
    }

    // ========== 错误处理测试 ==========

    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTest {

        @Test
        @DisplayName("应该抛出异常当未提供泛型参数")
        void should_throw_exception_when_no_generic_parameter() {
            // when & then
            assertThatThrownBy(() -> {
                // 尝试直接实例化（不使用匿名内部类）
                // 注意：这个测试可能需要特殊处理，因为抽象类不能直接实例化
                // 我们通过反射创建一个没有泛型参数的子类来模拟
                @SuppressWarnings("rawtypes")
                class RawTypeReference extends TypeReference {
                    // 这个类的泛型超类是 TypeReference（没有类型参数）
                }
                new RawTypeReference();
            })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须使用匿名内部类创建");
        }
    }

    // ========== 边界条件测试 ==========

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTest {

        @Test
        @DisplayName("toString() 应该返回清晰的描述")
        void should_return_clear_description_in_toString() {
            // given
            TypeReference<String> typeRef = new TypeReference<>() {};

            // when
            String toString = typeRef.toString();

            // then
            assertThat(toString).contains("TypeReference");
            assertThat(toString).contains("String");
        }

        @Test
        @DisplayName("toString() 应该包含完整泛型信息")
        void should_include_full_generic_info_in_toString() {
            // given
            TypeReference<List<String>> typeRef = new TypeReference<>() {};

            // when
            String toString = typeRef.toString();

            // then
            assertThat(toString).contains("TypeReference");
            assertThat(toString).contains("List");
        }

        @Test
        @DisplayName("应该支持原始类型（Primitive Type 的包装类）")
        void should_support_primitive_wrapper_types() {
            // given
            TypeReference<Integer> typeRef = new TypeReference<>() {};

            // when
            Class<?> rawType = typeRef.getRawType();

            // then
            assertThat(rawType).isEqualTo(Integer.class);
        }

        @Test
        @DisplayName("应该支持 String 类型")
        void should_support_string_type() {
            // given
            TypeReference<String> typeRef = new TypeReference<>() {};

            // when
            Class<?> rawType = typeRef.getRawType();

            // then
            assertThat(rawType).isEqualTo(String.class);
        }
    }
}
