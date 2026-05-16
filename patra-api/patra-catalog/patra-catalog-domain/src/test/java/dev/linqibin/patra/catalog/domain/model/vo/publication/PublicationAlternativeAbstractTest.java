package dev.linqibin.patra.catalog.domain.model.vo.publication;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.catalog.domain.model.enums.TranslationType;
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
}
