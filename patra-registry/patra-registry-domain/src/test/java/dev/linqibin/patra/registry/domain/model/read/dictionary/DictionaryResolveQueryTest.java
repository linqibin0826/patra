package dev.linqibin.patra.registry.domain.model.read.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// DictionaryResolveQuery 查询视图单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("DictionaryResolveQuery 查询视图")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class DictionaryResolveQueryTest {

  @Nested
  @DisplayName("构造函数验证")
  class ConstructorTests {

    @Test
    @DisplayName("应能正常构造有效的查询视图")
    void shouldConstructValidQuery() {
      List<DictionaryResolveItemQuery> items =
          List.of(
              new DictionaryResolveItemQuery("CN", "CN", "中国", DictionaryResolveStatus.RESOLVED));

      DictionaryResolveQuery query =
          new DictionaryResolveQuery("country", "ISO_3166_1_ALPHA2", items);

      assertThat(query.typeCode()).isEqualTo("country");
      assertThat(query.sourceStandard()).isEqualTo("ISO_3166_1_ALPHA2");
      assertThat(query.items()).hasSize(1);
    }

    @Test
    @DisplayName("空列表应正常构造")
    void shouldAllowEmptyItems() {
      DictionaryResolveQuery query =
          new DictionaryResolveQuery("country", "ISO_3166_1_ALPHA2", List.of());

      assertThat(query.items()).isEmpty();
    }

    @Test
    @DisplayName("typeCode 应自动修剪空白")
    void shouldTrimTypeCode() {
      DictionaryResolveQuery query =
          new DictionaryResolveQuery("  country  ", "ISO_3166_1_ALPHA2", List.of());

      assertThat(query.typeCode()).isEqualTo("country");
    }

    @Test
    @DisplayName("sourceStandard 应自动修剪空白")
    void shouldTrimSourceStandard() {
      DictionaryResolveQuery query =
          new DictionaryResolveQuery("country", "  ISO_3166_1_ALPHA2  ", List.of());

      assertThat(query.sourceStandard()).isEqualTo("ISO_3166_1_ALPHA2");
    }
  }

  @Nested
  @DisplayName("边界条件验证")
  class ValidationTests {

    @Test
    @DisplayName("typeCode 为 null 时应抛出异常")
    void shouldThrowWhenTypeCodeIsNull() {
      assertThatThrownBy(() -> new DictionaryResolveQuery(null, "ISO_3166_1_ALPHA2", List.of()))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary type code");
    }

    @Test
    @DisplayName("typeCode 为空白时应抛出异常")
    void shouldThrowWhenTypeCodeIsBlank() {
      assertThatThrownBy(() -> new DictionaryResolveQuery("   ", "ISO_3166_1_ALPHA2", List.of()))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary type code");
    }

    @Test
    @DisplayName("sourceStandard 为 null 时应抛出异常")
    void shouldThrowWhenSourceStandardIsNull() {
      assertThatThrownBy(() -> new DictionaryResolveQuery("country", null, List.of()))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary source standard");
    }

    @Test
    @DisplayName("sourceStandard 为空白时应抛出异常")
    void shouldThrowWhenSourceStandardIsBlank() {
      assertThatThrownBy(() -> new DictionaryResolveQuery("country", "   ", List.of()))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary source standard");
    }

    @Test
    @DisplayName("items 为 null 时应抛出异常")
    void shouldThrowWhenItemsIsNull() {
      assertThatThrownBy(() -> new DictionaryResolveQuery("country", "ISO_3166_1_ALPHA2", null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary resolve items");
    }
  }

  @Nested
  @DisplayName("不可变性")
  class ImmutabilityTests {

    @Test
    @DisplayName("items 列表应是不可变的副本")
    void shouldReturnImmutableCopyOfItems() {
      List<DictionaryResolveItemQuery> mutableList = new ArrayList<>();
      mutableList.add(
          new DictionaryResolveItemQuery("CN", "CN", "中国", DictionaryResolveStatus.RESOLVED));

      DictionaryResolveQuery query =
          new DictionaryResolveQuery("country", "ISO_3166_1_ALPHA2", mutableList);

      // 修改原列表不应影响查询视图
      mutableList.add(
          new DictionaryResolveItemQuery("US", "US", "美国", DictionaryResolveStatus.RESOLVED));

      assertThat(query.items()).hasSize(1);
    }

    @Test
    @DisplayName("返回的 items 列表不可修改")
    void shouldNotAllowModificationOfItems() {
      DictionaryResolveQuery query =
          new DictionaryResolveQuery("country", "ISO_3166_1_ALPHA2", List.of());

      assertThatThrownBy(
              () ->
                  query
                      .items()
                      .add(
                          new DictionaryResolveItemQuery(
                              "CN", "CN", "中国", DictionaryResolveStatus.RESOLVED)))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("Record 特性")
  class RecordTests {

    @Test
    @DisplayName("相同值的对象应相等")
    void shouldBeEqualForSameValues() {
      DictionaryResolveQuery query1 =
          new DictionaryResolveQuery("country", "ISO_3166_1_ALPHA2", List.of());
      DictionaryResolveQuery query2 =
          new DictionaryResolveQuery("country", "ISO_3166_1_ALPHA2", List.of());

      assertThat(query1).isEqualTo(query2);
      assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
    }

    @Test
    @DisplayName("不同值的对象应不相等")
    void shouldNotBeEqualForDifferentValues() {
      DictionaryResolveQuery query1 =
          new DictionaryResolveQuery("country", "ISO_3166_1_ALPHA2", List.of());
      DictionaryResolveQuery query2 =
          new DictionaryResolveQuery("language", "ISO_639_1", List.of());

      assertThat(query1).isNotEqualTo(query2);
    }
  }
}
