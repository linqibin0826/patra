package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueDetail 值对象单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueDetail 值对象")
@Timeout(2)
class VenueDetailTest {

  // ========== 测试数据常量 ==========

  private static final String ABBREVIATED_TITLE = "Nat. Med.";
  private static final List<String> ALTERNATE_TITLES = List.of("Nature Medicine", "Nat Med");
  private static final String HOMEPAGE_URL = "https://www.nature.com/nm";
  private static final String FREQUENCY = "Monthly";
  private static final String COUNTRY_CODE = "US";
  private static final String OA_TYPE = "gold";

  @Nested
  @DisplayName("创建测试")
  class CreationTests {

    @Test
    @DisplayName("使用 builder 创建完整的 VenueDetail")
    void createWithBuilder_fullFields_success() {
      // given
      PublicationHistory history = PublicationHistory.active(1995);
      VenueLanguages languages = VenueLanguages.ofSingleLanguage("eng");
      HostOrganization host = HostOrganization.of("I123456789", "Springer Nature");
      IndexingInfo indexing = IndexingInfo.of("C", "Nat Med", "Nat. Med.");

      // when
      VenueDetail detail =
          VenueDetail.builder()
              .abbreviatedTitle(ABBREVIATED_TITLE)
              .alternateTitles(ALTERNATE_TITLES)
              .homepageUrl(HOMEPAGE_URL)
              .frequency(FREQUENCY)
              .publicationHistory(history)
              .languages(languages)
              .hostOrganization(host)
              .countryCode(COUNTRY_CODE)
              .indexingInfo(indexing)
              .isOa(true)
              .isInDoaj(true)
              .oaType(OA_TYPE)
              .build();

      // then
      assertThat(detail.abbreviatedTitle()).isEqualTo(ABBREVIATED_TITLE);
      assertThat(detail.alternateTitles()).containsExactlyElementsOf(ALTERNATE_TITLES);
      assertThat(detail.homepageUrl()).isEqualTo(HOMEPAGE_URL);
      assertThat(detail.frequency()).isEqualTo(FREQUENCY);
      assertThat(detail.publicationHistory()).isEqualTo(history);
      assertThat(detail.languages()).isEqualTo(languages);
      assertThat(detail.hostOrganization()).isEqualTo(host);
      assertThat(detail.countryCode()).isEqualTo(COUNTRY_CODE);
      assertThat(detail.indexingInfo()).isEqualTo(indexing);
      assertThat(detail.isOa()).isTrue();
      assertThat(detail.isInDoaj()).isTrue();
      assertThat(detail.oaType()).isEqualTo(OA_TYPE);
    }

    @Test
    @DisplayName("创建空的 VenueDetail")
    void createEmpty_success() {
      // when
      VenueDetail detail = VenueDetail.empty();

      // then
      assertThat(detail.abbreviatedTitle()).isNull();
      assertThat(detail.alternateTitles()).isEmpty();
      assertThat(detail.homepageUrl()).isNull();
      assertThat(detail.frequency()).isNull();
      assertThat(detail.publicationHistory()).isNull();
      assertThat(detail.languages()).isNull();
      assertThat(detail.hostOrganization()).isNull();
      assertThat(detail.countryCode()).isNull();
      assertThat(detail.indexingInfo()).isNull();
      assertThat(detail.isOa()).isFalse();
      assertThat(detail.isInDoaj()).isFalse();
      assertThat(detail.oaType()).isNull();
      assertThat(detail.extData()).isEmpty();
    }

