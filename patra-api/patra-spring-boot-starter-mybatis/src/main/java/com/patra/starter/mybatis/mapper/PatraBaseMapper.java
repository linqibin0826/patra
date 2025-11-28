package com.patra.starter.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/// Patra 项目的 Mapper 基类，扩展 MyBatis-Plus 的 BaseMapper。
///
/// 提供真正的批量插入方法（单条 SQL + 多 VALUES），替代性能较差的 saveBatch()。
/// 所有业务 Mapper 应继承此接口以获得批量插入能力。
///
/// ## 性能说明
///
/// `insertBatchSomeColumn` 生成单条 SQL 语句：
/// ```sql
/// INSERT INTO table (col1, col2, ...) VALUES (...), (...), (...)
/// ```
/// 相比循环 INSERT 或 saveBatch()，性能提升 10-20 倍。
///
/// ## 审计字段
///
/// 此方法会自动触发 `MetaObjectHandler.insertFill()`，无需手动填充审计字段。
///
/// ## 使用建议
///
/// - 小数据量（< 1000 条）：直接调用 `insertBatchSomeColumn(list)`
/// - 大数据量（>= 1000 条）：使用 `BatchInsertHelper.batchInsert()` 自动分片
///
/// @param <T> 实体类型
/// @author Patra Team
/// @since 0.1.0
public interface PatraBaseMapper<T> extends BaseMapper<T> {

  /// 批量插入实体（单条 SQL 语句，多行 VALUES）。
  ///
  /// 此方法由 `PatraSqlInjector` 动态注入，生成高效的批量 INSERT 语句。
  /// 自动排除逻辑删除字段（`deleted`），由框架处理默认值。
  ///
  /// **注意**：大数据量场景请配合 `BatchInsertHelper` 进行分片，避免超出 MySQL 的
  /// `max_allowed_packet` 限制。
  ///
  /// @param entityList 实体列表，不能为空
  /// @return 插入的行数
  /// @throws IllegalArgumentException 如果 entityList 为 null 或空
  int insertBatchSomeColumn(@Param("list") List<T> entityList);
}
