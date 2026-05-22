package dev.linqibin.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// 出版历史值对象单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationHistory 出版历史值对象")
class PublicationHistoryTest {

  @Nested
  @DisplayName("工厂方法")
  class FactoryMethodTests {

    @Test
    @DisplayName("of() 应创建完整的出版历史")
    void shouldCreateWithAllFields() {
      // When
      PublicationHistory history = PublicationHistory.of(1990, 2020, true);

      // Then
      assertThat(history.startYear()).isEqualTo(1990);
      assertThat(history.endYear()).isEqualTo(2020);
      assertThat(history.ceased()).isTrue();
    }

    @Test
    @DisplayName("active() 应创建活跃出版的历史")
    void shouldCreateActiveHistory() {
      // When
      PublicationHistory history = PublicationHistory.active(1990);

      // Then
      assertThat(history.startYear()).isEqualTo(1990);
      assertThat(history.endYear()).isNull();
      assertThat(history.ceased()).isFalse();
    }

    @Test
    @DisplayName("ceased() 应创建已停刊的历史")
    void shouldCreateCeasedHistory() {
      // When
      PublicationHistory history = PublicationHistory.ceased(1950, 2010);

      // Then
      assertThat(history.startYear()).isEqualTo(1950);
      assertThat(history.endYear()).isEqualTo(2010);
      assertThat(history.ceased()).isTrue();
    }
  }

  @Nested
  @DisplayName("业务规则验证")
  class ValidationTests {

    @Test
    @DisplayName("停刊年份早于创刊年份应抛出异常")
    void shouldThrowWhenEndYearBeforeStartYear() {
      assertThatThrownBy(() -> PublicationHistory.of(2000, 1990, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("停刊年份")
          .hasMessageContaining("不能早于创刊年份");
    }

    @Test
    @DisplayName("停刊年份等于创刊年份应允许")
    void shouldAllowSameStartAndEndYear() {
      PublicationHistory history = PublicationHistory.of(2000, 2000, true);
      assertThat(history.startYear()).isEqualTo(2000);
      assertThat(history.endYear()).isEqualTo(2000);
    }

    @Test
    @DisplayName("有停刊年份但 ceased 为 false 应自动修正为 true")
    void shouldAutoCorrectedCeasedFlag() {
      PublicationHistory history = PublicationHistory.of(1990, 2020, false);
      assertThat(history.ceased()).isTrue();
    }
  }

  @Nested
  @DisplayName("状态判断方法")
  class StatusTests {

    @Test
    @DisplayName("isActive() 应正确判断是否仍在出版")
    void shouldCheckIsActive() {
      PublicationHistory active = PublicationHistory.active(1990);
      PublicationHistory ceased = PublicationHistory.ceased(1950, 2010);

      assertThat(active.isActive()).isTrue();
      assertThat(ceased.isActive()).isFalse();
    }

    @Test
    @DisplayName("hasStartYear() 应正确判断是否有创刊年份")
    void shouldCheckHasStartYear() {
      PublicationHistory withStart = PublicationHistory.active(1990);
      PublicationHistory withoutStart = PublicationHistory.of(null, 2020, true);

      assertThat(withStart.hasStartYear()).isTrue();
      assertThat(withoutStart.hasStartYear()).isFalse();
    }

    @Test
    @DisplayName("hasEndYear() 应正确判断是否有停刊年份")
    void shouldCheckHasEndYear() {
      PublicationHistory withEnd = PublicationHistory.ceased(1990, 2020);
      PublicationHistory withoutEnd = PublicationHistory.active(1990);

      assertThat(withEnd.hasEndYear()).isTrue();
      assertThat(withoutEnd.hasEndYear()).isFalse();
    }
  }

  @Nested
  @DisplayName("calculateYearsPublished()")
  class CalculateYearsPublishedTests {

    @Test
    @DisplayName("活跃期刊应计算到当前年份")
    void shouldCalculateYearsForActiveJournal() {
      PublicationHistory history = PublicationHistory.active(2000);
      int currentYear = 2024;

      Integer years = history.calculateYearsPublished(currentYear);
      assertThat(years).isEqualTo(25); // 2024 - 2000 + 1
    }

    @Test
    @DisplayName("已停刊期刊应计算到停刊年份")
    void shouldCalculateYearsForCeasedJournal() {
      PublicationHistory history = PublicationHistory.ceased(1990, 2010);
      int currentYear = 2024;

      Integer years = history.calculateYearsPublished(currentYear);
      assertThat(years).isEqualTo(21); // 2010 - 1990 + 1
    }

    @Test
    @DisplayName("无创刊年份应返回 null")
    void shouldReturnNullWhenNoStartYear() {
      PublicationHistory history = PublicationHistory.of(null, 2020, true);

      assertThat(history.calculateYearsPublished(2024)).isNull();
    }

    @Test
    @DisplayName("创刊第一年应返回 1")
    void shouldReturnOneForFirstYear() {
      PublicationHistory history = PublicationHistory.active(2024);

      assertThat(history.calculateYearsPublished(2024)).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("边界情况")
  class EdgeCaseTests {

    @Test
    @DisplayName("所有字段为 null 应允许创建")
    void shouldAllowAllNullFields() {
      PublicationHistory history = PublicationHistory.of(null, null, false);

      assertThat(history.startYear()).isNull();
      assertThat(history.endYear()).isNull();
      assertThat(history.ceased()).isFalse();
    }

    @Test
    @DisplayName("仅有创刊年份的活跃期刊")
    void shouldHandleOnlyStartYear() {
      PublicationHistory history = PublicationHistory.active(1950);

      assertThat(history.startYear()).isEqualTo(1950);
      assertThat(history.endYear()).isNull();
      assertThat(history.ceased()).isFalse();
      assertThat(history.isActive()).isTrue();
    }
  }

  @Nested
  @DisplayName("Record 特性")
  class RecordTests {

    @Test
    @DisplayName("equals 和 hashCode 应基于所有字段")
    void shouldImplementEqualsAndHashCode() {
      PublicationHistory history1 = PublicationHistory.of(1990, 2020, true);
      PublicationHistory history2 = PublicationHistory.of(1990, 2020, true);
      PublicationHistory history3 = PublicationHistory.of(1990, 2021, true);

      assertThat(history1).isEqualTo(history2);
      assertThat(history1.hashCode()).isEqualTo(history2.hashCode());
      assertThat(history1).isNotEqualTo(history3);
    }

    @Test
    @DisplayName("toString 应包含所有字段")
    void shouldHaveToString() {
      PublicationHistory history = PublicationHistory.of(1990, 2020, true);
      String str = history.toString();

      assertThat(str).contains("1990");
      assertThat(str).contains("2020");
      assertThat(str).contains("true");
    }
  }
}
