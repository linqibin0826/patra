package dev.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.model.enums.DisambiguationStatus;
import dev.patra.catalog.domain.model.enums.FullTextStatus;
import dev.patra.catalog.domain.model.read.portal.PublicationDetailReadModel;
import dev.patra.catalog.domain.model.vo.publication.EvidenceLevel;
import dev.patra.catalog.domain.model.vo.publication.PublicationAbstractSection;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.persistence.entity.AuthorEntity;
import dev.patra.catalog.infra.persistence.entity.MeshDescriptorEntity;
import dev.patra.catalog.infra.persistence.entity.PublicationAbstractEntity;
import dev.patra.catalog.infra.persistence.entity.PublicationAuthorAffiliationEntity;
import dev.patra.catalog.infra.persistence.entity.PublicationAuthorEntity;
import dev.patra.catalog.infra.persistence.entity.PublicationEntity;
import dev.patra.catalog.infra.persistence.entity.PublicationFullTextEntity;
import dev.patra.catalog.infra.persistence.entity.PublicationIdentifierEntity;
import dev.patra.catalog.infra.persistence.entity.PublicationMeshHeadingEntity;
import dev.patra.catalog.infra.persistence.entity.PublicationMetadataEntity;
import dev.patra.catalog.infra.persistence.entity.PublicationTypeEntity;
import dev.patra.catalog.infra.persistence.entity.VenueEntity;
import dev.patra.common.PublicationIdentifierType;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  PublicationDetailReadAdapter.class,
  JpaAuditingConfiguration.class,
  JacksonAutoConfiguration.class
})
@ActiveProfiles("test")
@DisplayName("PublicationDetailReadAdapter 文献详情查询集成测试")
class PublicationDetailReadAdapterIT {

  @Autowired private PublicationDetailReadAdapter adapter;
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("按 ID 查询文献详情，返回 present + 标量字段正确")
  void shouldReturnPublicationDetailWhenExists() {
    Long venueId = saveVenue("Nature");
    Long pubId = savePublication(venueId, "COVID-19 Treatment Study", "10.1000/xyz123", "12345678");
    saveAbstract(pubId, "structured", null, "This is a plain text abstract.");
    em.flush();
    em.clear();

    Optional<PublicationDetailReadModel> result = adapter.findById(pubId);

    assertThat(result).isPresent();
    PublicationDetailReadModel model = result.get();
    assertThat(model.title()).isEqualTo("COVID-19 Treatment Study");
    assertThat(model.doi()).isEqualTo("10.1000/xyz123");
    assertThat(model.pmid()).isEqualTo("12345678");
    assertThat(model.venueName()).isEqualTo("Nature");
    assertThat(model.publicationYear()).isEqualTo(2024);
    assertThat(model.abstractPlainText()).isEqualTo("This is a plain text abstract.");
    assertThat(model.fullTextUrl()).isNull();
  }

  @Test
  @DisplayName("结构化摘要按有序数组反序列化，顺序保持 BACKGROUND → METHODS → RESULTS")
  void shouldDeserializeStructuredSectionsInOrder() {
    Long venueId = saveVenue("NEJM");
    Long pubId = savePublication(venueId, "RCT Study", "10.1000/rct", "11111111");
    List<PublicationAbstractSection> sections =
        List.of(
            PublicationAbstractSection.of("BACKGROUND", "Background text."),
            PublicationAbstractSection.of("METHODS", "Methods text."),
            PublicationAbstractSection.of("RESULTS", "Results text."));
    saveAbstract(pubId, "structured", sections, null);
    em.flush();
    em.clear();

    PublicationDetailReadModel model = adapter.findById(pubId).orElseThrow();

    assertThat(model.abstractSections()).hasSize(3);
    assertThat(model.abstractSections().get(0).label()).isEqualTo("BACKGROUND");
    assertThat(model.abstractSections().get(1).label()).isEqualTo("METHODS");
    assertThat(model.abstractSections().get(2).label()).isEqualTo("RESULTS");
  }

