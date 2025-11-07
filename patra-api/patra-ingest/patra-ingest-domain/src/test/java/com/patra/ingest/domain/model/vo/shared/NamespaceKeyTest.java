package com.patra.ingest.domain.model.vo.shared;

import static org.assertj.core.api.Assertions.*;

import com.patra.ingest.domain.model.enums.NamespaceScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link NamespaceKey} 单元测试。
 *
 * <p>测试策略: Record 值对象 - 纯单元测试,无 Mock。
 *
 * <p>测试覆盖:
 *
 * <ul>
 *   <li>✅ Record 语义 (equals, hashCode, toString)
 *   <li>✅ 不变性约束
 *   <li>✅ 工厂方法 (global)
 *   <li>✅ 边界条件
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("NamespaceKey 值对象单元测试")
class NamespaceKeyTest {

  @Nested
  @DisplayName("构造函数和不变性约束")
  class ConstructorAndInvariantsTests {

    @Test
    @DisplayName("应该成功创建有效的命名空间键")
    void shouldCreateValidNamespaceKey() {
      // Given
      NamespaceScope scope = NamespaceScope.EXPR;
      String key = "abc123def456";

      // When
      NamespaceKey namespaceKey = new NamespaceKey(scope, key);

      // Then
      assertThat(namespaceKey.scope()).isEqualTo(scope);
      assertThat(namespaceKey.key()).isEqualTo(key);
    }

    @Test
    @DisplayName("应该允许创建全局作用域的命名空间键")
    void shouldAllowGlobalScope() {
      // Given
      NamespaceScope scope = NamespaceScope.GLOBAL;
      String key = "0".repeat(64);

      // When
      NamespaceKey namespaceKey = new NamespaceKey(scope, key);

      // Then
      assertThat(namespaceKey.scope()).isEqualTo(NamespaceScope.GLOBAL);
      assertThat(namespaceKey.key()).isEqualTo(key);
    }

    @Test
    @DisplayName("应该允许创建表达式作用域的命名空间键")
    void shouldAllowExprScope() {
      // Given
      NamespaceScope scope = NamespaceScope.EXPR;
      String key = "expression-hash-123";

      // When
      NamespaceKey namespaceKey = new NamespaceKey(scope, key);

      // Then
      assertThat(namespaceKey.scope()).isEqualTo(NamespaceScope.EXPR);
      assertThat(namespaceKey.key()).isEqualTo(key);
    }

    @Test
    @DisplayName("应该允许创建自定义作用域的命名空间键")
    void shouldAllowCustomScope() {
      // Given
      NamespaceScope scope = NamespaceScope.CUSTOM;
      String key = "custom-namespace-456";

      // When
      NamespaceKey namespaceKey = new NamespaceKey(scope, key);

      // Then
      assertThat(namespaceKey.scope()).isEqualTo(NamespaceScope.CUSTOM);
      assertThat(namespaceKey.key()).isEqualTo(key);
    }

    @Test
    @DisplayName("应该允许 null scope 和 key")
    void shouldAllowNullScopeAndKey() {
      // Given & When
      NamespaceKey namespaceKey = new NamespaceKey(null, null);

      // Then
      assertThat(namespaceKey.scope()).isNull();
      assertThat(namespaceKey.key()).isNull();
    }

