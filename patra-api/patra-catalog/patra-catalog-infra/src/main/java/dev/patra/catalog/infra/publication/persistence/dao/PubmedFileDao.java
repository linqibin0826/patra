package dev.patra.catalog.infra.publication.persistence.dao;

import dev.patra.catalog.domain.publication.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.publication.model.enums.PubmedFileStatus;
import dev.patra.catalog.infra.publication.persistence.entity.PubmedFileEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// `cat_pubmed_file` 数据访问。
///
/// @author linqibin
/// @since 0.11.0
public interface PubmedFileDao extends JpaRepository<PubmedFileEntity, Long> {

  /// 按文件名查记录。
  ///
  /// @param fileName 文件名
  /// @return 记录
  Optional<PubmedFileEntity> findByFileName(String fileName);

  /// 返回某状态下的最大文件号。
  ///
  /// @param kind 文件种类
  /// @param status 状态
  /// @return 最大文件号，没有记录时为 `null`
  @Query(
      "SELECT MAX(e.fileSeq) FROM PubmedFileEntity e WHERE e.kind = :kind AND e.status = :status")
  Integer findMaxSeqByKindAndStatus(
      @Param("kind") PubmedFileKind kind, @Param("status") PubmedFileStatus status);

  /// 返回不处于某状态的最小文件号。
  ///
  /// @param kind 文件种类
  /// @param status 状态
  /// @return 最小文件号，没有记录时为 `null`
  @Query(
      "SELECT MIN(e.fileSeq) FROM PubmedFileEntity e WHERE e.kind = :kind AND e.status <> :status")
  Integer findMinSeqByKindAndStatusNot(
      @Param("kind") PubmedFileKind kind, @Param("status") PubmedFileStatus status);
}
