package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// 出版概况值对象单元测试。
///
/// @author linqibin
/// @since 0.7.0
@DisplayName("PublicationProfile 出版概况值对象")
@Timeout(2)
class PublicationProfileTest {

  @Nested
  @DisplayName("工厂方法")
  class FactoryMethodTests {

    @Test
    @DisplayName("builder() 应创建完整的出版概况")
    void shouldCreateWithAllFields() {
      // Given
      var history = PublicationHistory.active(1995);
      var languages = VenueLanguages.of(List.of("eng"), List.of("eng", "chi"));
      var host = HostOrganization.of("I123", "Springer Nature");
      var indexing = IndexingInfo.of("C", "Nat Med", "Nat. Med.");

      // When
      PublicationProfile profile =
          PublicationProfile.builder()
              .abbreviatedTitle("Nat. Med.")
              .alternateTitles(List.of("Nature Medicine"))
              .homepageUrl("https://www.nature.com/nm")
              .frequency("Monthly")
              .publicationHistory(history)
              .languages(languages)
              .hostOrganization(host)
              .countryCode("US")
              .indexingInfo(indexing)
              .extData(Map.of("impact_factor", 87.241))
              .build();

      // Then
      assertThat(profile.abbreviatedTitle()).isEqualTo("Nat. Med.");
      assertThat(profile.alternateTitles()).containsExactly("Nature Medicine");
      assertThat(profile.homepageUrl()).isEqualTo("https://www.nature.com/nm");
      assertThat(profile.frequency()).isEqualTo("Monthly");
      assertThat(profile.publicationHistory()).isEqualTo(history);
      assertThat(profile.languages()).isEqualTo(languages);
      assertThat(profile.hostOrganization()).isEqualTo(host);
      assertThat(profile.countryCode()).isEqualTo("US");
      assertThat(profile.indexingInfo()).isEqualTo(indexing);
      assertThat(profile.extData()).containsEntry("impact_factor", 87.241);
    }

    @Test
    @DisplayName("empty() 应创建空的出版概况")
    void shouldCreateEmptyProfile() {
      // When
      PublicationProfile profile = PublicationProfile.empty();

      // Then
      assertThat(profile.abbreviatedTitle()).isNull();
      assertThat(profile.alternateTitles()).isEmpty();
      assertThat(profile.homepageUrl()).isNull();
      assertThat(profile.frequency()).isNull();
      assertThat(profile.publicationHistory()).isNull();
      assertThat(profile.languages()).isNull();
      assertThat(profile.hostOrganization()).isNull();
      assertThat(profile.countryCode()).isNull();
      assertThat(profile.indexingInfo()).isNull();
      assertThat(profile.extData()).isEmpty();
    }

    @Test
    @DisplayName("countryCode 应规范化为大写并过滤非法值")
    void shouldNormalizeCountryCode() {
      PublicationProfile lowerCase = PublicationProfile.builder().countryCode("us").build();
      PublicationProfile iso3 = PublicationProfile.builder().countryCode("USA").build();
      PublicationProfile name = PublicationProfile.builder().countryCode("United States").build();
      PublicationProfile invalid = PublicationProfile.builder().countryCode("Unknownland").build();
      PublicationProfile blank = PublicationProfile.builder().countryCode(" ").build();

      assertThat(lowerCase.countryCode()).isEqualTo("US");
      assertThat(iso3.countryCode()).isEqualTo("US");
      assertThat(name.countryCode()).isEqualTo("US");
      assertThat(invalid.countryCode()).isNull();
      assertThat(blank.countryCode()).isNull();
    }
  }

  @Nested
  @DisplayName("便捷判断方法")
  class ConvenienceMethodTests {

    @Test
    @DisplayName("hasAbbreviatedTitle() 应正确判断")
    void shouldCheckHasAbbreviatedTitle() {
      PublicationProfile withTitle =
          PublicationProfile.builder().abbreviatedTitle("Nat. Med.").build();
      PublicationProfile withBlank = PublicationProfile.builder().abbreviatedTitle("   ").build();
      PublicationProfile withNull = PublicationProfile.empty();

      assertThat(withTitle.hasAbbreviatedTitle()).isTrue();
      assertThat(withBlank.hasAbbreviatedTitle()).isFalse();
      assertThat(withNull.hasAbbreviatedTitle()).isFalse();
    }

