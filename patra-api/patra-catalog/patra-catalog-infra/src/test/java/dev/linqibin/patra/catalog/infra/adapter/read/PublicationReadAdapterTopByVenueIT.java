package dev.linqibin.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel;
import dev.linqibin.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.VenueDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.VenueEntity;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `PublicationReadAdapter#findTopByVenue` 集成测试。
///
/// **测试目标**：
///
/// - 返回 [PublicationSummaryReadModel] 列表，字段映射正确
/// - `venueName` 通过 [VenueDao] 单次查询正确装配
/// - 空数据场景返回空列表
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  PublicationReadAdapter.class,
  PublicationReadModelMapperImpl.class,
  JpaAuditingConfig.class
})
@ActiveProfiles("test")
@DisplayName("PublicationReadAdapter#findTopByVenue 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PublicationReadAdapterTopByVenueIT {

  @Autowired private PublicationReadAdapter publicationReadAdapter;
  @Autowired private PublicationDao publicationDao;
  @Autowired private VenueDao venueDao;

  private Long venueId;

  @BeforeEach
  void setUp() {
    publicationDao.deleteAll();
    venueDao.deleteAll();

    VenueEntity venue = new VenueEntity();
    venue.setId(SnowflakeIdGenerator.getId());
    venue.setVenueType("JOURNAL");
    venue.setTitle("Top Venue");
    venue.setProvenanceCode("PUBMED");
    venueDao.save(venue);
    venueId = venue.getId();

    // 3 篇文献：citation_count 500/300/100
    savePublication("Paper A", 500, 2023);
    savePublication("Paper B", 300, 2022);
    savePublication("Paper C", 100, 2020);
  }

  @Test
  @DisplayName("返回 ReadModel 列表且字段映射正确（含 venueName）")
  void findTopByVenue_returnsReadModelListWithVenueName() {
    List<PublicationSummaryReadModel> result =
        publicationReadAdapter.findTopByVenue(venueId, null, 5);

    assertThat(result).hasSize(3);
    PublicationSummaryReadModel top = result.get(0);
    assertThat(top.title()).isEqualTo("Paper A");
    assertThat(top.citationCount()).isEqualTo(500);
    assertThat(top.publicationYear()).isEqualTo(2023);
    assertThat(top.venueId()).isEqualTo(venueId);
    assertThat(top.venueName()).isEqualTo("Top Venue");
  }

  @Test
  @DisplayName("limit 控制返回条数")
  void findTopByVenue_respectsLimit() {
    List<PublicationSummaryReadModel> result =
        publicationReadAdapter.findTopByVenue(venueId, null, 2);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).citationCount()).isEqualTo(500);
    assertThat(result.get(1).citationCount()).isEqualTo(300);
  }

  @Test
  @DisplayName("since 过滤 publication_year")
  void findTopByVenue_withSinceFiltersYear() {
    List<PublicationSummaryReadModel> result =
        publicationReadAdapter.findTopByVenue(venueId, 2022, 10);

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(p -> p.publicationYear() >= 2022);
  }

  @Test
  @DisplayName("limit<=0 返回空列表")
  void findTopByVenue_withZeroLimit_returnsEmpty() {
    List<PublicationSummaryReadModel> result =
        publicationReadAdapter.findTopByVenue(venueId, null, 0);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("venue 不存在时返回空列表且不抛异常")
  void findTopByVenue_whenVenueAbsent_returnsEmpty() {
    List<PublicationSummaryReadModel> result =
        publicationReadAdapter.findTopByVenue(99999999L, null, 5);

    assertThat(result).isEmpty();
  }

  private void savePublication(String title, int citationCount, int year) {
    PublicationEntity p = new PublicationEntity();
    p.setId(SnowflakeIdGenerator.getId());
    p.setVenueId(venueId);
    p.setTitle(title);
    p.setProvenanceCode("PUBMED");
    p.setCitationCount(citationCount);
    p.setPublicationYear(year);
    p.setIsOa(false);
    p.setAuthorsComplete(true);
    publicationDao.save(p);
  }
}
