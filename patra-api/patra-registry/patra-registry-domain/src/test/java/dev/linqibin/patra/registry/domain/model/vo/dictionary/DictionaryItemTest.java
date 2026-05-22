package dev.linqibin.patra.registry.domain.model.vo.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// DictionaryItem 值对象单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("DictionaryItem 值对象")
class DictionaryItemTest {

  @Nested
  @DisplayName("构造函数验证")
  class ConstructorTests {

    @Test
    @DisplayName("应能正常构造有效的字典项")
    void shouldConstructValidItem() {
      DictionaryItem item = new DictionaryItem(1L, 100L, "CN", "中国", true);

      assertThat(item.id()).isEqualTo(1L);
      assertThat(item.typeId()).isEqualTo(100L);
      assertThat(item.itemCode()).isEqualTo("CN");
      assertThat(item.itemName()).isEqualTo("中国");
      assertThat(item.enabled()).isTrue();
    }

    @Test
    @DisplayName("itemName 为 null 时应正常构造")
    void shouldAllowNullItemName() {
      DictionaryItem item = new DictionaryItem(1L, 100L, "CN", null, true);

      assertThat(item.itemName()).isNull();
    }

    @Test
    @DisplayName("itemCode 应自动修剪空白")
    void shouldTrimItemCode() {
      DictionaryItem item = new DictionaryItem(1L, 100L, "  CN  ", "中国", true);

      assertThat(item.itemCode()).isEqualTo("CN");
    }

    @Test
    @DisplayName("itemName 应自动修剪空白")
    void shouldTrimItemName() {
      DictionaryItem item = new DictionaryItem(1L, 100L, "CN", "  中国  ", true);

      assertThat(item.itemName()).isEqualTo("中国");
    }
  }

  @Nested
  @DisplayName("边界条件验证")
  class ValidationTests {

    @Test
    @DisplayName("id 为 null 时应抛出异常")
    void shouldThrowWhenIdIsNull() {
      assertThatThrownBy(() -> new DictionaryItem(null, 100L, "CN", "中国", true))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary item id");
    }

    @Test
    @DisplayName("id 为 0 时应抛出异常")
    void shouldThrowWhenIdIsZero() {
      assertThatThrownBy(() -> new DictionaryItem(0L, 100L, "CN", "中国", true))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary item id");
    }

    @Test
    @DisplayName("id 为负数时应抛出异常")
    void shouldThrowWhenIdIsNegative() {
      assertThatThrownBy(() -> new DictionaryItem(-1L, 100L, "CN", "中国", true))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary item id");
    }

    @Test
    @DisplayName("typeId 为 null 时应抛出异常")
    void shouldThrowWhenTypeIdIsNull() {
      assertThatThrownBy(() -> new DictionaryItem(1L, null, "CN", "中国", true))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary type id");
    }

    @Test
    @DisplayName("itemCode 为 null 时应抛出异常")
    void shouldThrowWhenItemCodeIsNull() {
      assertThatThrownBy(() -> new DictionaryItem(1L, 100L, null, "中国", true))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary item code");
    }

    @Test
    @DisplayName("itemCode 为空白时应抛出异常")
    void shouldThrowWhenItemCodeIsBlank() {
      assertThatThrownBy(() -> new DictionaryItem(1L, 100L, "   ", "中国", true))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary item code");
    }
  }

  @Nested
  @DisplayName("Record 特性")
  class RecordTests {

    @Test
    @DisplayName("相同值的对象应相等")
    void shouldBeEqualForSameValues() {
      DictionaryItem item1 = new DictionaryItem(1L, 100L, "CN", "中国", true);
      DictionaryItem item2 = new DictionaryItem(1L, 100L, "CN", "中国", true);

      assertThat(item1).isEqualTo(item2);
      assertThat(item1.hashCode()).isEqualTo(item2.hashCode());
    }

    @Test
    @DisplayName("不同值的对象应不相等")
    void shouldNotBeEqualForDifferentValues() {
      DictionaryItem item1 = new DictionaryItem(1L, 100L, "CN", "中国", true);
      DictionaryItem item2 = new DictionaryItem(2L, 100L, "US", "美国", true);

      assertThat(item1).isNotEqualTo(item2);
    }
  }
}
