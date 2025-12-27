package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueLanguages 值对象单元测试。
///
/// @author linqibin
/// @since 0.7.0
@DisplayName("VenueLanguages 值对象")
@Timeout(2)
class VenueLanguagesTest {

  @Nested
  @DisplayName("静态工厂方法")
  class FactoryMethodTests {

    @Test
    @DisplayName("of() 应创建包含主语言和摘要语言的实例")
    void ofShouldCreateInstanceWithBothLanguages() {
      VenueLanguages languages = VenueLanguages.of(List.of("eng", "chi"), List.of("fre", "ger"));

      assertThat(languages.primary()).containsExactly("eng", "chi");
      assertThat(languages.summary()).containsExactly("fre", "ger");
    }

    @Test
    @DisplayName("of() 应处理 null 参数")
    void ofShouldHandleNullParameters() {
      VenueLanguages languages = VenueLanguages.of(null, null);

      assertThat(languages.primary()).isEmpty();
      assertThat(languages.summary()).isEmpty();
    }

    @Test
    @DisplayName("ofPrimary() 应创建仅包含主语言的实例")
    void ofPrimaryShouldCreateInstanceWithOnlyPrimaryLanguages() {
      VenueLanguages languages = VenueLanguages.ofPrimary(List.of("eng", "chi"));

      assertThat(languages.primary()).containsExactly("eng", "chi");
      assertThat(languages.summary()).isEmpty();
    }

    @Test
    @DisplayName("ofSingleLanguage() 应创建单一主语言的实例")
    void ofSingleLanguageShouldCreateInstanceWithSinglePrimary() {
      VenueLanguages languages = VenueLanguages.ofSingleLanguage("eng");

      assertThat(languages.primary()).containsExactly("eng");
      assertThat(languages.summary()).isEmpty();
    }

    @Test
    @DisplayName("empty() 应创建空实例")
    void emptyShouldCreateEmptyInstance() {
      VenueLanguages languages = VenueLanguages.empty();

      assertThat(languages.primary()).isEmpty();
      assertThat(languages.summary()).isEmpty();
      assertThat(languages.isEmpty()).isTrue();
    }
  }

  @Nested
  @DisplayName("不可变性")
  class ImmutabilityTests {

    @Test
    @DisplayName("构造函数应创建列表的防御性副本")
    void constructorShouldCreateDefensiveCopy() {
      List<String> primary = new ArrayList<>(List.of("eng"));
      List<String> summary = new ArrayList<>(List.of("fre"));

      VenueLanguages languages = VenueLanguages.of(primary, summary);

      // 修改原始列表
      primary.add("chi");
      summary.add("ger");

      // 值对象应不受影响
      assertThat(languages.primary()).containsExactly("eng");
      assertThat(languages.summary()).containsExactly("fre");
    }

