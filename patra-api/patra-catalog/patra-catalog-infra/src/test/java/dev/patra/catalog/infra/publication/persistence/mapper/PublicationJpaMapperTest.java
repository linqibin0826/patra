package dev.patra.catalog.infra.publication.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.portal.model.read.PublicationDetailReadModel.AbstractSectionView;
import dev.patra.catalog.domain.publication.model.enums.AbstractType;
import dev.patra.catalog.domain.publication.model.vo.PublicationAbstract;
import dev.patra.catalog.domain.publication.model.vo.PublicationAbstractSection;
import dev.patra.catalog.domain.publication.model.vo.PublicationAlternativeAbstract;
import dev.patra.catalog.domain.publication.model.vo.PublicationMetadata;
import dev.patra.catalog.infra.publication.persistence.entity.PublicationAbstractEntity;
import dev.patra.catalog.infra.publication.persistence.entity.PublicationAlternativeAbstractEntity;
import dev.patra.catalog.infra.publication.persistence.entity.PublicationMetadataEntity;
import dev.patra.tool.jpa.json.Jackson3JsonFormatMapper;
import java.util.List;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.JavaTypeBasicAdaptor;
import org.hibernate.type.format.FormatMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/// PublicationJpaMapper 摘要映射单元测试。
///
/// 覆盖摘要段落类型化（`List<PublicationAbstractSection>` 直落 jsonb）后的映射语义：
/// Entity 字段不再承载 JSON 字符串，序列化由 Hibernate 的 JsonFormatMapper 单点完成。
@DisplayName("PublicationJpaMapper 摘要映射单元测试")
class PublicationJpaMapperTest {

  private final PublicationJpaMapper mapper = new PublicationJpaMapperImpl();

  @Nested
  @DisplayName("主摘要映射")
  class AbstractMapping {

    @Test
    @DisplayName("toAbstractEntity 应将段落列表原样落到 Entity（不做 JSON 序列化）")
    void shouldKeepSectionsAsListOnEntity() {
      List<PublicationAbstractSection> sections =
          List.of(
              PublicationAbstractSection.of("BACKGROUND", "背景内容"),
              PublicationAbstractSection.of(null, "无标签段"),
              PublicationAbstractSection.of("BACKGROUND", "重复标签段"));
      PublicationAbstract pubAbstract =
          PublicationAbstract.builder()
              .plainText("背景内容 无标签段 重复标签段")
              .structuredSections(sections)
              .copyright("© 2026 Publisher")
              .abstractType(AbstractType.STRUCTURED)
              .build();

      PublicationAbstractEntity entity = mapper.toAbstractEntity(pubAbstract, 100L);

      assertThat(entity.getStructuredSections()).containsExactlyElementsOf(sections);
      assertThat(entity.getCopyright()).isEqualTo("© 2026 Publisher");
    }

    @Test
    @DisplayName("toAbstractEntity 在段落为空时应落 null（SQL NULL）")
    void shouldWriteNullWhenSectionsEmpty() {
      PublicationAbstract pubAbstract =
          PublicationAbstract.builder()
              .plainText("纯文本摘要")
              .structuredSections(List.of())
              .abstractType(AbstractType.UNSTRUCTURED)
              .build();

      PublicationAbstractEntity entity = mapper.toAbstractEntity(pubAbstract, 100L);

      assertThat(entity.getStructuredSections()).isNull();
    }

    @Test
    @DisplayName("toAbstract 应从 Entity 读回段落列表与版权信息")
    void shouldReadSectionsBackFromEntity() {
      PublicationAbstractEntity entity = new PublicationAbstractEntity();
      entity.setPublicationId(100L);
      entity.setPlainText("背景内容");
      entity.setStructuredSections(List.of(PublicationAbstractSection.of("BACKGROUND", "背景内容")));
      entity.setCopyright("© 2026 Publisher");
      entity.setAbstractType(AbstractType.STRUCTURED.name());

      PublicationAbstract pubAbstract = mapper.toAbstract(entity);

      assertThat(pubAbstract.structuredSections())
          .containsExactly(PublicationAbstractSection.of("BACKGROUND", "背景内容"));
      assertThat(pubAbstract.copyright()).isEqualTo("© 2026 Publisher");
    }

