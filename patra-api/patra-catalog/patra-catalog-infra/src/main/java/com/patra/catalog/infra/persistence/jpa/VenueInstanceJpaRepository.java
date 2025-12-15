package com.patra.catalog.infra.persistence.jpa;

import com.patra.catalog.infra.persistence.jpa.entity.VenueInstanceEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 载体实例 JPA Repository。
///
/// 提供载体实例的数据库访问操作。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueInstanceJpaRepository extends JpaRepository<VenueInstanceEntity, Long> {

  /// 根据 venueId 集合批量查找实例。
  ///
  /// @param venueIds venueId 集合
  /// @return 匹配的实例列表
  List<VenueInstanceEntity> findByVenueIdIn(Collection<Long> venueIds);

  /// 查找期刊实例（卷期匹配）。
  ///
  /// 支持 volume 和 issue 为 null 的情况。
  ///
  /// @param venueId 载体 ID
  /// @param volume 卷号（可为 null）
  /// @param issue 期号（可为 null）
  /// @param publicationYear 出版年份
  /// @return 匹配的实例
  @Query(
      """
      SELECT e FROM VenueInstanceEntity e
      WHERE e.venueId = :venueId
        AND ((:volume IS NULL AND e.volume IS NULL) OR e.volume = :volume)
        AND ((:issue IS NULL AND e.issue IS NULL) OR e.issue = :issue)
        AND e.publicationYear = :publicationYear
      """)
  Optional<VenueInstanceEntity> findJournalInstance(
      @Param("venueId") Long venueId,
      @Param("volume") String volume,
      @Param("issue") String issue,
      @Param("publicationYear") Integer publicationYear);

  /// 查找书籍实例（版次匹配）。
  ///
  /// @param venueId 载体 ID
  /// @param edition 版次
  /// @param publicationYear 出版年份
  /// @return 匹配的实例
  @Query(
      """
      SELECT e FROM VenueInstanceEntity e
      WHERE e.venueId = :venueId
        AND ((:edition IS NULL AND e.edition IS NULL) OR e.edition = :edition)
        AND e.publicationYear = :publicationYear
      """)
  Optional<VenueInstanceEntity> findBookInstance(
      @Param("venueId") Long venueId,
      @Param("edition") String edition,
      @Param("publicationYear") Integer publicationYear);

  /// 查找会议实例（会议名称匹配）。
  ///
  /// @param venueId 载体 ID
  /// @param conferenceName 会议名称
  /// @param publicationYear 出版年份
  /// @return 匹配的实例
  @Query(
      """
      SELECT e FROM VenueInstanceEntity e
      WHERE e.venueId = :venueId
        AND ((:conferenceName IS NULL AND e.conferenceName IS NULL) OR e.conferenceName = :conferenceName)
        AND e.publicationYear = :publicationYear
      """)
  Optional<VenueInstanceEntity> findConferenceInstance(
      @Param("venueId") Long venueId,
      @Param("conferenceName") String conferenceName,
      @Param("publicationYear") Integer publicationYear);

  /// 根据 venueId 删除所有关联实例。
  ///
  /// @param venueId 载体 ID
  /// @return 删除的记录数
  @Modifying
  @Query("DELETE FROM VenueInstanceEntity e WHERE e.venueId = :venueId")
  int deleteByVenueId(@Param("venueId") Long venueId);
}
