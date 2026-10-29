package dev.patra.identity.infra.account.geolocation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// RegionText 单元测试：ip2region 的五段原始串 → 展示用的属地（PAP-99 设计第 8.3 节）。
@DisplayName("RegionText 单元测试")
class RegionTextTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = ';',
      value = {
        "中国|广东省|深圳市|电信|CN; 中国 广东 深圳",
        "中国|上海市|上海市|电信|CN; 中国 上海",
        "中国|北京市|北京市|腾讯|CN; 中国 北京",
        "United States|California|0|Google LLC|US; 美国",
        "Australia|Queensland|Brisbane|0|AU; 澳大利亚",
        "United States|Florida|Miami|Google LLC|US; 美国",
        "中国|香港特别行政区|0|0|CN; 中国 香港",
        "中国|台湾省|台北市|0|CN; 中国 台湾 台北",
        "中国|吉林省|延边朝鲜族自治州|0|CN; 中国 吉林 延边朝鲜族自治州",
        "中国|新疆|乌鲁木齐市|0|CN; 中国 新疆 乌鲁木齐",
        "中国|0|0|0|CN; 中国",
        "Narnia|0|0|0|0; Narnia",
        "Xanadu|0|0|0|ZZ; Xanadu"
      })
  @DisplayName("中国到国家 / 省 / 市并去掉后缀，境外只到国家并转中文名")
  void should_normalize_region(String raw, String expected) {
    assertThat(RegionText.normalize(raw)).isEqualTo(expected);
  }

  @Test
  @DisplayName("保留地址、空串、段数不对、未知国家：为空")
  void should_return_null_for_unusable_results() {
    assertThat(RegionText.normalize("Reserved|Reserved|Reserved|0|0")).isNull();
    assertThat(RegionText.normalize("")).isNull();
    assertThat(RegionText.normalize(null)).isNull();
    assertThat(RegionText.normalize("中国|广东省|深圳市|电信")).isNull();
    assertThat(RegionText.normalize("0|0|0|0|0")).isNull();
  }

  @Test
  @DisplayName("结果超过 64 个字符截断")
  void should_truncate_to_64_characters() {
    assertThat(RegionText.normalize("X".repeat(80) + "|0|0|0|0")).hasSize(64);
  }
}
