package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo.ApcPrice;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// 开放获取信息值对象单元测试。
///
/// @author linqibin
/// @since 0.7.0
@DisplayName("OpenAccessInfo 开放获取信息值对象")
@Timeout(2)
class OpenAccessInfoTest {

  @Nested
  @DisplayName("工厂方法")
  class FactoryMethodTests {

    @Test
    @DisplayName("of() 应创建完整的开放获取信息")
    void shouldCreateWithAllFields() {
      // Given
      var prices = List.of(ApcPrice.of(3000, "USD"), ApcPrice.of(2500, "EUR"));

      // When
      OpenAccessInfo info = OpenAccessInfo.of(true, true, "gold", 3000, prices);

      // Then
      assertThat(info.isOa()).isTrue();
      assertThat(info.isInDoaj()).isTrue();
      assertThat(info.oaType()).isEqualTo("gold");
      assertThat(info.apcUsd()).isEqualTo(3000);
      assertThat(info.apcPrices()).hasSize(2);
    }

    @Test
    @DisplayName("notOpenAccess() 应创建非 OA 信息")
    void shouldCreateNotOpenAccess() {
      // When
      OpenAccessInfo info = OpenAccessInfo.notOpenAccess();

      // Then
      assertThat(info.isOa()).isFalse();
      assertThat(info.isInDoaj()).isFalse();
      assertThat(info.oaType()).isNull();
      assertThat(info.apcUsd()).isNull();
      assertThat(info.apcPrices()).isEmpty();
    }

    @Test
    @DisplayName("ofOaStatus() 应创建仅 OA 状态的信息")
    void shouldCreateWithOaStatusOnly() {
      // When
      OpenAccessInfo info = OpenAccessInfo.ofOaStatus(true, false, "hybrid");

      // Then
      assertThat(info.isOa()).isTrue();
      assertThat(info.isInDoaj()).isFalse();
      assertThat(info.oaType()).isEqualTo("hybrid");
      assertThat(info.apcUsd()).isNull();
      assertThat(info.apcPrices()).isEmpty();
    }