    @Test
    @DisplayName("应该允许空字符串作为 key")
    void shouldAllowEmptyStringAsKey() {
      // Given
      NamespaceScope scope = NamespaceScope.GLOBAL;
      String emptyKey = "";

      // When
      NamespaceKey namespaceKey = new NamespaceKey(scope, emptyKey);

      // Then
      assertThat(namespaceKey.key()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("相同属性的命名空间键应该相等")
    void shouldBeEqualWithSameProperties() {
      // Given
      NamespaceScope scope = NamespaceScope.EXPR;
      String key = "expression-hash-123";

      // When
      NamespaceKey namespaceKey1 = new NamespaceKey(scope, key);
      NamespaceKey namespaceKey2 = new NamespaceKey(scope, key);

      // Then
      assertThat(namespaceKey1)
          .isEqualTo(namespaceKey2)
          .hasSameHashCodeAs(namespaceKey2)
          .isNotSameAs(namespaceKey2);
    }

    @Test
    @DisplayName("不同作用域的命名空间键应该不相等")
    void shouldNotBeEqualWithDifferentScope() {
      // Given
      String key = "same-key";
      NamespaceKey globalKey = new NamespaceKey(NamespaceScope.GLOBAL, key);
      NamespaceKey exprKey = new NamespaceKey(NamespaceScope.EXPR, key);
      NamespaceKey customKey = new NamespaceKey(NamespaceScope.CUSTOM, key);

      // When & Then
      assertThat(globalKey).isNotEqualTo(exprKey).isNotEqualTo(customKey);
      assertThat(exprKey).isNotEqualTo(customKey);
    }

    @Test
    @DisplayName("不同 key 的命名空间键应该不相等")
    void shouldNotBeEqualWithDifferentKey() {
      // Given
      NamespaceScope scope = NamespaceScope.EXPR;
      NamespaceKey namespaceKey1 = new NamespaceKey(scope, "key1");
      NamespaceKey namespaceKey2 = new NamespaceKey(scope, "key2");

      // When & Then
      assertThat(namespaceKey1).isNotEqualTo(namespaceKey2);
    }

    @Test
    @DisplayName("null 属性的命名空间键应该与有值的不相等")
    void shouldNotBeEqualWithNullProperties() {
      // Given
      NamespaceKey withValues = new NamespaceKey(NamespaceScope.GLOBAL, "key");
      NamespaceKey withNullScope = new NamespaceKey(null, "key");
      NamespaceKey withNullKey = new NamespaceKey(NamespaceScope.GLOBAL, null);
      NamespaceKey withBothNull = new NamespaceKey(null, null);

      // When & Then
      assertThat(withValues)
          .isNotEqualTo(withNullScope)
          .isNotEqualTo(withNullKey)
          .isNotEqualTo(withBothNull);
      assertThat(withNullScope).isNotEqualTo(withNullKey);
    }

    @Test
    @DisplayName("toString 应该包含所有字段")
    void shouldIncludeAllFieldsInToString() {
      // Given
      NamespaceScope scope = NamespaceScope.EXPR;
      String key = "expression-hash-123";
      NamespaceKey namespaceKey = new NamespaceKey(scope, key);

      // When
      String result = namespaceKey.toString();

      // Then
      assertThat(result)
          .contains("NamespaceKey")
          .contains("scope=" + scope)
          .contains("key=" + key);
    }

    @Test
    @DisplayName("toString 应该正确处理 null 值")
    void shouldHandleNullInToString() {
      // Given
      NamespaceKey namespaceKey = new NamespaceKey(null, null);

      // When
      String result = namespaceKey.toString();

      // Then
      assertThat(result).contains("NamespaceKey").contains("null");
    }
  }

  @Nested
  @DisplayName("工厂方法测试")
  class FactoryMethodsTests {

    @Test
    @DisplayName("global() 应该创建全局命名空间键")
    void globalShouldCreateGlobalNamespaceKey() {
      // When
      NamespaceKey namespaceKey = NamespaceKey.global();

      // Then
      assertThat(namespaceKey.scope()).isEqualTo(NamespaceScope.GLOBAL);
      assertThat(namespaceKey.key()).isNotNull().hasSize(64).matches("0+");
    }

    @Test
    @DisplayName("global() 应该创建由 64 个零字符组成的 key")
    void globalShouldCreate64ZeroCharacters() {
      // When
      NamespaceKey namespaceKey = NamespaceKey.global();

      // Then
      String expectedKey = "0".repeat(64);
      assertThat(namespaceKey.key()).isEqualTo(expectedKey);
    }

    @Test
    @DisplayName("多次调用 global() 应该返回相等的对象")
    void multipleGlobalCallsShouldReturnEqualObjects() {
      // When
      NamespaceKey global1 = NamespaceKey.global();
      NamespaceKey global2 = NamespaceKey.global();

      // Then
      assertThat(global1)
          .isEqualTo(global2)
          .hasSameHashCodeAs(global2)
          .isNotSameAs(global2);
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionsTests {

    @Test
    @DisplayName("应该支持超长 key")
    void shouldSupportVeryLongKey() {
      // Given
      NamespaceScope scope = NamespaceScope.CUSTOM;
      String longKey = "a".repeat(1000);

      // When
      NamespaceKey namespaceKey = new NamespaceKey(scope, longKey);

      // Then
      assertThat(namespaceKey.key()).hasSize(1000);
    }

    @Test
    @DisplayName("应该支持包含特殊字符的 key")
    void shouldSupportSpecialCharactersInKey() {
      // Given
      NamespaceScope scope = NamespaceScope.EXPR;
      String specialKey = "key-with-special!@#$%^&*()_+={}[]|\\:;\"'<>,.?/~`";

      // When
      NamespaceKey namespaceKey = new NamespaceKey(scope, specialKey);

      // Then
      assertThat(namespaceKey.key()).isEqualTo(specialKey);
    }

    @Test
    @DisplayName("应该支持包含 Unicode 字符的 key")
    void shouldSupportUnicodeCharactersInKey() {
      // Given
      NamespaceScope scope = NamespaceScope.CUSTOM;
      String unicodeKey = "中文键值-🚀-emoji";

      // When
      NamespaceKey namespaceKey = new NamespaceKey(scope, unicodeKey);

      // Then
      assertThat(namespaceKey.key()).isEqualTo(unicodeKey);
    }

    @Test
    @DisplayName("应该支持包含空格的 key")
    void shouldSupportKeyWithSpaces() {
      // Given
      NamespaceScope scope = NamespaceScope.CUSTOM;
      String keyWithSpaces = "key with spaces";

      // When
      NamespaceKey namespaceKey = new NamespaceKey(scope, keyWithSpaces);

      // Then
      assertThat(namespaceKey.key()).isEqualTo(keyWithSpaces);
    }

    @Test
    @DisplayName("应该支持单字符 key")
    void shouldSupportSingleCharacterKey() {
      // Given
      NamespaceScope scope = NamespaceScope.EXPR;
      String singleCharKey = "x";

      // When
      NamespaceKey namespaceKey = new NamespaceKey(scope, singleCharKey);

      // Then
      assertThat(namespaceKey.key()).isEqualTo(singleCharKey).hasSize(1);
    }
  }

  @Nested
  @DisplayName("实际使用场景测试")
  class RealWorldUseCaseTests {

    @Test
    @DisplayName("应该支持全局命名空间的使用场景")
    void shouldSupportGlobalNamespaceUseCase() {
      // Given - 全局命名空间,所有数据源共享
      NamespaceKey globalKey = NamespaceKey.global();

      // When & Then
      assertThat(globalKey.scope()).isEqualTo(NamespaceScope.GLOBAL);
      assertThat(globalKey.key()).hasSize(64);
    }

    @Test
    @DisplayName("应该支持表达式哈希命名空间的使用场景")
    void shouldSupportExpressionHashNamespaceUseCase() {
      // Given - 基于表达式签名的命名空间隔离
      String expressionHash = "sha256-abc123def456";
      NamespaceKey exprKey = new NamespaceKey(NamespaceScope.EXPR, expressionHash);

      // When & Then
      assertThat(exprKey.scope()).isEqualTo(NamespaceScope.EXPR);
      assertThat(exprKey.key()).isEqualTo(expressionHash);
    }

    @Test
    @DisplayName("应该支持自定义命名空间的使用场景")
    void shouldSupportCustomNamespaceUseCase() {
      // Given - 用户自定义的命名空间
      String customId = "tenant-12345";
      NamespaceKey customKey = new NamespaceKey(NamespaceScope.CUSTOM, customId);

      // When & Then
      assertThat(customKey.scope()).isEqualTo(NamespaceScope.CUSTOM);
      assertThat(customKey.key()).isEqualTo(customId);
    }

    @Test
    @DisplayName("应该支持多租户隔离场景")
    void shouldSupportMultiTenantIsolation() {
      // Given - 不同租户的命名空间键
      NamespaceKey tenant1 =
          new NamespaceKey(NamespaceScope.CUSTOM, "tenant-001");
      NamespaceKey tenant2 =
          new NamespaceKey(NamespaceScope.CUSTOM, "tenant-002");

      // When & Then - 不同租户的键应该不相等
      assertThat(tenant1).isNotEqualTo(tenant2);
      assertThat(tenant1.scope()).isEqualTo(tenant2.scope());
      assertThat(tenant1.key()).isNotEqualTo(tenant2.key());
    }

    @Test
    @DisplayName("应该支持作为 Map 键使用")
    void shouldSupportUseAsMapKey() {
      // Given
      NamespaceKey key1 = new NamespaceKey(NamespaceScope.EXPR, "expr-1");
      NamespaceKey key2 = new NamespaceKey(NamespaceScope.EXPR, "expr-2");
      NamespaceKey key3 = new NamespaceKey(NamespaceScope.EXPR, "expr-1"); // 与 key1 相同

      java.util.Map<NamespaceKey, String> map = new java.util.HashMap<>();
      map.put(key1, "value1");
      map.put(key2, "value2");

      // When & Then - 相同的键应该覆盖
      assertThat(map).hasSize(2);
      assertThat(map.get(key3)).isEqualTo("value1"); // key3 与 key1 相等
      assertThat(map.containsKey(key1)).isTrue();
      assertThat(map.containsKey(key2)).isTrue();
    }
  }
}