    @Test
    @DisplayName("toAbstract 在 Entity 段落为 null 时应归一化为空列表")
    void shouldNormalizeNullSectionsToEmptyList() {
      PublicationAbstractEntity entity = new PublicationAbstractEntity();
      entity.setPublicationId(100L);
      entity.setPlainText("纯文本摘要");
      entity.setStructuredSections(null);
      entity.setAbstractType(AbstractType.UNSTRUCTURED.name());

      assertThat(mapper.toAbstract(entity).structuredSections()).isEmpty();
    }
  }

  @Nested
  @DisplayName("翻译摘要映射")
  class AlternativeAbstractMapping {

    @Test
    @DisplayName("toAlternativeAbstractEntity 应映射段落列表与版权信息")
    void shouldMapSectionsAndCopyright() {
      List<PublicationAbstractSection> sections =
          List.of(PublicationAbstractSection.of("目的", "探讨疗效"));
      PublicationAlternativeAbstract altAbstract =
          PublicationAlternativeAbstract.builder()
              .languageCode("zh")
              .sourceType("publisher")
              .plainText("探讨疗效")
              .structuredSections(sections)
              .copyright("© 2026 出版商")
              .build();

      PublicationAlternativeAbstractEntity entity =
          mapper.toAlternativeAbstractEntity(altAbstract, 100L);

      assertThat(entity.getStructuredSections()).containsExactlyElementsOf(sections);
      assertThat(entity.getCopyright()).isEqualTo("© 2026 出版商");
    }

    @Test
    @DisplayName("toAlternativeAbstract 在 Entity 段落为 null 时应归一化为空列表")
    void shouldNormalizeNullSectionsToEmptyList() {
      PublicationAlternativeAbstractEntity entity = new PublicationAlternativeAbstractEntity();
      entity.setPublicationId(100L);
      entity.setLanguageCode("zh");
      entity.setSourceType("publisher");
      entity.setStructuredSections(null);

      assertThat(mapper.toAlternativeAbstract(entity).structuredSections()).isEmpty();
    }

    @Test
    @DisplayName("toAlternativeAbstract 应读回段落列表与版权信息")
    void shouldReadSectionsAndCopyrightBack() {
      PublicationAlternativeAbstractEntity entity = new PublicationAlternativeAbstractEntity();
      entity.setPublicationId(100L);
      entity.setLanguageCode("zh");
      entity.setSourceType("publisher");
      entity.setStructuredSections(List.of(PublicationAbstractSection.of("目的", "探讨疗效")));
      entity.setCopyright("© 2026 出版商");

      PublicationAlternativeAbstract altAbstract = mapper.toAlternativeAbstract(entity);

      assertThat(altAbstract.structuredSections())
          .containsExactly(PublicationAbstractSection.of("目的", "探讨疗效"));
      assertThat(altAbstract.copyright()).isEqualTo("© 2026 出版商");
    }

    @Test
    @DisplayName("toAlternativeAbstractEntity 在段落为空时应落 null（SQL NULL）")
    void shouldWriteNullWhenSectionsEmpty() {
      PublicationAlternativeAbstract altAbstract =
          PublicationAlternativeAbstract.builder()
              .languageCode("zh")
              .sourceType("publisher")
              .plainText("纯文本翻译摘要")
              .build();

      PublicationAlternativeAbstractEntity entity =
          mapper.toAlternativeAbstractEntity(altAbstract, 100L);

      assertThat(entity.getStructuredSections()).isNull();
    }
  }

  @Nested
  @DisplayName("元数据列表字段映射")
  class MetadataListMapping {

