package dev.linqibin.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/// Venue 值对象 JSON 序列化/反序列化兼容性测试。
///
/// @author linqibin
/// @since 0.7.0
@DisplayName("Venue 值对象 JSON 兼容性")
class VenueValueObjectJsonTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Nested
  @DisplayName("OpenAccessInfo")
  class OpenAccessInfoJsonTests {

    @Test
    @DisplayName("序列化时不应包含派生字段")
    void shouldNotSerializeDerivedFields() {
      OpenAccessInfo info =
          OpenAccessInfo.of(
              true, true, "gold", 3000, List.of(OpenAccessInfo.ApcPrice.of(3000, "USD")));

      JsonNode node = objectMapper.valueToTree(info);

      assertThat(node.has("goldOa")).isFalse();
      assertThat(node.has("greenOa")).isFalse();
      assertThat(node.has("hybridOa")).isFalse();
      assertThat(node.has("bronzeOa")).isFalse();
      assertThat(node.has("diamondOa")).isFalse();
    }

    @Test
    @DisplayName("反序列化应忽略历史未知字段")
    void shouldIgnoreUnknownFieldsOnDeserialization() throws Exception {
      String json =
          "{"
              + "\"isOa\":true,"
              + "\"isInDoaj\":false,"
              + "\"oaType\":\"gold\","
              + "\"apcUsd\":3000,"
              + "\"apcPrices\":[{\"price\":3000,\"currency\":\"USD\"}],"
              + "\"goldOa\":true"
              + "}";

      assertThatCode(() -> objectMapper.readValue(json, OpenAccessInfo.class))
          .doesNotThrowAnyException();

      OpenAccessInfo info = objectMapper.readValue(json, OpenAccessInfo.class);
      assertThat(info.isOa()).isTrue();
      assertThat(info.isInDoaj()).isFalse();
      assertThat(info.oaType()).isEqualTo("gold");
      assertThat(info.apcUsd()).isEqualTo(3000);
      assertThat(info.apcPrices()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("PublicationProfile")
  class PublicationProfileJsonTests {

    @Test
    @DisplayName("序列化时不应包含派生字段")
    void shouldNotSerializeDerivedFields() {
      PublicationProfile profile = buildPublicationProfile();

      JsonNode node = objectMapper.valueToTree(profile);

      assertThat(node.has("ceased")).isFalse();
      assertThat(node.has("currentlyIndexed")).isFalse();
      assertThat(node.has("englishJournal")).isFalse();
      assertThat(node.has("chineseJournal")).isFalse();
      assertThat(node.has("startYear")).isFalse();
      assertThat(node.has("endYear")).isFalse();
      assertThat(node.has("medlineTa")).isFalse();
      assertThat(node.has("isoAbbreviation")).isFalse();
      assertThat(node.has("mainLanguage")).isFalse();
      assertThat(node.has("hostOrganizationName")).isFalse();
      assertThat(node.has("hostOrganizationId")).isFalse();

      JsonNode historyNode = node.path("publicationHistory");
      assertThat(historyNode.has("active")).isFalse();

      JsonNode languagesNode = node.path("languages");
      assertThat(languagesNode.has("mainLanguage")).isFalse();
      assertThat(languagesNode.has("english")).isFalse();
      assertThat(languagesNode.has("chinese")).isFalse();
      assertThat(languagesNode.has("empty")).isFalse();
      assertThat(languagesNode.has("allLanguages")).isFalse();

      JsonNode hostNode = node.path("hostOrganization");
      assertThat(hostNode.has("topParentId")).isFalse();

      JsonNode indexingNode = node.path("indexingInfo");
      assertThat(indexingNode.has("currentlyIndexed")).isFalse();
      assertThat(indexingNode.has("discontinued")).isFalse();
      assertThat(indexingNode.has("neverIndexed")).isFalse();
      assertThat(indexingNode.has("preferredAbbreviation")).isFalse();
    }

    @Test
    @DisplayName("反序列化应忽略嵌套与根部未知字段")
    void shouldIgnoreUnknownFieldsOnDeserialization() throws Exception {
      String json =
          "{"
              + "\"abbreviatedTitle\":\"Nat. Med.\","
              + "\"alternateTitles\":[\"Nature Medicine\"],"
              + "\"frequency\":\"Monthly\","
              + "\"publicationHistory\":{"
              + "\"startYear\":1990,"
              + "\"endYear\":2020,"
              + "\"ceased\":true,"
              + "\"active\":false"
              + "},"
              + "\"languages\":{"
              + "\"primary\":[\"eng\"],"
              + "\"summary\":[\"chi\"],"
              + "\"mainLanguage\":\"eng\","
              + "\"english\":true,"
              + "\"chinese\":false"
              + "},"
              + "\"hostOrganization\":{"
              + "\"id\":\"I123\","
              + "\"name\":\"Example Org\","
              + "\"lineage\":[\"I123\"],"
              + "\"topParentId\":\"I123\""
              + "},"
              + "\"countryCode\":\"US\","
              + "\"indexingInfo\":{"
              + "\"status\":\"C\","
              + "\"medlineTa\":\"Nature\","
              + "\"isoAbbreviation\":\"Nat.\","
              + "\"currentlyIndexed\":true,"
              + "\"preferredAbbreviation\":\"Nature\""
              + "},"
              + "\"extData\":{\"extra\":\"x\"},"
              + "\"ceased\":true,"
              + "\"startYear\":1990"
              + "}";

      assertThatCode(() -> objectMapper.readValue(json, PublicationProfile.class))
          .doesNotThrowAnyException();

      PublicationProfile profile = objectMapper.readValue(json, PublicationProfile.class);
      assertThat(profile.abbreviatedTitle()).isEqualTo("Nat. Med.");
      assertThat(profile.countryCode()).isEqualTo("US");
      assertThat(profile.publicationHistory()).isNotNull();
      assertThat(profile.publicationHistory().startYear()).isEqualTo(1990);
      assertThat(profile.languages()).isNotNull();
      assertThat(profile.languages().primary()).contains("eng");
      assertThat(profile.hostOrganization()).isNotNull();
      assertThat(profile.hostOrganization().id()).isEqualTo("I123");
      assertThat(profile.indexingInfo()).isNotNull();
      assertThat(profile.indexingInfo().status()).isEqualTo("C");
      assertThat(profile.extData()).containsEntry("extra", "x");
    }
  }

  private PublicationProfile buildPublicationProfile() {
    PublicationHistory history = PublicationHistory.of(1990, 2020, true);
    VenueLanguages languages = VenueLanguages.of(List.of("eng"), List.of("chi"));
    HostOrganization hostOrganization =
        HostOrganization.of("I123", "Example Org", List.of("I123", "I456"));
    IndexingInfo indexingInfo = IndexingInfo.of("C", "Nature", "Nat.");

    return PublicationProfile.builder()
        .abbreviatedTitle("Nat. Med.")
        .alternateTitles(List.of("Nature Medicine"))
        .frequency("Monthly")
        .publicationHistory(history)
        .languages(languages)
        .hostOrganization(hostOrganization)
        .countryCode("US")
        .indexingInfo(indexingInfo)
        .extData(Map.of("extra", "x"))
        .build();
  }
}