  @Test
  @DisplayName("出版类型包含 RCT 时 evidenceLevel 为 RANDOMIZED_CONTROLLED_TRIAL")
  void shouldClassifyEvidenceLevelFromPublicationTypes() {
    Long venueId = saveVenue("Lancet");
    Long pubId = savePublication(venueId, "RCT Trial", "10.1000/lancet", "22222222");
    saveType(pubId, "Journal Article", 1);
    saveType(pubId, "Randomized Controlled Trial", 2);
    em.flush();
    em.clear();

    PublicationDetailReadModel model = adapter.findById(pubId).orElseThrow();

    assertThat(model.evidenceLevel()).isEqualTo(EvidenceLevel.RANDOMIZED_CONTROLLED_TRIAL);
    assertThat(model.primaryType()).isEqualTo("Journal Article");
  }

  @Test
  @DisplayName("作者列表按顺序返回，包含第一作者标记和机构归属")
  void shouldReturnAuthorsInOrder() {
    Long venueId = saveVenue("BMJ");
    Long pubId = savePublication(venueId, "Author Order Study", "10.1000/bmj", "33333333");
    Long authorId1 = saveAuthor("John Smith");
    Long authorId2 = saveAuthor("Jane Doe");
    Long pubAuthorId1 = savePublicationAuthor(pubId, authorId1, "John Smith", 1, true, false);
    Long pubAuthorId2 = savePublicationAuthor(pubId, authorId2, "Jane Doe", 2, false, true);
    saveAffiliation(pubAuthorId1, pubId, 1, "Harvard University");
    em.flush();
    em.clear();

    PublicationDetailReadModel model = adapter.findById(pubId).orElseThrow();

    assertThat(model.authors()).hasSize(2);
    assertThat(model.authors().get(0).name()).isEqualTo("John Smith");
    assertThat(model.authors().get(0).first()).isTrue();
    assertThat(model.authors().get(0).corresponding()).isFalse();
    assertThat(model.authors().get(0).affiliation()).isEqualTo("Harvard University");
    assertThat(model.authors().get(1).name()).isEqualTo("Jane Doe");
    assertThat(model.authors().get(1).corresponding()).isTrue();
  }

  @Test
  @DisplayName("MeSH 标引按 heading_order 顺序返回，含 term 名称")
  void shouldReturnMeshHeadingsInOrder() {
    Long venueId = saveVenue("JAMA");
    Long pubId = savePublication(venueId, "MeSH Study", "10.1000/jama", "44444444");
    saveMeshDescriptor("D000001", "Calcimycin");
    saveMeshDescriptor("D000002", "Temefos");
    saveMeshHeading(pubId, "D000001", false, 1);
    saveMeshHeading(pubId, "D000002", true, 2);
    em.flush();
    em.clear();

    PublicationDetailReadModel model = adapter.findById(pubId).orElseThrow();

    assertThat(model.meshHeadings()).hasSize(2);
    assertThat(model.meshHeadings().get(0).descriptorUi()).isEqualTo("D000001");
    assertThat(model.meshHeadings().get(0).term()).isEqualTo("Calcimycin");
    assertThat(model.meshHeadings().get(0).major()).isFalse();
    assertThat(model.meshHeadings().get(1).descriptorUi()).isEqualTo("D000002");
    assertThat(model.meshHeadings().get(1).major()).isTrue();
  }