    @Test
    @DisplayName("ofApc() 应创建仅 APC 信息的 OA 信息")
    void shouldCreateWithApcOnly() {
      // Given
      var prices = List.of(ApcPrice.of(2500, "EUR"));

      // When
      OpenAccessInfo info = OpenAccessInfo.ofApc(2500, prices);

      // Then
      assertThat(info.isOa()).isTrue();
      assertThat(info.isInDoaj()).isFalse();
      assertThat(info.oaType()).isNull();
      assertThat(info.apcUsd()).isEqualTo(2500);
      assertThat(info.apcPrices()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("OA 类型判断")
  class OaTypeTests {

    @ParameterizedTest
    @DisplayName("isGoldOa() 应正确识别 Gold OA")
    @ValueSource(strings = {"gold", "GOLD", "Gold"})
    void shouldIdentifyGoldOa(String oaType) {
      OpenAccessInfo info = OpenAccessInfo.ofOaStatus(true, false, oaType);
      assertThat(info.isGoldOa()).isTrue();
    }

    @ParameterizedTest
    @DisplayName("非 Gold OA 类型应返回 false")
    @ValueSource(strings = {"green", "hybrid", "bronze", "diamond"})
    void shouldReturnFalseForNonGoldOa(String oaType) {
      OpenAccessInfo info = OpenAccessInfo.ofOaStatus(true, false, oaType);
      assertThat(info.isGoldOa()).isFalse();
    }

    @Test
    @DisplayName("null OA 类型应返回 false")
    void shouldReturnFalseForNullOaType() {
      OpenAccessInfo info = OpenAccessInfo.ofOaStatus(true, false, null);
      assertThat(info.isGoldOa()).isFalse();
    }

    @ParameterizedTest
    @DisplayName("isGreenOa() 应正确识别 Green OA")
    @ValueSource(strings = {"green", "GREEN", "Green"})
    void shouldIdentifyGreenOa(String oaType) {
      OpenAccessInfo info = OpenAccessInfo.ofOaStatus(true, false, oaType);
      assertThat(info.isGreenOa()).isTrue();
    }

    @ParameterizedTest
    @DisplayName("isHybridOa() 应正确识别 Hybrid OA")
    @ValueSource(strings = {"hybrid", "HYBRID", "Hybrid"})
    void shouldIdentifyHybridOa(String oaType) {
      OpenAccessInfo info = OpenAccessInfo.ofOaStatus(true, false, oaType);
      assertThat(info.isHybridOa()).isTrue();
    }
  }

  @Nested
  @DisplayName("APC 相关判断")
  class ApcTests {

    @Test
    @DisplayName("hasApc() 应正确判断是否有 APC")
    void shouldCheckHasApc() {
      OpenAccessInfo withApc = OpenAccessInfo.of(true, false, "gold", 3000, List.of());
      OpenAccessInfo withoutApc = OpenAccessInfo.ofOaStatus(true, false, "green");
      OpenAccessInfo withZeroApc = OpenAccessInfo.of(true, false, "diamond", 0, List.of());

      assertThat(withApc.hasApc()).isTrue();
      assertThat(withoutApc.hasApc()).isFalse();
      assertThat(withZeroApc.hasApc()).isFalse();
    }

    @Test
    @DisplayName("hasMultiplePrices() 应正确判断是否有多货币价格")
    void shouldCheckHasMultiplePrices() {
      var prices = List.of(ApcPrice.of(3000, "USD"), ApcPrice.of(2500, "EUR"));
      OpenAccessInfo withPrices = OpenAccessInfo.of(true, false, "gold", 3000, prices);
      OpenAccessInfo withoutPrices = OpenAccessInfo.ofOaStatus(true, false, "gold");

      assertThat(withPrices.hasMultiplePrices()).isTrue();
      assertThat(withoutPrices.hasMultiplePrices()).isFalse();
    }

    @Test
    @DisplayName("getPriceByCurrency() 应返回指定货币的价格")
    void shouldGetPriceByCurrency() {
      var prices =
          List.of(ApcPrice.of(3000, "USD"), ApcPrice.of(2500, "EUR"), ApcPrice.of(20000, "CNY"));
      OpenAccessInfo info = OpenAccessInfo.of(true, false, "gold", 3000, prices);

      assertThat(info.getPriceByCurrency("USD")).isEqualTo(3000);
      assertThat(info.getPriceByCurrency("EUR")).isEqualTo(2500);
      assertThat(info.getPriceByCurrency("CNY")).isEqualTo(20000);
      assertThat(info.getPriceByCurrency("GBP")).isNull();
    }
  }

  @Nested
  @DisplayName("不可变性")
  class ImmutabilityTests {

    @Test
    @DisplayName("apcPrices 列表应是不可变的")
    void shouldReturnDefensiveCopyOfPrices() {
      var prices = new java.util.ArrayList<>(List.of(ApcPrice.of(3000, "USD")));
      OpenAccessInfo info = OpenAccessInfo.of(true, false, "gold", 3000, prices);

      // 修改原始列表
      prices.clear();

      // 值对象内部列表不受影响
      assertThat(info.apcPrices()).hasSize(1);
    }

    @Test
    @DisplayName("返回的 apcPrices 列表不可修改")
    void shouldReturnUnmodifiableList() {
      OpenAccessInfo info = OpenAccessInfo.of(true, false, "gold", 3000, List.of());

      assertThatThrownBy(() -> info.apcPrices().add(ApcPrice.of(1000, "GBP")))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("Record 特性")
  class RecordTests {

    @Test
    @DisplayName("equals 和 hashCode 应基于所有字段")
    void shouldImplementEqualsAndHashCode() {
      var prices = List.of(ApcPrice.of(3000, "USD"));
      OpenAccessInfo info1 = OpenAccessInfo.of(true, true, "gold", 3000, prices);
      OpenAccessInfo info2 = OpenAccessInfo.of(true, true, "gold", 3000, prices);
      OpenAccessInfo info3 = OpenAccessInfo.of(true, false, "gold", 3000, prices);

      assertThat(info1).isEqualTo(info2);
      assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
      assertThat(info1).isNotEqualTo(info3);
    }

    @Test
    @DisplayName("toString 应包含关键字段")
    void shouldHaveToString() {
      OpenAccessInfo info = OpenAccessInfo.of(true, true, "gold", 3000, List.of());
      String str = info.toString();

      assertThat(str).contains("true");
      assertThat(str).contains("gold");
      assertThat(str).contains("3000");
    }
  }

  @Nested
  @DisplayName("ApcPrice 内嵌值对象")
  class ApcPriceTests {

    @Test
    @DisplayName("of() 应创建有效的 APC 价格")
    void shouldCreateValidApcPrice() {
      ApcPrice price = ApcPrice.of(3000, "USD");

      assertThat(price.price()).isEqualTo(3000);
      assertThat(price.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("价格不能为负数")
    void shouldRejectNegativePrice() {
      assertThatThrownBy(() -> ApcPrice.of(-100, "USD"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @DisplayName("货币代码不能为空")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldRejectBlankCurrency(String currency) {
      assertThatThrownBy(() -> ApcPrice.of(3000, currency))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
