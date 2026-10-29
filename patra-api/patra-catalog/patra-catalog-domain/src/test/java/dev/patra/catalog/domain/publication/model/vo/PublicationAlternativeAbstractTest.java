package dev.patra.catalog.domain.publication.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.publication.model.enums.TranslationType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// PublicationAlternativeAbstract 值对象单元测试。
///
/// @author Patra Lin
/// @since 0.6.0
@DisplayName("PublicationAlternativeAbstract 值对象")
class PublicationAlternativeAbstractTest {

  @Test
  @DisplayName("sourceType 应该自动 trim 并转为小写")
  void shouldNormalizeSourceTypeToLowerCase() {
    PublicationAlternativeAbstract value =
        PublicationAlternativeAbstract.builder()
            .languageCode("fr")
            .sourceType("  Plain-Language-Summary  ")
            .plainText("法语摘要")
            .translationType(TranslationType.PROFESSIONAL)
            .build();

    assertThat(value.sourceType()).isEqualTo("plain-language-summary");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  @DisplayName("sourceType 为空时应回退为 unknown")
  void shouldFallbackSourceTypeToUnknown(String sourceType) {
    PublicationAlternativeAbstract value =
        PublicationAlternativeAbstract.builder()
            .languageCode("en")
            .sourceType(sourceType)
            .plainText("summary")
            .translationType(TranslationType.OFFICIAL)
            .isOfficial(true)
            .build();

    assertThat(value.sourceType()).isEqualTo("unknown");
  }

  @Test
  @DisplayName("工厂方法应设置默认 sourceType")
  void factoryMethodsShouldSetDefaultSourceType() {
    PublicationAlternativeAbstract official =
        PublicationAlternativeAbstract.ofOfficial("zh-CN", "Chinese", "官方摘要");
    PublicationAlternativeAbstract professional =
        PublicationAlternativeAbstract.ofProfessional("ja", "Japanese", "专业摘要", "translator");
    PublicationAlternativeAbstract machine =
        PublicationAlternativeAbstract.ofMachine("de", "German", "机器摘要");

    assertThat(official.sourceType()).isEqualTo("publisher");
    assertThat(professional.sourceType()).isEqualTo("professional");
    assertThat(machine.sourceType()).isEqualTo("machine");
  }

  @Test
  @DisplayName("段落列表应做防御性拷贝并保持顺序")
  void shouldDefensivelyCopySections() {
    List<PublicationAbstractSection> mutable = new ArrayList<>();
    mutable.add(PublicationAbstractSection.of("背景", "第一段"));
    mutable.add(PublicationAbstractSection.of(null, "第二段"));

    PublicationAlternativeAbstract value =
        PublicationAlternativeAbstract.builder()
            .languageCode("zh-CN")
            .structuredSections(mutable)
            .build();
    mutable.clear();

    assertThat(value.structuredSections()).hasSize(2);
    assertThat(value.structuredSections().get(1).text()).isEqualTo("第二段");
    assertThat(value.hasStructuredSections()).isTrue();
    assertThat(value.getFullText()).isEqualTo("第一段 第二段");
    assertThat(value.findSectionsByLabel("背景")).hasSize(1);
    // 入参首尾空白应被 trim 后再匹配
    assertThat(value.findSectionsByLabel("  背景 ")).hasSize(1);
  }

  @Test
  @DisplayName("copyright 应原样透传")
  void shouldCarryCopyright() {
    PublicationAlternativeAbstract value =
        PublicationAlternativeAbstract.builder()
            .languageCode("ja")
            .plainText("翻译摘要")
            .copyright("©2026 Publisher")
            .build();

    assertThat(value.copyright()).isEqualTo("©2026 Publisher");
  }
}
