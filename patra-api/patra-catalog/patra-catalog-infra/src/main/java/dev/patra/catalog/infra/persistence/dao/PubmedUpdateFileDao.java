package dev.patra.catalog.infra.persistence.dao;

import dev.patra.catalog.domain.model.enums.PubmedUpdateFileStatus;
import dev.patra.catalog.infra.persistence.entity.PubmedUpdateFileEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// `cat_pubmed_update_file` 数据访问。
///
/// @author linqibin
/// @since 0.11.0
public interface PubmedUpdateFileDao extends JpaRepository<PubmedUpdateFileEntity, Long> {

  /// 按文件名查记录。
  ///
  /// @param fileName 文件名
  /// @return 记录
  Optional<PubmedUpdateFileEntity> findByFileName(String fileName);

  /// 返回某状态下的最大文件号。
  ///
  /// @param status 状态
  /// @return 最大文件号，没有记录时为 `null`
  @Query("SELECT MAX(e.fileSeq) FROM PubmedUpdateFileEntity e WHERE e.status = :status")
  Integer findMaxSeqByStatus(@Param("status") PubmedUpdateFileStatus status);

  /// 返回不处于某状态的最小文件号。
  ///
  /// @param status 状态
  /// @return 最小文件号，没有记录时为 `null`
  @Query("SELECT MIN(e.fileSeq) FROM PubmedUpdateFileEntity e WHERE e.status <> :status")
  Integer findMinSeqByStatusNot(@Param("status") PubmedUpdateFileStatus status);
}
