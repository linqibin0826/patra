package com.patra.catalog.infra.batch.publication;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.enums.DatePrecision;
import com.patra.catalog.domain.model.enums.PublicationDateType;
import com.patra.catalog.domain.model.enums.TranslationType;
import com.patra.catalog.domain.model.vo.publication.PublicationCompleteData;
import com.patra.catalog.domain.model.vo.publication.PublicationDate;
import com.patra.catalog.domain.model.vo.publication.PublicationFunding;
import com.patra.catalog.domain.model.vo.publication.PublicationId;
import com.patra.catalog.domain.model.vo.publication.PublicationInvestigator;
import com.patra.catalog.domain.model.vo.publication.PublicationKeyword;
import com.patra.catalog.domain.model.vo.publication.PublicationMeshHeading;
import com.patra.catalog.domain.model.vo.publication.PublicationPersonalNameSubject;
import com.patra.catalog.domain.model.vo.publication.PublicationSupplMesh;
import com.patra.catalog.domain.model.vo.publication.PublicationTypeInfo;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.AlternativeAbstractData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.FundingData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.InvestigatorData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.KeywordData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.MeshHeadingData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.PersonalNameSubjectData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.PublicationDateData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.PublicationTypeData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.QualifierData;
import com.patra.catalog.infra.batch.publication.PublicationImportResult.SupplMeshData;
import com.patra.common.enums.ProvenanceCode;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// PublicationImportResultMapper 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationImportResultMapper")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class PublicationImportResultMapperTest {

  private PublicationImportResultMapper mapper;

  private static final Long PUBLICATION_ID = 1001L;

  @BeforeEach
  void setUp() {
    mapper = new PublicationImportResultMapper();
  }

  @Nested
  @DisplayName("toCompleteData()")
  class ToCompleteDataTest {

    @Test
    @DisplayName("应该正确转换完整的 ImportResult")
    void should_convert_complete_import_result() {
      // given
      PublicationAggregate pub = createPublication("12345678");
      pub.assignId(PublicationId.of(PUBLICATION_ID));

      MeshHeadingData meshHeading =
          MeshHeadingData.of("D000001", true, 1, List.of(QualifierData.of("Q000379", false, 1)));
      KeywordData keyword = KeywordData.of("author", "machine learning", true, 1);
      FundingData funding =
          FundingData.builder()
              .organizationId(1001L)
              .grantId("R01AI123456")
              .funderNameRaw("NIH")
              .fundingOrder(1)
              .provenanceCode("PUBMED")
              .build();
      PublicationTypeData pubType = PublicationTypeData.of("D016428", "Journal Article", "MeSH", 1);
      SupplMeshData supplMesh = SupplMeshData.of("C538003", 1);
      AlternativeAbstractData altAbstract =
          AlternativeAbstractData.of("zh", "Publisher", "中文摘要", null, 1);
      PublicationDateData date = PublicationDateData.of("published", 2024, 3, 15, 1);
      InvestigatorData investigator =
          InvestigatorData.builder()
              .lastName("Smith")
              .foreName("John")
              .orcid("0000-0001-2345-6789")
              .dedupKey("abc123")
              .orderNum(1)
              .build();
      PersonalNameSubjectData personalNameSubject =
          PersonalNameSubjectData.builder()
              .lastName("Darwin")
              .foreName("Charles")
              .dates("1809-1882")
              .orderNum(1)
              .build();

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(pub)
              .meshHeadings(List.of(meshHeading))
              .keywords(List.of(keyword))
              .funding(List.of(funding))
              .publicationTypes(List.of(pubType))
              .supplMeshNames(List.of(supplMesh))
              .alternativeAbstracts(List.of(altAbstract))
              .dates(List.of(date))
              .investigators(List.of(investigator))
              .personalNameSubjects(List.of(personalNameSubject))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.publication()).isEqualTo(pub);
      assertThat(data.meshHeadings()).hasSize(1);
      assertThat(data.keywords()).hasSize(1);
      assertThat(data.funding()).hasSize(1);
      assertThat(data.publicationTypes()).hasSize(1);
      assertThat(data.supplMeshList()).hasSize(1);
      assertThat(data.alternativeAbstracts()).hasSize(1);
      assertThat(data.dates()).hasSize(1);
      assertThat(data.investigators()).hasSize(1);
      assertThat(data.personalNameSubjects()).hasSize(1);
    }

    @Test
    @DisplayName("空集合输入应该返回空列表")
    void should_return_empty_lists_for_empty_input() {
      // given
      PublicationAggregate pub = createPublication("12345678");
      PublicationImportResult result = PublicationImportResult.ofPublication(pub);

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.meshHeadings()).isEmpty();
      assertThat(data.keywords()).isEmpty();
      assertThat(data.funding()).isEmpty();
      assertThat(data.publicationTypes()).isEmpty();
      assertThat(data.supplMeshList()).isEmpty();
      assertThat(data.alternativeAbstracts()).isEmpty();
      assertThat(data.dates()).isEmpty();
      assertThat(data.investigators()).isEmpty();
      assertThat(data.personalNameSubjects()).isEmpty();
    }
  }

  @Nested
  @DisplayName("MeSH 转换")
  class MeshConversionTest {

    @Test
    @DisplayName("应该正确转换 MeSH 标引及其限定词")
    void should_convert_mesh_heading_with_qualifiers() {
      // given
      QualifierData qualifier1 = QualifierData.of("Q000379", true, 1);
      QualifierData qualifier2 = QualifierData.of("Q000188", false, 2);
      MeshHeadingData heading =
          MeshHeadingData.of("D000001", true, 1, List.of(qualifier1, qualifier2));

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .meshHeadings(List.of(heading))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.meshHeadings()).hasSize(1);
      PublicationMeshHeading meshHeading = data.meshHeadings().getFirst();
      assertThat(meshHeading.descriptorUi()).isEqualTo("D000001");
      assertThat(meshHeading.majorTopic()).isTrue();
      assertThat(meshHeading.headingOrder()).isEqualTo(1);
      assertThat(meshHeading.qualifiers()).hasSize(2);
      assertThat(meshHeading.qualifiers().get(0).qualifierUi()).isEqualTo("Q000379");
      assertThat(meshHeading.qualifiers().get(0).majorTopic()).isTrue();
    }

    @Test
    @DisplayName("无限定词的 MeSH 标引应该返回空限定词列表")
    void should_return_empty_qualifiers_when_none() {
      // given
      MeshHeadingData heading = MeshHeadingData.of("D000001", false, 1, null);

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .meshHeadings(List.of(heading))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.meshHeadings().getFirst().qualifiers()).isEmpty();
    }
  }

  @Nested
  @DisplayName("关键词转换")
  class KeywordConversionTest {

    @Test
    @DisplayName("应该正确转换关键词")
    void should_convert_keyword() {
      // given
      KeywordData keyword = KeywordData.of("author", "machine learning", true, 1);

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .keywords(List.of(keyword))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.keywords()).hasSize(1);
      PublicationKeyword kw = data.keywords().getFirst();
      assertThat(kw.source()).isEqualTo("author");
      assertThat(kw.term()).isEqualTo("machine learning");
      assertThat(kw.majorTopic()).isTrue();
      assertThat(kw.keywordOrder()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("资助信息转换")
  class FundingConversionTest {

    @Test
    @DisplayName("应该正确转换资助信息")
    void should_convert_funding() {
      // given
      FundingData funding =
          FundingData.builder()
              .organizationId(1001L)
              .grantId("R01AI123456")
              .funderNameRaw("National Institutes of Health")
              .funderAcronymRaw("NIH")
              .funderIdentifierRaw("100000002")
              .countryRaw("US")
              .fundingOrder(1)
              .provenanceCode("PUBMED")
              .build();

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .funding(List.of(funding))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.funding()).hasSize(1);
      PublicationFunding f = data.funding().getFirst();
      assertThat(f.organizationId()).isEqualTo(1001L);
      assertThat(f.grantId()).isEqualTo("R01AI123456");
      assertThat(f.funderNameRaw()).isEqualTo("National Institutes of Health");
      assertThat(f.funderAcronymRaw()).isEqualTo("NIH");
      assertThat(f.provenanceCode()).isEqualTo("PUBMED");
    }
  }

  @Nested
  @DisplayName("出版类型转换")
  class PublicationTypeConversionTest {

    @Test
    @DisplayName("应该正确转换出版类型")
    void should_convert_publication_type() {
      // given
      PublicationTypeData pubType = PublicationTypeData.of("D016428", "Journal Article", "MeSH", 1);

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .publicationTypes(List.of(pubType))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.publicationTypes()).hasSize(1);
      PublicationTypeInfo type = data.publicationTypes().getFirst();
      assertThat(type.typeId()).isEqualTo("D016428");
      assertThat(type.typeValue()).isEqualTo("Journal Article");
      assertThat(type.vocabularySource()).isEqualTo("MeSH");
    }
  }

  @Nested
  @DisplayName("补充 MeSH 转换")
  class SupplMeshConversionTest {

    @Test
    @DisplayName("应该正确转换补充 MeSH 概念")
    void should_convert_suppl_mesh() {
      // given
      SupplMeshData suppl = SupplMeshData.of("C538003", 1);

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .supplMeshNames(List.of(suppl))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.supplMeshList()).hasSize(1);
      PublicationSupplMesh sm = data.supplMeshList().getFirst();
      assertThat(sm.scrUi()).isEqualTo("C538003");
      assertThat(sm.supplOrder()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("翻译摘要转换")
  class AlternativeAbstractConversionTest {

    @Test
    @DisplayName("Publisher 类型应该映射为 OFFICIAL")
    void should_map_publisher_to_official() {
      // given
      AlternativeAbstractData altAbstract =
          AlternativeAbstractData.of("zh", "Publisher", "中文摘要", null, 1);

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .alternativeAbstracts(List.of(altAbstract))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.alternativeAbstracts()).hasSize(1);
      assertThat(data.alternativeAbstracts().getFirst().translationType())
          .isEqualTo(TranslationType.OFFICIAL);
      assertThat(data.alternativeAbstracts().getFirst().isOfficial()).isTrue();
      assertThat(data.alternativeAbstracts().getFirst().sourceType()).isEqualTo("publisher");
    }

    @ParameterizedTest(name = "[{index}] abstractType=\"{0}\" 应该映射为 PROFESSIONAL")
    @ValueSource(
        strings = {
          "plain-language-summary",
          "Plain-Language-Summary",
          "AIMSHP",
          "aimshp",
          "KIEML",
          "kieml",
          "NASA",
          "nasa"
        })
    @DisplayName("专业机构类型应该映射为 PROFESSIONAL")
    void should_map_professional_types(String abstractType) {
      // given
      AlternativeAbstractData altAbstract =
          AlternativeAbstractData.of("en", abstractType, "Professional abstract", null, 1);

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .alternativeAbstracts(List.of(altAbstract))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.alternativeAbstracts().getFirst().translationType())
          .isEqualTo(TranslationType.PROFESSIONAL);
      assertThat(data.alternativeAbstracts().getFirst().sourceType())
          .isEqualTo(abstractType.trim().toLowerCase(Locale.ROOT));
    }

    @ParameterizedTest(name = "[{index}] abstractType=\"{0}\" 应该映射为 OFFICIAL (默认)")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "Unknown", "Other"})
    @DisplayName("空值或未知类型应该映射为 OFFICIAL")
    void should_map_null_or_unknown_to_official(String abstractType) {
      // given
      AlternativeAbstractData altAbstract =
          AlternativeAbstractData.of("en", abstractType, "Some abstract", null, 1);

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .alternativeAbstracts(List.of(altAbstract))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.alternativeAbstracts().getFirst().translationType())
          .isEqualTo(TranslationType.OFFICIAL);
      String expectedSourceType =
          (abstractType == null || abstractType.trim().isEmpty())
              ? "unknown"
              : abstractType.trim().toLowerCase(Locale.ROOT);
      assertThat(data.alternativeAbstracts().getFirst().sourceType()).isEqualTo(expectedSourceType);
    }
  }

  @Nested
  @DisplayName("日期转换")
  class DateConversionTest {

    @Test
    @DisplayName("应该正确转换完整日期")
    void should_convert_complete_date() {
      // given
      PublicationDateData date =
          PublicationDateData.builder()
              .dateType("published")
              .year(2024)
              .month(3)
              .day(15)
              .datePrecision("day")
              .isPrimary(true)
              .orderNum(1)
              .build();

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .dates(List.of(date))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.dates()).hasSize(1);
      PublicationDate d = data.dates().getFirst();
      assertThat(d.dateType()).isEqualTo(PublicationDateType.PUBLISHED);
      assertThat(d.year()).isEqualTo(2024);
      assertThat(d.month()).isEqualTo(3);
      assertThat(d.day()).isEqualTo(15);
      assertThat(d.datePrecision()).isEqualTo(DatePrecision.DAY);
      assertThat(d.isPrimary()).isTrue();
    }

    @ParameterizedTest(name = "[{index}] dateType=\"{0}\" 应该映射为 {1}")
    @CsvSource({
      "published, PUBLISHED",
      "received, RECEIVED",
      "accepted, ACCEPTED",
      "revised, REVISED",
      "epublish, EPUBLISH"
    })
    @DisplayName("已知日期类型应该正确映射")
    void should_map_known_date_types(String dateType, PublicationDateType expected) {
      // given
      PublicationDateData date =
          PublicationDateData.builder()
              .dateType(dateType)
              .year(2024)
              .datePrecision("year")
              .orderNum(1)
              .build();

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .dates(List.of(date))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.dates().getFirst().dateType()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] dateType=\"{0}\" 应该映射为 OTHER")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "unknown", "invalid_type"})
    @DisplayName("无效日期类型应该映射为 OTHER")
    void should_map_invalid_date_type_to_other(String dateType) {
      // given
      PublicationDateData date =
          PublicationDateData.builder()
              .dateType(dateType)
              .year(2024)
              .datePrecision("year")
              .orderNum(1)
              .build();

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .dates(List.of(date))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.dates().getFirst().dateType()).isEqualTo(PublicationDateType.OTHER);
    }

    @ParameterizedTest(name = "[{index}] precision=\"{0}\" 应该映射为 YEAR (默认)")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "invalid_precision"})
    @DisplayName("无效日期精度应该映射为 YEAR")
    void should_map_invalid_precision_to_year(String precision) {
      // given
      PublicationDateData date =
          PublicationDateData.builder()
              .dateType("published")
              .year(2024)
              .datePrecision(precision)
              .orderNum(1)
              .build();

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .dates(List.of(date))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.dates().getFirst().datePrecision()).isEqualTo(DatePrecision.YEAR);
    }
  }

  @Nested
  @DisplayName("研究者转换")
  class InvestigatorConversionTest {

    @Test
    @DisplayName("应该正确转换研究者")
    void should_convert_investigator() {
      // given
      InvestigatorData investigator =
          InvestigatorData.builder()
              .lastName("Smith")
              .foreName("John")
              .initials("J.S.")
              .suffix("MD")
              .orcid("0000-0001-2345-6789")
              .affiliationName("Harvard Medical School")
              .dedupKey("abc123def456")
              .orderNum(1)
              .build();

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .investigators(List.of(investigator))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.investigators()).hasSize(1);
      PublicationInvestigator inv = data.investigators().getFirst();
      assertThat(inv.lastName()).isEqualTo("Smith");
      assertThat(inv.foreName()).isEqualTo("John");
      assertThat(inv.initials()).isEqualTo("J.S.");
      assertThat(inv.suffix()).isEqualTo("MD");
      assertThat(inv.orcid()).isEqualTo("0000-0001-2345-6789");
      assertThat(inv.affiliationName()).isEqualTo("Harvard Medical School");
      assertThat(inv.dedupKey()).isEqualTo("abc123def456");
    }
  }

  @Nested
  @DisplayName("人物主题转换")
  class PersonalNameSubjectConversionTest {

    @Test
    @DisplayName("应该正确转换人物主题")
    void should_convert_personal_name_subject() {
      // given
      PersonalNameSubjectData subject =
          PersonalNameSubjectData.builder()
              .lastName("Darwin")
              .foreName("Charles")
              .initials("C.R.")
              .suffix("FRS")
              .dates("1809-1882")
              .description("British naturalist")
              .subjectType("biography")
              .identifier("viaf:27063124")
              .orderNum(1)
              .build();

      PublicationImportResult result =
          PublicationImportResult.builder()
              .publication(createPublication("12345678"))
              .personalNameSubjects(List.of(subject))
              .build();

      // when
      PublicationCompleteData data = mapper.toCompleteData(result);

      // then
      assertThat(data.personalNameSubjects()).hasSize(1);
      PublicationPersonalNameSubject pns = data.personalNameSubjects().getFirst();
      assertThat(pns.lastName()).isEqualTo("Darwin");
      assertThat(pns.foreName()).isEqualTo("Charles");
      assertThat(pns.dates()).isEqualTo("1809-1882");
      assertThat(pns.description()).isEqualTo("British naturalist");
      assertThat(pns.subjectType()).isEqualTo("biography");
      assertThat(pns.identifier()).isEqualTo("viaf:27063124");
    }
  }

  /// 创建测试用的 PublicationAggregate。
  private PublicationAggregate createPublication(String pmid) {
    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        pmid,
        null,
        VenueId.of(1L),
        VenueInstanceId.of(100L),
        "Test Article " + pmid,
        null,
        null,
        null,
        null,
        2024,
        true,
        null,
        null);
  }
}
