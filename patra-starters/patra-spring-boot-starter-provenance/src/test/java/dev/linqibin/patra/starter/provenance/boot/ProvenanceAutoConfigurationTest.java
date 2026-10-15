package dev.linqibin.patra.starter.provenance.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.XmlMapperBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.xml.XmlMapper;

/// ProvenanceAutoConfiguration 自动配置测试：`XmlMapper` 由 Boot 装配，本 starter 只贡献定制器。
@DisplayName("ProvenanceAutoConfiguration 自动配置测试")
class ProvenanceAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class, ProvenanceAutoConfiguration.class))
          .withBean(RestClient.class, RestClient::create);

  /// 测试用的根元素：`item` 是不带外层包装元素的重复子元素。
  ///
  /// @param item 重复出现的子元素文本
  record UnwrappedItems(List<String> item) {}

  @Test
  @DisplayName("容器里只有 Boot 装配的一个 XmlMapper，本 starter 只注册定制器")
  void should_register_single_xml_mapper_and_provenance_customizer() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(XmlMapper.class);
          assertThat(context).hasBean("provenanceXmlMapperBuilderCustomizer");
          assertThat(context.getBean("provenanceXmlMapperBuilderCustomizer"))
              .isInstanceOf(XmlMapperBuilderCustomizer.class);
          assertThat(context).doesNotHaveBean("provenanceXmlMapper");
        });
  }

  @Test
  @DisplayName("XmlMapper 带上 PubMed 需要的宽松解析配置")
  void should_apply_lenient_pubmed_settings_to_xml_mapper() {
    contextRunner.run(
        context -> {
          XmlMapper xmlMapper = context.getBean(XmlMapper.class);

          assertThat(xmlMapper.rebuild().defaultUseWrapper()).isFalse();
          assertThat(xmlMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
              .isFalse();
          assertThat(xmlMapper.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT))
              .isTrue();
          assertThat(xmlMapper.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY))
              .isTrue();
          assertThat(xmlMapper.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES))
              .isTrue();
        });
  }

  @Test
  @DisplayName("不带包装元素的重复子元素能反序列化成 List")
  void should_read_unwrapped_repeated_elements_as_list() {
    contextRunner.run(
        context -> {
          XmlMapper xmlMapper = context.getBean(XmlMapper.class);

          UnwrappedItems result =
              xmlMapper.readValue(
                  "<root><item>a</item><item>b</item><unknown>x</unknown></root>",
                  UnwrappedItems.class);

          assertThat(result.item()).containsExactly("a", "b");
        });
  }

  @Test
  @DisplayName("XML 定制不影响容器里的 JsonMapper")
  void should_not_leak_xml_settings_into_json_mapper() {
    contextRunner.run(
        context -> {
          JsonMapper jsonMapper = context.getBean(JsonMapper.class);

          assertThat(jsonMapper.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES))
              .isFalse();
        });
  }
}