    @Test
    @DisplayName("alternateTitles 为 null 时转换为空列表")
    void createWithNullAlternateTitles_convertsToEmptyList() {
      // when
      VenueDetail detail = VenueDetail.builder().alternateTitles(null).build();

      // then
      assertThat(detail.alternateTitles()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("extData 为 null 时转换为空 Map")
    void createWithNullExtData_convertsToEmptyMap() {
      // when
      VenueDetail detail = VenueDetail.builder().extData(null).build();

      // then
      assertThat(detail.extData()).isNotNull().isEmpty();
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodTests {

    @Test
    @DisplayName("hasAbbreviatedTitle - 有缩写标题时返回 true")
    void hasAbbreviatedTitle_withTitle_returnsTrue() {
      VenueDetail detail = VenueDetail.builder().abbreviatedTitle(ABBREVIATED_TITLE).build();

      assertThat(detail.hasAbbreviatedTitle()).isTrue();
    }

    @Test
    @DisplayName("hasAbbreviatedTitle - 无缩写标题时返回 false")
    void hasAbbreviatedTitle_withoutTitle_returnsFalse() {
      VenueDetail detail = VenueDetail.empty();

      assertThat(detail.hasAbbreviatedTitle()).isFalse();
    }

    @Test
    @DisplayName("hasAlternateTitles - 有替代名称时返回 true")
    void hasAlternateTitles_withTitles_returnsTrue() {
      VenueDetail detail = VenueDetail.builder().alternateTitles(ALTERNATE_TITLES).build();

      assertThat(detail.hasAlternateTitles()).isTrue();
    }

    @Test
    @DisplayName("hasAlternateTitles - 空列表时返回 false")
    void hasAlternateTitles_withEmptyList_returnsFalse() {
      VenueDetail detail = VenueDetail.empty();

      assertThat(detail.hasAlternateTitles()).isFalse();
    }

    @Test
    @DisplayName("hasHomepageUrl - 有主页 URL 时返回 true")
    void hasHomepageUrl_withUrl_returnsTrue() {
      VenueDetail detail = VenueDetail.builder().homepageUrl(HOMEPAGE_URL).build();

      assertThat(detail.hasHomepageUrl()).isTrue();
    }

    @Test
    @DisplayName("hasPublicationHistory - 有出版历史时返回 true")
    void hasPublicationHistory_withHistory_returnsTrue() {
      VenueDetail detail =
          VenueDetail.builder().publicationHistory(PublicationHistory.active(2000)).build();

      assertThat(detail.hasPublicationHistory()).isTrue();
    }

    @Test
    @DisplayName("hasLanguages - 有语言信息时返回 true")
    void hasLanguages_withLanguages_returnsTrue() {
      VenueDetail detail =
          VenueDetail.builder().languages(VenueLanguages.ofSingleLanguage("eng")).build();

      assertThat(detail.hasLanguages()).isTrue();
    }

    @Test
    @DisplayName("hasHostOrganization - 有宿主机构时返回 true")
    void hasHostOrganization_withHost_returnsTrue() {
      VenueDetail detail =
          VenueDetail.builder()
              .hostOrganization(HostOrganization.of("I123", "Test Publisher"))
              .build();

      assertThat(detail.hasHostOrganization()).isTrue();
    }

    @Test
    @DisplayName("hasIndexingInfo - 有索引信息时返回 true")
    void hasIndexingInfo_withIndexing_returnsTrue() {
      VenueDetail detail = VenueDetail.builder().indexingInfo(IndexingInfo.ofStatus("C")).build();

      assertThat(detail.hasIndexingInfo()).isTrue();
    }

    @Test
    @DisplayName("isCurrentlyIndexed - MEDLINE 收录时返回 true")
    void isCurrentlyIndexed_whenIndexed_returnsTrue() {
      VenueDetail detail = VenueDetail.builder().indexingInfo(IndexingInfo.ofStatus("C")).build();

      assertThat(detail.isCurrentlyIndexed()).isTrue();
    }

    @Test
    @DisplayName("isCurrentlyIndexed - 无索引信息时返回 false")
    void isCurrentlyIndexed_withoutIndexing_returnsFalse() {
      VenueDetail detail = VenueDetail.empty();

      assertThat(detail.isCurrentlyIndexed()).isFalse();
    }

    @Test
    @DisplayName("isCeased - 已停刊时返回 true")
    void isCeased_whenCeased_returnsTrue() {
      VenueDetail detail =
          VenueDetail.builder().publicationHistory(PublicationHistory.ceased(1990, 2020)).build();

      assertThat(detail.isCeased()).isTrue();
    }

    @Test
    @DisplayName("isCeased - 活跃时返回 false")
    void isCeased_whenActive_returnsFalse() {
      VenueDetail detail =
          VenueDetail.builder().publicationHistory(PublicationHistory.active(2000)).build();

      assertThat(detail.isCeased()).isFalse();
    }

    @Test
    @DisplayName("isCeased - 无出版历史时返回 false")
    void isCeased_withoutHistory_returnsFalse() {
      VenueDetail detail = VenueDetail.empty();

      assertThat(detail.isCeased()).isFalse();
    }
  }

  @Nested
  @DisplayName("不可变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("alternateTitles 列表不可修改")
    void alternateTitles_isUnmodifiable() {
      VenueDetail detail =
          VenueDetail.builder().alternateTitles(List.of("Title 1", "Title 2")).build();

      assertThatThrownBy(() -> detail.alternateTitles().add("Title 3"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("extData Map 不可修改")
    void extData_isUnmodifiable() {
      VenueDetail detail = VenueDetail.builder().extData(Map.of("key", "value")).build();

      assertThatThrownBy(() -> detail.extData().put("newKey", "newValue"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("扩展数据测试")
  class ExtDataTests {

    @Test
    @DisplayName("可以存储扩展数据")
    void storeExtData_success() {
      // given
      Map<String, Object> extData = Map.of("scopusId", "12345", "impactFactor", 50.5);

      // when
      VenueDetail detail = VenueDetail.builder().extData(extData).build();

      // then
      assertThat(detail.extData()).containsEntry("scopusId", "12345");
      assertThat(detail.extData()).containsEntry("impactFactor", 50.5);
    }
  }

  @Nested
  @DisplayName("Record 等价性测试")
  class EqualityTests {

    @Test
    @DisplayName("相同字段的 VenueDetail 相等")
    void sameFields_areEqual() {
      // given
      VenueDetail detail1 =
          VenueDetail.builder()
              .abbreviatedTitle(ABBREVIATED_TITLE)
              .frequency(FREQUENCY)
              .isOa(true)
              .build();

      VenueDetail detail2 =
          VenueDetail.builder()
              .abbreviatedTitle(ABBREVIATED_TITLE)
              .frequency(FREQUENCY)
              .isOa(true)
              .build();

      // then
      assertThat(detail1).isEqualTo(detail2);
      assertThat(detail1.hashCode()).isEqualTo(detail2.hashCode());
    }

    @Test
    @DisplayName("不同字段的 VenueDetail 不相等")
    void differentFields_areNotEqual() {
      // given
      VenueDetail detail1 = VenueDetail.builder().abbreviatedTitle(ABBREVIATED_TITLE).build();

      VenueDetail detail2 = VenueDetail.builder().abbreviatedTitle("Different Title").build();

      // then
      assertThat(detail1).isNotEqualTo(detail2);
    }
  }
}
