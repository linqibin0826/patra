package dev.patra.catalog.infra.publication.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.patra.catalog.domain.kernel.model.vo.VenueId;
import dev.patra.catalog.domain.portal.model.read.PublicationDetailReadModel;
import dev.patra.catalog.domain.portal.model.read.PublicationDetailReadModel.AbstractSectionView;
import dev.patra.catalog.domain.publication.model.aggregate.PublicationAggregate;
import dev.patra.catalog.domain.publication.model.enums.PublicationMedium;
import dev.patra.catalog.domain.publication.model.enums.PublicationStatus;
import dev.patra.catalog.domain.publication.model.enums.TranslationType;
import dev.patra.catalog.domain.publication.model.vo.LanguageInfo;
import dev.patra.catalog.domain.publication.model.vo.PublicationAbstract;
import dev.patra.catalog.domain.publication.model.vo.PublicationAbstractSection;
import dev.patra.catalog.domain.publication.model.vo.PublicationAlternativeAbstract;
import dev.patra.catalog.domain.publication.model.vo.PublicationCompleteData;
import dev.patra.catalog.domain.publication.model.vo.VenueInstanceId;
import dev.patra.catalog.infra.author.persistence.AuthorOrcidReadAdapter;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.portal.persistence.PublicationDetailReadAdapter;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationAbstractDao;
import dev.patra.catalog.infra.publication.persistence.dao.PublicationAlternativeAbstractDao;
import dev.patra.catalog.infra.publication.persistence.entity.PublicationAbstractEntity;
import dev.patra.catalog.infra.publication.persistence.entity.PublicationAlternativeAbstractEntity;
import dev.patra.common.ProvenanceCode;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import dev.patra.tool.jpa.json.Jackson3JsonFormatMapper;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/// 摘要写读闭环集成测试（真实写路径 → 库内 jsonb 形态 → 双读路径恢复）。
///
/// **测试目标**（v0.5 schema 不一致的漏网根因是 IT 手造数组数据、从未走真实写路径）：
///
/// - 写入必须经 `PublicationRepositoryAdapter.insertAllWithAssociations`（生产写路径），
///   而非测试内手工构造 Entity
/// - 库内形态用 native query 断言：`jsonb_typeof` 为 `array`、元素 key 集合恰为 `{label, text}`
/// - 读路径两条都要证：Entity → Domain（Hibernate FormatMapper 反序列化）
///   与 `structured_sections::text` → `PublicationDetailReadAdapter`（Jackson 3 ObjectMapper）
///
/// **为什么显式 `@Import(PatraHibernatePropertiesCustomizer.class)`**：
///
/// `@DataJpaTest` 只加载白名单内的自动配置，starter-jpa 的 `JpaAutoConfiguration`
/// 不在其中——实测默认切片下 Hibernate 挑的是内置的
/// `org.hibernate.type.format.jackson.JacksonJsonFormatMapper`（Jackson 2 链路），
/// 与生产的 `Jackson3JsonFormatMapper` 不是同一条，IT 便证不到生产的 wire format。
/// 显式导入该 `@Configuration` 把生产 FormatMapper 注入 Hibernate 属性
/// （`hibernate.type.json_format_mapper`），使 persist 走生产同款序列化。
/// 该前置由 `formatMapper_isProductionJackson3` 断言 SessionFactory 上的实例类型钉死——
/// 移除本 `@Import` 该用例即红。
///
/// @author linqibin
/// @since 0.6.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  PublicationRepositoryAdapter.class,
  AuthorOrcidReadAdapter.class,
  PublicationDetailReadAdapter.class,
  PatraHibernatePropertiesCustomizer.class,
  JpaAuditingConfiguration.class,
  JacksonAutoConfiguration.class,
  JsonColumnCodec.class
})
@ComponentScan(
    basePackages = {
      "dev.patra.catalog.infra.*.persistence.mapper",
      "dev.patra.catalog.infra.*.persistence.converter"
    })
@ActiveProfiles("test")
@DisplayName("文献摘要写读闭环集成测试")
class PublicationAbstractRoundTripIT {