    @Test
    @DisplayName("hasAlternateTitles() 应正确判断")
    void shouldCheckHasAlternateTitles() {
      PublicationProfile withTitles =
          PublicationProfile.builder().alternateTitles(List.of("Nature Medicine")).build();
      PublicationProfile withEmpty =
          PublicationProfile.builder().alternateTitles(List.of()).build();
      PublicationProfile withNull = PublicationProfile.empty();

      assertThat(withTitles.hasAlternateTitles()).isTrue();
      assertThat(withEmpty.hasAlternateTitles()).isFalse();
      assertThat(withNull.hasAlternateTitles()).isFalse();
    }

    @Test
    @DisplayName("hasHomepageUrl() 应正确判断")
    void shouldCheckHasHomepageUrl() {
      PublicationProfile withUrl =
          PublicationProfile.builder().homepageUrl("https://example.com").build();
      PublicationProfile withBlank = PublicationProfile.builder().homepageUrl("  ").build();
      PublicationProfile withNull = PublicationProfile.empty();

      assertThat(withUrl.hasHomepageUrl()).isTrue();
      assertThat(withBlank.hasHomepageUrl()).isFalse();
      assertThat(withNull.hasHomepageUrl()).isFalse();
    }

    @Test
    @DisplayName("hasFrequency() 应正确判断")
    void shouldCheckHasFrequency() {
      PublicationProfile withFreq = PublicationProfile.builder().frequency("Monthly").build();
      PublicationProfile withBlank = PublicationProfile.builder().frequency("").build();
      PublicationProfile withNull = PublicationProfile.empty();

      assertThat(withFreq.hasFrequency()).isTrue();
      assertThat(withBlank.hasFrequency()).isFalse();
      assertThat(withNull.hasFrequency()).isFalse();
    }

    @Test
    @DisplayName("hasPublicationHistory() 应正确判断")
    void shouldCheckHasPublicationHistory() {
      PublicationProfile withHistory =
          PublicationProfile.builder().publicationHistory(PublicationHistory.active(1990)).build();
      PublicationProfile withNull = PublicationProfile.empty();

      assertThat(withHistory.hasPublicationHistory()).isTrue();
      assertThat(withNull.hasPublicationHistory()).isFalse();
    }

    @Test
    @DisplayName("hasLanguages() 应正确判断")
    void shouldCheckHasLanguages() {
      PublicationProfile withLang =
          PublicationProfile.builder().languages(VenueLanguages.ofSingleLanguage("eng")).build();
      PublicationProfile withNull = PublicationProfile.empty();

      assertThat(withLang.hasLanguages()).isTrue();
      assertThat(withNull.hasLanguages()).isFalse();
    }

    @Test
    @DisplayName("hasHostOrganization() 应正确判断")
    void shouldCheckHasHostOrganization() {
      PublicationProfile withHost =
          PublicationProfile.builder().hostOrganization(HostOrganization.of("I1", "Test")).build();
      PublicationProfile withNull = PublicationProfile.empty();

      assertThat(withHost.hasHostOrganization()).isTrue();
      assertThat(withNull.hasHostOrganization()).isFalse();
    }

    @Test
    @DisplayName("hasCountryCode() 应正确判断")
    void shouldCheckHasCountryCode() {
      PublicationProfile withCode = PublicationProfile.builder().countryCode("US").build();
      PublicationProfile withBlank = PublicationProfile.builder().countryCode("  ").build();
      PublicationProfile withNull = PublicationProfile.empty();

      assertThat(withCode.hasCountryCode()).isTrue();
      assertThat(withBlank.hasCountryCode()).isFalse();
      assertThat(withNull.hasCountryCode()).isFalse();
    }

    @Test
    @DisplayName("hasIndexingInfo() 应正确判断")
    void shouldCheckHasIndexingInfo() {
      PublicationProfile withIndexing =
          PublicationProfile.builder().indexingInfo(IndexingInfo.ofStatus("C")).build();
      PublicationProfile withNull = PublicationProfile.empty();

      assertThat(withIndexing.hasIndexingInfo()).isTrue();
      assertThat(withNull.hasIndexingInfo()).isFalse();
    }

    @Test
    @DisplayName("hasExtData() 应正确判断")
    void shouldCheckHasExtData() {
      PublicationProfile withData =
          PublicationProfile.builder().extData(Map.of("key", "value")).build();
      PublicationProfile withEmpty = PublicationProfile.builder().extData(Map.of()).build();
      PublicationProfile withNull = PublicationProfile.empty();

      assertThat(withData.hasExtData()).isTrue();
      assertThat(withEmpty.hasExtData()).isFalse();
      assertThat(withNull.hasExtData()).isFalse();
    }
  }

  @Nested
  @DisplayName("代理方法")
  class DelegateMethodTests {

