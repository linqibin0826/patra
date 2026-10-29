package dev.patra.catalog.infra.publication.persistence.dao;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// 文献子表按文献批量删除的集成测试（增量同步覆盖更新用）。
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfiguration.class)
@ActiveProfiles("test")
@DisplayName("文献子表批量删除集成测试")
class PublicationChildBatchDeleteIT {

  private static final long PUB_A = 9_200_001L;

  private static final long PUB_B = 9_200_002L;

  private static final long KEYWORD_ID = 9_200_900L;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PublicationTypeDao typeDao;
  @Autowired private PublicationKeywordDao keywordDao;
  @Autowired private PublicationFundingDao fundingDao;
  @Autowired private PublicationSupplMeshDao supplMeshDao;
  @Autowired private PublicationMeshHeadingDao meshHeadingDao;
  @Autowired private PublicationMeshQualifierDao meshQualifierDao;
  @Autowired private PublicationIdentifierDao identifierDao;

  private long idSeq;

  @BeforeEach
  void setUp() {
    idSeq = 9_201_000L;
    jdbc.update("INSERT INTO cat_keyword (id, term) VALUES (?, 'kw')", KEYWORD_ID);
    for (long pub : new long[] {PUB_A, PUB_B}) {
      jdbc.update(
          "INSERT INTO cat_publication_type (id, publication_id, type_value) VALUES (?, ?, 'T')",
          nextId(),
          pub);
      jdbc.update(
          "INSERT INTO cat_publication_keyword (id, publication_id, keyword_id) VALUES (?, ?, ?)",
          nextId(),
          pub,
          KEYWORD_ID);
      jdbc.update(
          "INSERT INTO cat_publication_funding (id, publication_id, provenance_code) VALUES (?, ?, 'PUBMED')",
          nextId(),
          pub);
      jdbc.update(
          "INSERT INTO cat_publication_suppl_mesh (id, publication_id, scr_ui) VALUES (?, ?, 'C000001')",
          nextId(),
          pub);
      long headingId = nextId();
      jdbc.update(
          "INSERT INTO cat_publication_mesh_heading (id, publication_id, descriptor_ui) VALUES (?, ?, 'D000001')",
          headingId,
          pub);
      jdbc.update(
          "INSERT INTO cat_publication_mesh_qualifier (id, publication_mesh_heading_id, qualifier_ui) VALUES (?, ?, 'Q000001')",
          nextId(),
          headingId);
      jdbc.update(
          "INSERT INTO cat_publication_identifier (id, publication_id, type, identifier_value, source) VALUES (?, ?, 'pmc', ?, 'PubMed')",
          nextId(),
          pub,
          "PMC" + pub);
      jdbc.update(
          "INSERT INTO cat_publication_identifier (id, publication_id, type, identifier_value, source) VALUES (?, ?, 'pmcid_alt', ?, 'PMC')",
          nextId(),
          pub,
          "X" + pub);
    }
  }

  @Test
  @DisplayName("只删目标文献的子表行，标识符只删 PubMed 来源，关键词主表保留")
  void should_delete_children_of_given_publications_only() {
    List<Long> ids = List.of(PUB_A);

    assertThat(meshQualifierDao.deleteByPublicationIdIn(ids)).isEqualTo(1);
    assertThat(meshHeadingDao.deleteByPublicationIdIn(ids)).isEqualTo(1);
    assertThat(typeDao.deleteByPublicationIdIn(ids)).isEqualTo(1);
    assertThat(keywordDao.deleteByPublicationIdIn(ids)).isEqualTo(1);
    assertThat(fundingDao.deleteByPublicationIdIn(ids)).isEqualTo(1);
    assertThat(supplMeshDao.deleteByPublicationIdIn(ids)).isEqualTo(1);
    assertThat(identifierDao.deleteByPublicationIdInAndSource(ids, "PubMed")).isEqualTo(1);

    for (String table :
        List.of(
            "cat_publication_type",
            "cat_publication_keyword",
            "cat_publication_funding",
            "cat_publication_suppl_mesh",
            "cat_publication_mesh_heading")) {
      assertThat(countByPublication(table, PUB_A)).as(table + " A").isZero();
      assertThat(countByPublication(table, PUB_B)).as(table + " B").isEqualTo(1);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM cat_publication_mesh_qualifier q"
                    + " JOIN cat_publication_mesh_heading h ON q.publication_mesh_heading_id = h.id"
                    + " WHERE h.publication_id = ?",
                Integer.class,
                PUB_B))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "SELECT source FROM cat_publication_identifier WHERE publication_id = ?",
                String.class,
                PUB_A))
        .containsExactly("PMC");
    assertThat(countByPublication("cat_publication_identifier", PUB_B)).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM cat_keyword WHERE id = ?", Integer.class, KEYWORD_ID))
        .isEqualTo(1);
  }

  private int countByPublication(String table, long publicationId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE publication_id = ?",
        Integer.class,
        publicationId);
  }

  private long nextId() {
    return idSeq++;
  }
}
