package dev.linqibin.patra.catalog.domain.model.vo.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// GeoLocation 值对象单元测试。
///
/// 基于 ROR Schema v2.0 的 locations 字段定义。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("GeoLocation 值对象")
class GeoLocationTest {

  @Nested
  @DisplayName("创建测试")
  class CreationTest {

    @Test
    @DisplayName("应正确创建完整的地理位置")
    void shouldCreateFullGeoLocation() {
      GeoLocation location =
          GeoLocation.builder()
              .geonamesId(4931972)
              .continentCode("NA")
              .continentName("North America")
              .countryCode("US")
              .countryName("United States")
              .subdivisionCode("MA")
              .subdivisionName("Massachusetts")
              .cityName("Cambridge")
              .latitude(new BigDecimal("42.3736"))
              .longitude(new BigDecimal("-71.1097"))
              .build();

      assertThat(location.geonamesId()).isEqualTo(4931972);
      assertThat(location.continentCode()).isEqualTo("NA");
      assertThat(location.continentName()).isEqualTo("North America");
      assertThat(location.countryCode()).isEqualTo("US");
      assertThat(location.countryName()).isEqualTo("United States");
      assertThat(location.subdivisionCode()).isEqualTo("MA");
      assertThat(location.subdivisionName()).isEqualTo("Massachusetts");
      assertThat(location.cityName()).isEqualTo("Cambridge");
      assertThat(location.latitude()).isEqualByComparingTo("42.3736");
      assertThat(location.longitude()).isEqualByComparingTo("-71.1097");
      assertThat(location.id()).isNull();
    }

    @Test
    @DisplayName("应正确创建带 ID 的地理位置")
    void shouldCreateGeoLocationWithId() {
      GeoLocation location =
          GeoLocation.builder()
              .id(123L)
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .build();

      assertThat(location.id()).isEqualTo(123L);
    }

    @Test
    @DisplayName("应正确创建最小的地理位置（仅国家）")
    void shouldCreateMinimalGeoLocation() {
      GeoLocation location =
          GeoLocation.builder()
              .geonamesId(2921044)
              .countryCode("DE")
              .countryName("Germany")
              .build();

      assertThat(location.geonamesId()).isEqualTo(2921044);
      assertThat(location.countryCode()).isEqualTo("DE");
      assertThat(location.countryName()).isEqualTo("Germany");
      assertThat(location.subdivisionCode()).isNull();
      assertThat(location.cityName()).isNull();
    }

    @Test
    @DisplayName("null GeoNames ID 应抛出异常")
    void shouldThrowWhenGeonamesIdIsNull() {
      assertThatThrownBy(
              () -> GeoLocation.builder().countryCode("US").countryName("United States").build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("GeoNames ID 不能为空");
    }

    @Test
    @DisplayName("空白国家代码应抛出异常")
    void shouldThrowWhenCountryCodeIsBlank() {
      assertThatThrownBy(
              () ->
                  GeoLocation.builder()
                      .geonamesId(4931972)
                      .countryCode("")
                      .countryName("United States")
                      .build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("国家代码不能为空");
    }

    @Test
    @DisplayName("空白国家名称应抛出异常")
    void shouldThrowWhenCountryNameIsBlank() {
      assertThatThrownBy(
              () ->
                  GeoLocation.builder()
                      .geonamesId(4931972)
                      .countryCode("US")
                      .countryName("")
                      .build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("国家名称不能为空");
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("hasCoordinates() 应正确判断是否有坐标")
    void shouldCheckHasCoordinates() {
      GeoLocation withCoords =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .latitude(new BigDecimal("42.3736"))
              .longitude(new BigDecimal("-71.1097"))
              .build();

      GeoLocation withoutCoords =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .build();

      GeoLocation partialCoords =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .latitude(new BigDecimal("42.3736"))
              .build();

      assertThat(withCoords.hasCoordinates()).isTrue();
      assertThat(withoutCoords.hasCoordinates()).isFalse();
      assertThat(partialCoords.hasCoordinates()).isFalse();
    }

    @Test
    @DisplayName("hasSubdivision() 应正确判断是否有省/州信息")
    void shouldCheckHasSubdivision() {
      GeoLocation withSubdivision =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .subdivisionCode("MA")
              .subdivisionName("Massachusetts")
              .build();

      GeoLocation withoutSubdivision =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .build();

      assertThat(withSubdivision.hasSubdivision()).isTrue();
      assertThat(withoutSubdivision.hasSubdivision()).isFalse();
    }

    @Test
    @DisplayName("hasCity() 应正确判断是否有城市信息")
    void shouldCheckHasCity() {
      GeoLocation withCity =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .cityName("Cambridge")
              .build();

      GeoLocation withoutCity =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .build();

      assertThat(withCity.hasCity()).isTrue();
      assertThat(withoutCity.hasCity()).isFalse();
    }

    @Test
    @DisplayName("hasId() 应正确判断是否已持久化")
    void shouldCheckHasId() {
      GeoLocation withId =
          GeoLocation.builder()
              .id(1L)
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .build();

      GeoLocation withoutId =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .build();

      assertThat(withId.hasId()).isTrue();
      assertThat(withoutId.hasId()).isFalse();
    }

    @Test
    @DisplayName("isInCountry() 应正确判断是否在指定国家")
    void shouldCheckIsInCountry() {
      GeoLocation location =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .build();

      assertThat(location.isInCountry("US")).isTrue();
      assertThat(location.isInCountry("us")).isTrue(); // 大小写不敏感
      assertThat(location.isInCountry("CN")).isFalse();
    }

    @Test
    @DisplayName("isInContinent() 应正确判断是否在指定洲")
    void shouldCheckIsInContinent() {
      GeoLocation location =
          GeoLocation.builder()
              .geonamesId(4931972)
              .continentCode("NA")
              .continentName("North America")
              .countryCode("US")
              .countryName("United States")
              .build();

      assertThat(location.isInContinent("NA")).isTrue();
      assertThat(location.isInContinent("na")).isTrue(); // 大小写不敏感
      assertThat(location.isInContinent("EU")).isFalse();
    }
  }

  @Nested
  @DisplayName("相等性测试")
  class EqualityTest {

    @Test
    @DisplayName("相同 GeoNames ID 的位置应相等（忽略 ID）")
    void shouldBeEqualWhenGeonamesIdSame() {
      GeoLocation loc1 =
          GeoLocation.builder()
              .id(1L)
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .build();

      GeoLocation loc2 =
          GeoLocation.builder()
              .id(2L)
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("USA") // 不同名称
              .build();

      assertThat(loc1).isEqualTo(loc2);
      assertThat(loc1.hashCode()).isEqualTo(loc2.hashCode());
    }

    @Test
    @DisplayName("不同 GeoNames ID 的位置应不相等")
    void shouldNotBeEqualWhenGeonamesIdDifferent() {
      GeoLocation loc1 =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .build();

      GeoLocation loc2 =
          GeoLocation.builder()
              .geonamesId(2921044)
              .countryCode("DE")
              .countryName("Germany")
              .build();

      assertThat(loc1).isNotEqualTo(loc2);
    }
  }

  @Nested
  @DisplayName("with-style 方法测试")
  class WithMethodsTest {

    @Test
    @DisplayName("withId() 应返回带 ID 的新实例")
    void shouldReturnNewInstanceWithId() {
      GeoLocation original =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .build();

      GeoLocation withId = original.withId(123L);

      assertThat(withId.id()).isEqualTo(123L);
      assertThat(withId.geonamesId()).isEqualTo(4931972);
      // 原对象不变
      assertThat(original.id()).isNull();
    }
  }
}