  @Autowired private PublicationRepositoryAdapter adapter;
  @Autowired private PublicationDetailReadAdapter readAdapter;
  @Autowired private PublicationAbstractDao abstractDao;
  @Autowired private PublicationAlternativeAbstractDao alternativeAbstractDao;
  @Autowired private EntityManager em;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  @DisplayName("前置：Hibernate 使用生产同款 Jackson3JsonFormatMapper（非切片默认实现）")
  void formatMapper_isProductionJackson3() {
    assertThat(
            entityManagerFactory
                .unwrap(SessionFactoryImplementor.class)
                .getSessionFactoryOptions()
                .getJsonFormatMapper())
        .isInstanceOf(Jackson3JsonFormatMapper.class);
  }

  @Test
  @DisplayName("前置：Hibernate 写 jsonb 用的是容器 JsonMapper（容器里注册的序列化器会生效）")
  void formatMapper_usesContainerJsonMapper() {
    CountingSectionSerializer.CALLS.set(0);
    PublicationAggregate agg = buildAggregateWithVenue("91000009", "10.9100/abs-9");
    agg.attachAbstract(
        PublicationAbstract.ofBoth(
            "BACKGROUND: bg.", List.of(PublicationAbstractSection.of("BACKGROUND", "bg.")), "© X"));

    adapter.insertAllWithAssociations(List.of(PublicationCompleteData.ofPublication(agg)));
    em.flush();

    // 序列化器只登记在容器的 JsonMapper 上：被调用即证明 FormatMapper 持有的就是容器 bean
    assertThat(CountingSectionSerializer.CALLS.get()).isPositive();
  }

  @Test
  @DisplayName("结构化摘要：jsonb array 落库、顺序/重复 label/无 label 段保持、双读路径恢复")
  void structuredAbstract_fullMatrix() {
    List<PublicationAbstractSection> sections =
        List.of(
            PublicationAbstractSection.of("INTRODUCTION", "i."),
            PublicationAbstractSection.of("METHODS", "m1."),
            PublicationAbstractSection.of("METHODS", "m2."),
            PublicationAbstractSection.of(null, "mixed tail."));
    String plainText = "INTRODUCTION: i.\nMETHODS: m1.\nMETHODS: m2.\nmixed tail.";

    PublicationAggregate agg = buildAggregateWithVenue("91000001", "10.9100/abs-1");
    agg.attachAbstract(PublicationAbstract.ofBoth(plainText, sections, "© X"));

    adapter.insertAllWithAssociations(List.of(PublicationCompleteData.ofPublication(agg)));
    em.flush();
    em.clear();
    Long pubId = agg.getId().value();

    // 1) 库内 jsonb 形态：数组（对象形态 / 字符串形态都会红）
    assertThat(jsonbTypeOf("cat_publication_abstract", pubId)).isEqualTo("array");

    // 1b) 元素 key 集合恰为 {label, text}——jsonb_typeof 挡不住多余属性混入 wire format
    assertThat(elementKeysOf("cat_publication_abstract", pubId))
        .containsExactlyInAnyOrder("label", "text");

    // 2) Entity → Domain 读路径（Hibernate FormatMapper 反序列化 List<record>）
    PublicationAbstractEntity entity = abstractDao.findByPublicationId(pubId).orElseThrow();
    assertThat(entity.getStructuredSections())
        .extracting(PublicationAbstractSection::label, PublicationAbstractSection::text)
        .containsExactly(
            tuple("INTRODUCTION", "i."),
            tuple("METHODS", "m1."),
            tuple("METHODS", "m2."),
            tuple(null, "mixed tail."));
    assertThat(entity.getPlainText()).isEqualTo(plainText);
    assertThat(entity.getCopyright()).isEqualTo("© X");
    assertThat(entity.getAbstractType()).isEqualTo("STRUCTURED");

    // 3) native ::text → ReadAdapter 读路径（Jackson 3 ObjectMapper）
    PublicationDetailReadModel model = readAdapter.findById(pubId).orElseThrow();
    assertThat(model.abstractSections())
        .extracting(AbstractSectionView::label, AbstractSectionView::text)
        .containsExactly(
            tuple("INTRODUCTION", "i."),
            tuple("METHODS", "m1."),
            tuple("METHODS", "m2."),
            tuple(null, "mixed tail."));
    assertThat(model.abstractPlainText()).isEqualTo(entity.getPlainText());
    // 真实写路径写进列的是枚举大写 name，端到端只此一处能证
    assertThat(model.abstractType()).isEqualTo("STRUCTURED");
  }