    @Test
    @DisplayName("返回的列表应不可修改")
    void returnedListsShouldBeUnmodifiable() {
      VenueLanguages languages = VenueLanguages.of(List.of("eng"), List.of("fre"));

      org.assertj.core.api.Assertions.assertThatThrownBy(() -> languages.primary().add("chi"))
          .isInstanceOf(UnsupportedOperationException.class);

      org.assertj.core.api.Assertions.assertThatThrownBy(() -> languages.summary().add("ger"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("主语言查询")
  class PrimaryLanguageQueryTests {

    @Test
    @DisplayName("getMainLanguage() 应返回第一个主语言")
    void getMainLanguageShouldReturnFirstPrimary() {
      VenueLanguages languages = VenueLanguages.of(List.of("eng", "chi"), List.of());

      assertThat(languages.getMainLanguage()).isEqualTo("eng");
    }

    @Test
    @DisplayName("getMainLanguage() 无主语言时应返回 null")
    void getMainLanguageShouldReturnNullWhenEmpty() {
      VenueLanguages languages = VenueLanguages.empty();

      assertThat(languages.getMainLanguage()).isNull();
    }

    @Test
    @DisplayName("hasPrimaryLanguage() 应正确判断主语言是否存在")
    void hasPrimaryLanguageShouldCheckCorrectly() {
      VenueLanguages languages = VenueLanguages.of(List.of("eng", "chi"), List.of());

      assertThat(languages.hasPrimaryLanguage("eng")).isTrue();
      assertThat(languages.hasPrimaryLanguage("chi")).isTrue();
      assertThat(languages.hasPrimaryLanguage("fre")).isFalse();
    }

    @Test
    @DisplayName("hasPrimaryLanguages() 应判断是否有主语言")
    void hasPrimaryLanguagesShouldCheckIfNotEmpty() {
      assertThat(VenueLanguages.of(List.of("eng"), List.of()).hasPrimaryLanguages()).isTrue();
      assertThat(VenueLanguages.empty().hasPrimaryLanguages()).isFalse();
    }
  }

  @Nested
  @DisplayName("摘要语言查询")
  class SummaryLanguageQueryTests {

    @Test
    @DisplayName("hasSummaryLanguage() 应正确判断摘要语言是否存在")
    void hasSummaryLanguageShouldCheckCorrectly() {
      VenueLanguages languages = VenueLanguages.of(List.of("eng"), List.of("fre", "ger"));

      assertThat(languages.hasSummaryLanguage("fre")).isTrue();
      assertThat(languages.hasSummaryLanguage("ger")).isTrue();
      assertThat(languages.hasSummaryLanguage("eng")).isFalse();
    }

    @Test
    @DisplayName("hasSummaryLanguages() 应判断是否有摘要语言")
    void hasSummaryLanguagesShouldCheckIfNotEmpty() {
      assertThat(VenueLanguages.of(List.of("eng"), List.of("fre")).hasSummaryLanguages()).isTrue();
      assertThat(VenueLanguages.ofPrimary(List.of("eng")).hasSummaryLanguages()).isFalse();
    }
  }

  @Nested
  @DisplayName("语言类型判断")
  class LanguageTypeTests {

    @Test
    @DisplayName("isEnglish() 应正确判断英语期刊")
    void isEnglishShouldCheckForEngCode() {
      assertThat(VenueLanguages.ofSingleLanguage("eng").isEnglish()).isTrue();
      assertThat(VenueLanguages.of(List.of("chi", "eng"), List.of()).isEnglish()).isTrue();
      assertThat(VenueLanguages.ofSingleLanguage("chi").isEnglish()).isFalse();
    }

    @Test
    @DisplayName("isChinese() 应正确判断中文期刊（支持 chi 和 zho 代码）")
    void isChineseShouldCheckForBothCodes() {
      assertThat(VenueLanguages.ofSingleLanguage("chi").isChinese()).isTrue();
      assertThat(VenueLanguages.ofSingleLanguage("zho").isChinese()).isTrue();
      assertThat(VenueLanguages.of(List.of("eng", "chi"), List.of()).isChinese()).isTrue();
      assertThat(VenueLanguages.ofSingleLanguage("eng").isChinese()).isFalse();
    }

    @Test
    @DisplayName("isEmpty() 应正确判断空状态")
    void isEmptyShouldCheckBothLists() {
      assertThat(VenueLanguages.empty().isEmpty()).isTrue();
      assertThat(VenueLanguages.of(List.of(), List.of()).isEmpty()).isTrue();
      assertThat(VenueLanguages.ofSingleLanguage("eng").isEmpty()).isFalse();
      assertThat(VenueLanguages.of(List.of(), List.of("fre")).isEmpty()).isFalse();
    }
  }

  @Nested
  @DisplayName("getAllLanguages()")
  class GetAllLanguagesTests {

    @Test
    @DisplayName("应返回所有语言（去重）")
    void shouldReturnAllLanguagesWithoutDuplicates() {
      VenueLanguages languages = VenueLanguages.of(List.of("eng", "chi"), List.of("chi", "fre"));

      List<String> all = languages.getAllLanguages();

      assertThat(all).containsExactly("eng", "chi", "fre");
    }

    @Test
    @DisplayName("空实例应返回空列表")
    void emptyInstanceShouldReturnEmptyList() {
      assertThat(VenueLanguages.empty().getAllLanguages()).isEmpty();
    }

    @Test
    @DisplayName("返回的列表应不可修改")
    void returnedListShouldBeUnmodifiable() {
      VenueLanguages languages = VenueLanguages.of(List.of("eng"), List.of("fre"));

      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> languages.getAllLanguages().add("ger"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("with* 不可变转换方法")
  class WithMethodTests {

    @Test
    @DisplayName("withPrimaryLanguage() 应返回包含新主语言的新实例")
    void withPrimaryLanguageShouldReturnNewInstanceWithAddedLanguage() {
      VenueLanguages original = VenueLanguages.ofSingleLanguage("eng");

      VenueLanguages modified = original.withPrimaryLanguage("chi");

      assertThat(modified).isNotSameAs(original);
      assertThat(modified.primary()).containsExactly("eng", "chi");
      assertThat(original.primary()).containsExactly("eng"); // 原对象不变
    }

    @Test
    @DisplayName("withPrimaryLanguage() 语言已存在时应返回相同实例")
    void withPrimaryLanguageShouldReturnSameInstanceIfAlreadyExists() {
      VenueLanguages original = VenueLanguages.ofSingleLanguage("eng");

      VenueLanguages result = original.withPrimaryLanguage("eng");

      assertThat(result).isSameAs(original);
    }

    @Test
    @DisplayName("withSummaryLanguage() 应返回包含新摘要语言的新实例")
    void withSummaryLanguageShouldReturnNewInstanceWithAddedLanguage() {
      VenueLanguages original = VenueLanguages.of(List.of("eng"), List.of("fre"));

      VenueLanguages modified = original.withSummaryLanguage("ger");

      assertThat(modified).isNotSameAs(original);
      assertThat(modified.summary()).containsExactly("fre", "ger");
      assertThat(original.summary()).containsExactly("fre"); // 原对象不变
    }

    @Test
    @DisplayName("withSummaryLanguage() 语言已存在时应返回相同实例")
    void withSummaryLanguageShouldReturnSameInstanceIfAlreadyExists() {
      VenueLanguages original = VenueLanguages.of(List.of("eng"), List.of("fre"));

      VenueLanguages result = original.withSummaryLanguage("fre");

      assertThat(result).isSameAs(original);
    }
  }

  @Nested
  @DisplayName("equals 和 hashCode")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("相同内容的实例应相等")
    void instancesWithSameContentShouldBeEqual() {
      VenueLanguages lang1 = VenueLanguages.of(List.of("eng"), List.of("fre"));
      VenueLanguages lang2 = VenueLanguages.of(List.of("eng"), List.of("fre"));

      assertThat(lang1).isEqualTo(lang2);
      assertThat(lang1.hashCode()).isEqualTo(lang2.hashCode());
    }

    @Test
    @DisplayName("不同内容的实例应不相等")
    void instancesWithDifferentContentShouldNotBeEqual() {
      VenueLanguages lang1 = VenueLanguages.of(List.of("eng"), List.of("fre"));
      VenueLanguages lang2 = VenueLanguages.of(List.of("chi"), List.of("fre"));

      assertThat(lang1).isNotEqualTo(lang2);
    }
  }
}
