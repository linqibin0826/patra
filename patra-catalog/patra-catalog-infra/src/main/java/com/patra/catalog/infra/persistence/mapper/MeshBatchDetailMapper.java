package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshBatchDetailDO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MeSH 批次详情 Mapper 接口。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，获得基础 CRUD 能力。
 *
 * <p><b>功能</b>：
 *
 * <ul>
 *   <li>基础 CRUD：继承自 {@link BaseMapper}
 *   <li>查询失败批次：{@link #findFailedBatches(Long)}
 * </ul>
 *
 * <p><b>自定义查询</b>：
 *
 * <ul>
 *   <li>使用 @Select 注解定义 SQL
 *   <li>支持失败重试查询
 *   <li>只查询未删除的记录（deleted = 0）
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Mapper
public interface MeshBatchDetailMapper extends BaseMapper<MeshBatchDetailDO> {

  /**
   * 查询指定任务的所有失败批次。
   *
   * <p>查询条件：
   *
   * <ul>
   *   <li>import_id = #{importId}
   *   <li>status = 'FAILED'
   *   <li>retry_count < 3（未超过最大重试次数）
   *   <li>deleted = 0（未删除）
   * </ul>
   *
   * <p>用于失败批次重试，只返回可以重试的批次。
   *
   * @param importId 任务 ID
   * @return 失败批次列表，如果不存在则返回空列表
   */
  @Select(
      """
      SELECT * FROM cat_mesh_batch_detail
      WHERE import_id = #{importId}
        AND status = 'FAILED'
        AND retry_count < 3
        AND deleted = 0
      ORDER BY batch_num ASC
      """)
  List<MeshBatchDetailDO> findFailedBatches(@Param("importId") Long importId);
}