    @Test
    @DisplayName("isCeased() 应代理到 publicationHistory")
    void shouldDelegateToCeased() {
      PublicationProfile ceased =
          PublicationProfile.builder()
              .publicationHistory(PublicationHistory.ceased(1990, 2020))
              .build();
      PublicationProfile active =
          PublicationProfile.builder().publicationHistory(PublicationHistory.active(1990)).build();
      PublicationProfile noHistory = PublicationProfile.empty();

      assertThat(ceased.isCeased()).isTrue();
      assertThat(active.isCeased()).isFalse();
      assertThat(noHistory.isCeased()).isFalse();
    }

    @Test
    @DisplayName("isCurrentlyIndexed() 应代理到 indexingInfo")
    void shouldDelegateToCurrentlyIndexed() {
      PublicationProfile indexed =
          PublicationProfile.builder().indexingInfo(IndexingInfo.ofStatus("C")).build();
      PublicationProfile notIndexed =
          PublicationProfile.builder().indexingInfo(IndexingInfo.ofStatus("N")).build();
      PublicationProfile noInfo = PublicationProfile.empty();

      assertThat(indexed.isCurrentlyIndexed()).isTrue();
      assertThat(notIndexed.isCurrentlyIndexed()).isFalse();
      assertThat(noInfo.isCurrentlyIndexed()).isFalse();
    }

    @Test
    @DisplayName("isEnglishJournal() 应代理到 languages")
    void shouldDelegateToEnglishCheck() {
      PublicationProfile english =
          PublicationProfile.builder().languages(VenueLanguages.ofSingleLanguage("eng")).build();
      PublicationProfile chinese =
          PublicationProfile.builder().languages(VenueLanguages.ofSingleLanguage("chi")).build();
      PublicationProfile noLang = PublicationProfile.empty();

      assertThat(english.isEnglishJournal()).isTrue();
      assertThat(chinese.isEnglishJournal()).isFalse();
      assertThat(noLang.isEnglishJournal()).isFalse();
    }

    @Test
    @DisplayName("isChineseJournal() 应代理到 languages")
    void shouldDelegateToChineseCheck() {
      PublicationProfile chinese =
          PublicationProfile.builder().languages(VenueLanguages.ofSingleLanguage("chi")).build();
      PublicationProfile english =
          PublicationProfile.builder().languages(VenueLanguages.ofSingleLanguage("eng")).build();
      PublicationProfile noLang = PublicationProfile.empty();

      assertThat(chinese.isChineseJournal()).isTrue();
      assertThat(english.isChineseJournal()).isFalse();
      assertThat(noLang.isChineseJournal()).isFalse();
    }

    @Test
    @DisplayName("getStartYear() 应代理到 publicationHistory")
    void shouldDelegateToStartYear() {
      PublicationProfile withHistory =
          PublicationProfile.builder().publicationHistory(PublicationHistory.active(1995)).build();
      PublicationProfile noHistory = PublicationProfile.empty();

      assertThat(withHistory.getStartYear()).isEqualTo(1995);
      assertThat(noHistory.getStartYear()).isNull();
    }

    @Test
    @DisplayName("getEndYear() 应代理到 publicationHistory")
    void shouldDelegateToEndYear() {
      PublicationProfile ceased =
          PublicationProfile.builder()
              .publicationHistory(PublicationHistory.ceased(1990, 2020))
              .build();
      PublicationProfile active =
          PublicationProfile.builder().publicationHistory(PublicationHistory.active(1990)).build();
      PublicationProfile noHistory = PublicationProfile.empty();

      assertThat(ceased.getEndYear()).isEqualTo(2020);
      assertThat(active.getEndYear()).isNull();
      assertThat(noHistory.getEndYear()).isNull();
    }

    @Test
    @DisplayName("getMedlineTa() 应代理到 indexingInfo")
    void shouldDelegateToMedlineTa() {
      PublicationProfile withInfo =
          PublicationProfile.builder().indexingInfo(IndexingInfo.of("C", "Nat Med", null)).build();
      PublicationProfile noInfo = PublicationProfile.empty();

      assertThat(withInfo.getMedlineTa()).isEqualTo("Nat Med");
      assertThat(noInfo.getMedlineTa()).isNull();
    }

    @Test
    @DisplayName("getIsoAbbreviation() 应代理到 indexingInfo")
    void shouldDelegateToIsoAbbreviation() {
      PublicationProfile withInfo =
          PublicationProfile.builder()
              .indexingInfo(IndexingInfo.of("C", null, "Nat. Med."))
              .build();
      PublicationProfile noInfo = PublicationProfile.empty();

      assertThat(withInfo.getIsoAbbreviation()).isEqualTo("Nat. Med.");
      assertThat(noInfo.getIsoAbbreviation()).isNull();
    }

