package dev.patra.identity.infra.account.persistence.dao;

import dev.patra.identity.infra.account.persistence.entity.UserResearchInterestEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// `idn_user_research_interest` 的 Spring Data 仓库。
public interface UserResearchInterestDao extends JpaRepository<UserResearchInterestEntity, Long> {

  /// 某个用户的全部行，按用户排好的顺序。
  ///
  /// @param userId 用户 ID
  /// @return 实体
  List<UserResearchInterestEntity> findByUserIdOrderByPositionAsc(Long userId);

  /// 批量删掉某个用户的全部行。整组替换时先删后插：派生的 `deleteBy…` 会逐行删且排在插入之后，
  /// 撞 `(user_id, position)` 唯一约束。
  ///
  /// @param userId 用户 ID
  /// @return 删掉的行数
  @Modifying
  @Query("delete from UserResearchInterestEntity e where e.userId = :userId")
  int deleteAllByUserId(@Param("userId") Long userId);
}