  @Test
  @DisplayName("纯文本摘要：structured_sections 为 SQL NULL，plainText 完整")
  void plainAbstract_nullSections() {
    String plainText = "just plain text.";
    PublicationAggregate agg = buildAggregateWithVenue("91000002", "10.9100/abs-2");
    agg.attachAbstract(PublicationAbstract.ofPlainText(plainText, null));

    adapter.insertAllWithAssociations(List.of(PublicationCompleteData.ofPublication(agg)));
    em.flush();
    em.clear();
    Long pubId = agg.getId().value();

    // 空段落列表须落 SQL NULL，而非 `[]` / `"null"` 等伪空形态
    assertThat(structuredSectionsIsNull("cat_publication_abstract", pubId)).isTrue();

    PublicationAbstractEntity entity = abstractDao.findByPublicationId(pubId).orElseThrow();
    assertThat(entity.getStructuredSections()).isNull();
    assertThat(entity.getPlainText()).isEqualTo(plainText);
    assertThat(entity.getAbstractType()).isEqualTo("UNSTRUCTURED");

    PublicationDetailReadModel model = readAdapter.findById(pubId).orElseThrow();
    assertThat(model.abstractSections()).isEmpty();
    assertThat(model.abstractPlainText()).isEqualTo(plainText);
    assertThat(model.abstractType()).isEqualTo("UNSTRUCTURED");
  }

  @Test
  @DisplayName("翻译摘要：sections/copyright 同款闭环（jsonb array + key 集合 + Entity 恢复）")
  void alternativeAbstract_roundTrip() {
    PublicationAggregate agg = buildAggregateWithVenue("91000003", "10.9100/abs-3");
    PublicationAlternativeAbstract alt =
        PublicationAlternativeAbstract.builder()
            .languageCode("zh-CN")
            .languageName("Chinese")
            .plainText("目的: 摘要正文")
            .structuredSections(List.of(PublicationAbstractSection.of("目的", "摘要正文")))
            .copyright("© 中文版权")
            .sourceType("publisher")
            .translationType(TranslationType.OFFICIAL)
            .isOfficial(true)
            .orderNum(1)
            .build();

    adapter.insertAllWithAssociations(
        List.of(
            PublicationCompleteData.builder()
                .publication(agg)
                .alternativeAbstracts(List.of(alt))
                .build()));
    em.flush();
    em.clear();
    Long pubId = agg.getId().value();

    assertThat(jsonbTypeOf("cat_publication_alternative_abstract", pubId)).isEqualTo("array");
    assertThat(elementKeysOf("cat_publication_alternative_abstract", pubId))
        .containsExactlyInAnyOrder("label", "text");

    List<PublicationAlternativeAbstractEntity> entities =
        alternativeAbstractDao.findByPublicationId(pubId);
    assertThat(entities).hasSize(1);
    PublicationAlternativeAbstractEntity entity = entities.getFirst();
    assertThat(entity.getStructuredSections()).hasSize(1);
    assertThat(entity.getStructuredSections().getFirst().label()).isEqualTo("目的");
    assertThat(entity.getStructuredSections().getFirst().text()).isEqualTo("摘要正文");
    assertThat(entity.getPlainText()).isEqualTo("目的: 摘要正文");
    assertThat(entity.getCopyright()).isEqualTo("© 中文版权");
    assertThat(entity.getLanguageCode()).isEqualTo("zh-CN");
    assertThat(entity.getLanguageName()).isEqualTo("Chinese");
    assertThat(entity.getSourceType()).isEqualTo("publisher");
    assertThat(entity.getIsOfficial()).isTrue();
    // 枚举 → 列契约（大写 name）：全仓仅此一处覆盖
    assertThat(entity.getTranslationType()).isEqualTo("OFFICIAL");
    assertThat(entity.getOrderNum()).isEqualTo(1);
  }

  // ========== 测试助手 ==========