    @Test
    @DisplayName("getMainLanguage() 应代理到 languages")
    void shouldDelegateToMainLanguage() {
      PublicationProfile withLang =
          PublicationProfile.builder()
              .languages(VenueLanguages.of(List.of("eng"), List.of("chi")))
              .build();
      PublicationProfile noLang = PublicationProfile.empty();

      assertThat(withLang.getMainLanguage()).isEqualTo("eng");
      assertThat(noLang.getMainLanguage()).isNull();
    }

    @Test
    @DisplayName("getHostOrganizationName() 应代理到 hostOrganization")
    void shouldDelegateToHostOrgName() {
      PublicationProfile withHost =
          PublicationProfile.builder()
              .hostOrganization(HostOrganization.of("I1", "Springer Nature"))
              .build();
      PublicationProfile noHost = PublicationProfile.empty();

      assertThat(withHost.getHostOrganizationName()).isEqualTo("Springer Nature");
      assertThat(noHost.getHostOrganizationName()).isNull();
    }

    @Test
    @DisplayName("getHostOrganizationId() 应代理到 hostOrganization")
    void shouldDelegateToHostOrgId() {
      PublicationProfile withHost =
          PublicationProfile.builder()
              .hostOrganization(HostOrganization.of("I12345", "Test Org"))
              .build();
      PublicationProfile noHost = PublicationProfile.empty();

      assertThat(withHost.getHostOrganizationId()).isEqualTo("I12345");
      assertThat(noHost.getHostOrganizationId()).isNull();
    }
  }

  @Nested
  @DisplayName("不可变性")
  class ImmutabilityTests {

    @Test
    @DisplayName("alternateTitles 列表应是不可变的")
    void shouldReturnDefensiveCopyOfAlternateTitles() {
      var titles = new java.util.ArrayList<>(List.of("Title 1", "Title 2"));
      PublicationProfile profile = PublicationProfile.builder().alternateTitles(titles).build();

      // 修改原始列表
      titles.clear();

      // 值对象内部列表不受影响
      assertThat(profile.alternateTitles()).hasSize(2);
    }

    @Test
    @DisplayName("返回的 alternateTitles 列表不可修改")
    void shouldReturnUnmodifiableAlternateTitlesList() {
      PublicationProfile profile =
          PublicationProfile.builder().alternateTitles(List.of("Title")).build();

      assertThatThrownBy(() -> profile.alternateTitles().add("New Title"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("extData Map 应是不可变的")
    void shouldReturnDefensiveCopyOfExtData() {
      var data = new HashMap<String, Object>();
      data.put("key", "value");
      PublicationProfile profile = PublicationProfile.builder().extData(data).build();

      // 修改原始 Map
      data.clear();

      // 值对象内部 Map 不受影响
      assertThat(profile.extData()).hasSize(1);
    }

    @Test
    @DisplayName("返回的 extData Map 不可修改")
    void shouldReturnUnmodifiableExtDataMap() {
      PublicationProfile profile =
          PublicationProfile.builder().extData(Map.of("key", "value")).build();

      assertThatThrownBy(() -> profile.extData().put("new", "data"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("Record 特性")
  class RecordTests {

    @Test
    @DisplayName("equals 和 hashCode 应基于所有字段")
    void shouldImplementEqualsAndHashCode() {
      var history = PublicationHistory.active(1995);
      PublicationProfile profile1 =
          PublicationProfile.builder()
              .abbreviatedTitle("Nat. Med.")
              .publicationHistory(history)
              .countryCode("US")
              .build();
      PublicationProfile profile2 =
          PublicationProfile.builder()
              .abbreviatedTitle("Nat. Med.")
              .publicationHistory(history)
              .countryCode("US")
              .build();
      PublicationProfile profile3 =
          PublicationProfile.builder()
              .abbreviatedTitle("Nat. Med.")
              .publicationHistory(history)
              .countryCode("UK")
              .build();

      assertThat(profile1).isEqualTo(profile2);
      assertThat(profile1.hashCode()).isEqualTo(profile2.hashCode());
      assertThat(profile1).isNotEqualTo(profile3);
    }

    @Test
    @DisplayName("toString 应包含关键字段")
    void shouldHaveToString() {
      PublicationProfile profile =
          PublicationProfile.builder()
              .abbreviatedTitle("Nat. Med.")
              .countryCode("US")
              .frequency("Monthly")
              .build();
      String str = profile.toString();

      assertThat(str).contains("Nat. Med.");
      assertThat(str).contains("US");
      assertThat(str).contains("Monthly");
    }
  }
}
