package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshTableProgressDO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MeSH 表进度记录 Mapper 接口。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，获得基础 CRUD 能力。
 *
 * <p><b>功能</b>：
 *
 * <ul>
 *   <li>基础 CRUD：继承自 {@link BaseMapper}
 *   <li>根据任务 ID 查询：{@link #findByImportId(Long)}
 * </ul>
 *
 * <p><b>自定义查询</b>：
 *
 * <ul>
 *   <li>使用 @Select 注解定义 SQL
 *   <li>返回 List 支持多表进度
 *   <li>只查询未删除的记录（deleted = 0）
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Mapper
public interface MeshTableProgressMapper extends BaseMapper<MeshTableProgressDO> {

  /**
   * 根据任务 ID 查询所有表进度记录。
   *
   * <p>查询条件：
   *
   * <ul>
   *   <li>import_id = #{importId}
   *   <li>deleted = 0（未删除）
   * </ul>
   *
   * <p>用于加载聚合根的关联数据（表进度列表）
   *
   * @param importId 任务 ID
   * @return 表进度列表，如果不存在则返回空列表
   */
  @Select(
      """
      SELECT * FROM cat_mesh_table_progress
      WHERE import_id = #{importId} AND deleted = 0
      ORDER BY table_name ASC
      """)
  List<MeshTableProgressDO> findByImportId(@Param("importId") Long importId);
}