    @Test
    @DisplayName("toMetadataEntity 应将验证错误与处理注释原样落到 Entity（不做 JSON 序列化）")
    void shouldKeepListsOnEntity() {
      PublicationMetadata metadata =
          PublicationMetadata.builder()
              .validationErrors(List.of("缺少标题", "年份无效"))
              .processingNotes(List.of("已回填"))
              .build();

      PublicationMetadataEntity entity = mapper.toMetadataEntity(metadata, 100L);

      assertThat(entity.getValidationErrors()).containsExactly("缺少标题", "年份无效");
      assertThat(entity.getProcessingNotes()).containsExactly("已回填");
    }

    @Test
    @DisplayName("toMetadataEntity 在列表为空时应落 null（SQL NULL）")
    void shouldWriteNullWhenListsEmpty() {
      PublicationMetadataEntity entity = mapper.toMetadataEntity(PublicationMetadata.empty(), 100L);

      assertThat(entity.getValidationErrors()).isNull();
      assertThat(entity.getProcessingNotes()).isNull();
    }

    @Test
    @DisplayName("toMetadata 应原样还原列表，列为 NULL 时得到空列表")
    void shouldRestoreListsAndDefaultToEmpty() {
      PublicationMetadataEntity entity = new PublicationMetadataEntity();
      entity.setValidationErrors(List.of("缺少标题"));

      PublicationMetadata metadata = mapper.toMetadata(entity);

      assertThat(metadata.validationErrors()).containsExactly("缺少标题");
      assertThat(metadata.processingNotes()).isEmpty();
    }
  }

  @Nested
  @DisplayName("jsonb wire format 回归锚")
  class WireFormat {

    /// 生产同款：`JpaAutoConfiguration` 把该实例注册为 Hibernate 的 `JSON_FORMAT_MAPPER`。
    private final FormatMapper formatMapper =
        new Jackson3JsonFormatMapper(JsonMapper.builder().build());

    /// `toString` 仅用 javaType 判定 String/Object 透传特例，段落列表走正常 Jackson 序列化。
    private final JavaType<List> listJavaType = new JavaTypeBasicAdaptor<>(List.class);

    @SuppressWarnings("unchecked")
    private String serialize(List<PublicationAbstractSection> sections) {
      return formatMapper.toString(sections, listJavaType, null);
    }

    @Test
    @DisplayName("段落序列化后的 key 集合恰为 label/text，不含派生属性")
    void shouldWriteExactlyLabelAndTextKeys() {
      String json =
          serialize(
              List.of(
                  PublicationAbstractSection.of("BACKGROUND", "bg"),
                  PublicationAbstractSection.of(null, "tail")));

      // 钉死 wire format：任何新增的 bean getter（如曾经的 isLabeled() → "labeled"）都会打破此断言，
      // 并把派生属性污染进库内 wire format（读端宽松反序列化不会报错，只能靠本断言拦截）。
      assertThat(json)
          .isEqualTo(
              "[{\"label\":\"BACKGROUND\",\"text\":\"bg\"},{\"label\":null,\"text\":\"tail\"}]");
    }

    @Test
    @DisplayName("序列化产物应能被读端还原为 AbstractSectionView")
    void shouldBeDeserializableByReadSide() {
      String json = serialize(List.of(PublicationAbstractSection.of("METHODS", "m")));

      // 读端 PublicationDetailReadAdapter 用 Spring 托管的 Jackson 3 mapper（宽松，未知属性被忽略），
      // 此处同款构造，只验证 label/text 能正确还原；派生属性泄漏由上面的 key 集合断言拦截。
      JsonMapper readSideMapper = JsonMapper.builder().build();

      List<AbstractSectionView> views =
          readSideMapper.readValue(json, new TypeReference<List<AbstractSectionView>>() {});

      assertThat(views).containsExactly(AbstractSectionView.of("METHODS", "m"));
    }
  }
}