  /// 构造带载体的文献聚合根。
  ///
  /// @param pmid PMID
  /// @param doi DOI
  /// @return 文献聚合根
  private PublicationAggregate buildAggregateWithVenue(String pmid, String doi) {
    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        pmid,
        doi,
        VenueId.of(1001L),
        VenueInstanceId.of(2001L),
        "Abstract Round Trip " + pmid,
        null,
        LanguageInfo.of("English", "en-US"),
        PublicationStatus.PPUBLISH,
        PublicationMedium.PRINT,
        2024,
        true,
        0,
        null);
  }

  /// 查询指定表内某文献 `structured_sections` 列的 jsonb 类型。
  ///
  /// @param table 表名（`cat_publication_abstract` 或 `cat_publication_alternative_abstract`）
  /// @param publicationId 文献 ID
  /// @return `jsonb_typeof` 结果（如 `array` / `object` / `string`）
  private String jsonbTypeOf(String table, Long publicationId) {
    return (String)
        em.createNativeQuery(
                "SELECT jsonb_typeof(structured_sections) FROM "
                    + table
                    + " WHERE publication_id = :id")
            .setParameter("id", publicationId)
            .getSingleResult();
  }

  /// 查询指定表内某文献 `structured_sections` 数组元素的全部属性名（去重）。
  ///
  /// 数组展开前先用子查询锁定目标行，避免同表其他文献的段落混进 key 集合、
  /// 让「恰为 {label, text}」的断言被无关行毒化。
  ///
  /// @param table 表名（`cat_publication_abstract` 或 `cat_publication_alternative_abstract`）
  /// @param publicationId 文献 ID
  /// @return 元素属性名集合
  @SuppressWarnings("unchecked")
  private List<String> elementKeysOf(String table, Long publicationId) {
    return em.createNativeQuery(
            "SELECT DISTINCT jsonb_object_keys(elem) FROM jsonb_array_elements("
                + "(SELECT structured_sections FROM "
                + table
                + " WHERE publication_id = :id)) elem")
        .setParameter("id", publicationId)
        .getResultList();
  }

  /// 判断指定表内某文献的 `structured_sections` 列是否为 SQL NULL。
  ///
  /// @param table 表名（`cat_publication_abstract` 或 `cat_publication_alternative_abstract`）
  /// @param publicationId 文献 ID
  /// @return true 如果列值为 SQL NULL
  private Boolean structuredSectionsIsNull(String table, Long publicationId) {
    return (Boolean)
        em.createNativeQuery(
                "SELECT structured_sections IS NULL FROM " + table + " WHERE publication_id = :id")
            .setParameter("id", publicationId)
            .getSingleResult();
  }

  /// 给容器 `JsonMapper` 登记一个计数序列化器，用来证明 Hibernate 的 FormatMapper 用的就是容器 bean。
  @TestConfiguration
  static class ContainerMapperProbeConfig {

    /// 登记 `PublicationAbstractSection` 的计数序列化器，输出与默认序列化完全一致。
    ///
    /// @return 容器 `JsonMapper` 的构建定制器
    @Bean
    JsonMapperBuilderCustomizer countingSectionSerializerCustomizer() {
      return builder ->
          builder.addModule(new SimpleModule().addSerializer(new CountingSectionSerializer()));
    }
  }

  /// 输出与默认序列化一致（`label`、`text` 两个键）并记录调用次数的序列化器。
  static class CountingSectionSerializer extends StdSerializer<PublicationAbstractSection> {

    /// 序列化调用次数。
    static final AtomicInteger CALLS = new AtomicInteger();

    /// 创建序列化器。
    CountingSectionSerializer() {
      super(PublicationAbstractSection.class);
    }

    /// 写出 `label`、`text` 并计数。
    ///
    /// @param value 段落
    /// @param gen JSON 生成器
    /// @param ctxt 序列化上下文
    @Override
    public void serialize(
        PublicationAbstractSection value, JsonGenerator gen, SerializationContext ctxt) {
      CALLS.incrementAndGet();
      gen.writeStartObject();
      gen.writeStringProperty("label", value.label());
      gen.writeStringProperty("text", value.text());
      gen.writeEndObject();
    }
  }
}
