package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshImportTaskDO;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * MeSH 导入任务 Mapper 接口。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，获得基础 CRUD 能力。
 *
 * <p><b>功能</b>：
 *
 * <ul>
 *   <li>基础 CRUD：继承自 {@link BaseMapper}
 *   <li>查询正在运行的任务：{@link #findRunningTask()}
 *   <li>统计正在运行的任务数：{@link #countRunningTasks()}
 * </ul>
 *
 * <p><b>自定义查询</b>：
 *
 * <ul>
 *   <li>使用 @Select 注解定义 SQL
 *   <li>返回 Optional 避免 null 处理
 *   <li>只查询未删除的记录（deleted = 0）
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Mapper
public interface MeshImportTaskMapper extends BaseMapper<MeshImportTaskDO> {

  /**
   * 查询当前正在运行的任务。
   *
   * <p>查询条件：
   *
   * <ul>
   *   <li>status = 'PROCESSING'
   *   <li>deleted = 0（未删除）
   * </ul>
   *
   * <p>如果有多个正在运行的任务，返回最早开始的任务（ORDER BY start_time ASC）
   *
   * @return 正在运行的任务，如果不存在则返回 Optional.empty()
   */
  @Select(
      """
      SELECT * FROM cat_mesh_import_task
      WHERE status = 'PROCESSING' AND deleted = 0
      ORDER BY start_time ASC
      LIMIT 1
      """)
  Optional<MeshImportTaskDO> findRunningTask();

  /**
   * 统计正在运行的任务数。
   *
   * <p>查询条件：
   *
   * <ul>
   *   <li>status = 'PROCESSING'
   *   <li>deleted = 0（未删除）
   * </ul>
   *
   * <p>用于快速判断是否存在正在运行的任务，避免加载完整对象。
   *
   * @return 正在运行的任务数
   */
  @Select(
      """
      SELECT COUNT(*) FROM cat_mesh_import_task
      WHERE status = 'PROCESSING' AND deleted = 0
      """)
  int countRunningTasks();
}