  @Test
  @DisplayName("ID 不存在时返回 empty")
  void shouldReturnEmptyWhenNotFound() {
    Optional<PublicationDetailReadModel> result = adapter.findById(Long.MAX_VALUE);
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("软删除后 findById 返回 empty")
  void shouldReturnEmptyWhenSoftDeleted() {
    Long venueId = saveVenue("Annals");
    Long pubId = savePublication(venueId, "Deleted Paper", "10.1000/del", "55555555");
    em.flush();
    em.clear();

    PublicationEntity pub = em.find(PublicationEntity.class, pubId);
    em.remove(pub); // Hibernate @SoftDelete 自动转为 UPDATE deleted_at = now()
    em.flush();
    em.clear();

    Optional<PublicationDetailReadModel> result = adapter.findById(pubId);
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("无 pii 标识符时 pii() 返回 null")
  void shouldReturnNullPiiWhenNoIdentifier() {
    Long venueId = saveVenue("Cell");
    Long pubId = savePublication(venueId, "PII Test Paper", "10.1000/cell", "66666666");
    em.flush();
    em.clear();

    PublicationDetailReadModel model = adapter.findById(pubId).orElseThrow();

    assertThat(model.pii()).isNull();
  }

  @Test
  @DisplayName("存在 pii 标识符时 pii() 返回正确值")
  void shouldReturnPiiWhenIdentifierExists() {
    Long venueId = saveVenue("Science");
    Long pubId = savePublication(venueId, "PII Present Paper", "10.1000/sci", "77777777");
    saveIdentifier(pubId, "pii", "S0140-6736(21)00183-2");
    em.flush();
    em.clear();

    PublicationDetailReadModel model = adapter.findById(pubId).orElseThrow();

    assertThat(model.pii()).isEqualTo("S0140-6736(21)00183-2");
  }

  // ===== 测试数据构建助手 =====

  @Test
  @DisplayName("有全文记录时给出可读、可下载、PDF 大小与字数")
  void should_expose_full_text_flags_and_word_count() {
    Long venueId = saveVenue("J");
    Long pubId = savePublication(venueId, "T", "10.1/ft", "70000001");
    saveFullText(pubId, FullTextStatus.PARSED, "pmc/PMC1/PMC1.1/PMC1.1.pdf", 2_621_440L, 5230);
    em.flush();
    em.clear();

    PublicationDetailReadModel model = adapter.findById(pubId).orElseThrow();

    assertThat(model.hasFullText()).isTrue();
    assertThat(model.hasPdf()).isTrue();
    assertThat(model.pdfSizeBytes()).isEqualTo(2_621_440L);
    assertThat(model.wordCount()).isEqualTo(5230);
  }

  @Test
  @DisplayName("撤稿文献（D016441）不可读、不可下载")
  void should_hide_full_text_when_retracted() {
    Long venueId = saveVenue("J");
    Long pubId = savePublication(venueId, "T", "10.1/rt", "70000002");
    saveFullText(pubId, FullTextStatus.PARSED, "k.pdf", 10L, 100);
    saveType(pubId, "D016441", "Retracted Publication", 1);
    em.flush();
    em.clear();

    PublicationDetailReadModel model = adapter.findById(pubId).orElseThrow();

    assertThat(model.hasFullText()).isFalse();
    assertThat(model.hasPdf()).isFalse();
    assertThat(model.pdfSizeBytes()).isNull();
  }

  @Test
  @DisplayName("没有全文记录时两项都为 false")
  void should_report_no_full_text_without_record() {
    Long venueId = saveVenue("J");
    Long pubId = savePublication(venueId, "T", "10.1/nf", "70000003");
    em.flush();
    em.clear();

    PublicationDetailReadModel model = adapter.findById(pubId).orElseThrow();

    assertThat(model.hasFullText()).isFalse();
    assertThat(model.hasPdf()).isFalse();
  }

  private void saveFullText(
      Long pubId, FullTextStatus status, String pdfKey, Long pdfSize, Integer wordCount) {
    PublicationFullTextEntity f = new PublicationFullTextEntity();
    f.setId(SnowflakeIdGenerator.getId());
    f.setPublicationId(pubId);
    f.setPmcid("PMC1");
    f.setStatus(status);
    f.setPdfObjectKey(pdfKey);
    f.setPdfSize(pdfSize);
    f.setWordCount(wordCount);
    em.persist(f);
  }

  private void saveType(Long pubId, String typeId, String typeValue, int typeOrder) {
    PublicationTypeEntity t = PublicationTypeEntity.of(pubId, typeId, typeValue, "MeSH", typeOrder);
    t.setId(SnowflakeIdGenerator.getId());
    em.persist(t);
  }

  private Long saveVenue(String title) {
    VenueEntity v = new VenueEntity();
    v.setId(SnowflakeIdGenerator.getId());
    v.setVenueType("JOURNAL");
    v.setTitle(title);
    v.setAbbreviatedTitle(title.substring(0, Math.min(title.length(), 10)));
    v.setProvenanceCode("OPENALEX");
    em.persist(v);
    return v.getId();
  }

  private Long savePublication(Long venueId, String title, String doi, String pmid) {
    PublicationEntity p =
        PublicationEntity.builder()
            .id(SnowflakeIdGenerator.getId())
            .provenanceCode("PUBMED")
            .venueId(venueId)
            .venueInstanceId(SnowflakeIdGenerator.getId())
            .title(title)
            .doi(doi)
            .pmid(pmid)
            .publicationYear(2024)
            .citationCount(10)
            .isOa(false)
            .authorsComplete(true)
            .build();
    em.persist(p);
    return p.getId();
  }

  private void saveAbstract(
      Long pubId,
      String abstractType,
      List<PublicationAbstractSection> structuredSections,
      String plainText) {
    PublicationAbstractEntity a = new PublicationAbstractEntity();
    a.setId(SnowflakeIdGenerator.getId());
    a.setPublicationId(pubId);
    a.setAbstractType(abstractType);
    a.setStructuredSections(structuredSections);
    a.setPlainText(plainText);
    em.persist(a);
  }

  private void saveType(Long pubId, String typeValue, int typeOrder) {
    PublicationTypeEntity t = PublicationTypeEntity.of(pubId, null, typeValue, "MeSH", typeOrder);
    t.setId(SnowflakeIdGenerator.getId());
    em.persist(t);
  }

  private Long saveAuthor(String displayName) {
    AuthorEntity a = new AuthorEntity();
    a.setId(SnowflakeIdGenerator.getId());
    a.setNormalizedKey(displayName.toUpperCase().replace(" ", "+"));
    a.setDisplayName(displayName);
    a.setStatus("ACTIVE");
    a.setProvenanceCode("PUBMED");
    em.persist(a);
    return a.getId();
  }

  private Long savePublicationAuthor(
      Long pubId,
      Long authorId,
      String displayName,
      int order,
      boolean first,
      boolean corresponding) {
    PublicationAuthorEntity pa = new PublicationAuthorEntity();
    pa.setId(SnowflakeIdGenerator.getId());
    pa.setPublicationId(pubId);
    pa.setAuthorId(authorId);
    pa.setDisplayName(displayName);
    pa.setAuthorOrder(order);
    pa.setFirstAuthor(first);
    pa.setCorrespondingAuthor(corresponding);
    pa.setEqualContribution(false);
    em.persist(pa);
    return pa.getId();
  }

  private void saveAffiliation(Long pubAuthorId, Long pubId, int order, String affiliationString) {
    PublicationAuthorAffiliationEntity aff = new PublicationAuthorAffiliationEntity();
    aff.setId(SnowflakeIdGenerator.getId());
    aff.setPubAuthorId(pubAuthorId);
    aff.setPublicationId(pubId);
    aff.setAffiliationOrder(order);
    aff.setAffiliationString(affiliationString);
    aff.setDisambiguationStatus(DisambiguationStatus.PENDING);
    em.persist(aff);
  }

  private void saveMeshDescriptor(String ui, String name) {
    MeshDescriptorEntity d = new MeshDescriptorEntity();
    d.setId(SnowflakeIdGenerator.getId());
    d.setUi(ui);
    d.setName(name);
    d.setActiveStatus(true);
    em.persist(d);
  }

  private void saveMeshHeading(Long pubId, String descriptorUi, boolean major, int order) {
    PublicationMeshHeadingEntity h =
        PublicationMeshHeadingEntity.of(pubId, descriptorUi, major, order);
    h.setId(SnowflakeIdGenerator.getId());
    em.persist(h);
  }

  private void saveIdentifier(Long pubId, String type, String value) {
    PublicationIdentifierEntity ident = new PublicationIdentifierEntity();
    ident.setId(SnowflakeIdGenerator.getId());
    ident.setPublicationId(pubId);
    ident.setType(PublicationIdentifierType.fromCode(type));
    ident.setValue(value);
    em.persist(ident);
  }

  private void saveMetadata(Long publicationId, String fullTextUrl) {
    PublicationMetadataEntity m = new PublicationMetadataEntity();
    m.setId(SnowflakeIdGenerator.getId());
    m.setPublicationId(publicationId);
    m.setHasFullText(true);
    m.setFullTextUrl(fullTextUrl);
    em.persist(m);
  }

  @Test
  @DisplayName("详情返回 provenanceCode 与 fullTextUrl（来自 metadata LEFT JOIN）")
  void shouldReturnSourceAndFullTextUrl() {
    Long venueId = saveVenue("Nature");
    Long pubId = savePublication(venueId, "OA Study", "10.1/oa", "99");
    saveMetadata(pubId, "https://publisher.example.com/article/oa");
    em.flush();
    em.clear();

    PublicationDetailReadModel model = adapter.findById(pubId).orElseThrow();

    assertThat(model.provenanceCode()).isEqualTo("PUBMED");
    assertThat(model.fullTextUrl()).isEqualTo("https://publisher.example.com/article/oa");
  }

  @Test
  @DisplayName("作者名来自行内快照：author_id 为 NULL（未消歧）时照常展示")
  void authorName_fromInlineSnapshot_withoutLinkedAuthor() {
    Long venueId = saveVenue("Snapshot Journal");
    Long pubId = savePublication(venueId, "Snapshot Study", "10.1/x", "99999999");
    // 重建后生产主形态：author_id 为 NULL（仅 ORCID 命中已消歧作者时才填充）。
    savePublicationAuthor(pubId, null, "Snapshot Name", 1, true, false);
    em.flush();
    em.clear();

    PublicationDetailReadModel model = adapter.findById(pubId).orElseThrow();

    assertThat(model.authors()).hasSize(1);
    assertThat(model.authors().getFirst().name()).isEqualTo("Snapshot Name");
  }

  @Test
  @DisplayName("malformed structured_sections：降级空列表，plainText 照常返回")
  void malformedSectionsJson_degradesGracefully() {
    Long venueId = saveVenue("Malformed Journal");
    Long pubId = savePublication(venueId, "Malformed Study", "10.1/y", "99999998");
    saveAbstract(
        pubId,
        "STRUCTURED",
        List.of(PublicationAbstractSection.of("BACKGROUND", "Background text.")),
        "plain fallback");
    em.flush();
    // Entity 已类型化（List<PublicationAbstractSection>），无法直塞坏 JSON——用原生 SQL 注入坏形态。
    em.getEntityManager()
        .createNativeQuery(
            """
            UPDATE cat_publication_abstract
               SET structured_sections = to_jsonb('not-a-json-array'::text)
             WHERE publication_id = :id
            """)
        .setParameter("id", pubId)
        .executeUpdate();
    em.clear();

    PublicationDetailReadModel model = adapter.findById(pubId).orElseThrow();

    assertThat(model.abstractSections()).isEmpty();
    assertThat(model.abstractPlainText()).isEqualTo("plain fallback");
  }
}
