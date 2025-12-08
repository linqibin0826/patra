package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.VenueRelationDO;
import java.util.Collection;
import org.apache.ibatis.annotations.Param;

/// 载体关联关系 Mapper 接口 — 对载体关联关系表的数据访问操作。
///
/// 继承 `BaseMapper` 提供标准 CRUD，批量插入请使用 `Db.saveBatch()`。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueRelationMapper extends BaseMapper<VenueRelationDO> {

  /// 物理删除指定 venue 的所有关联关系。
  ///
  /// **注意**：此方法绕过 `@TableLogic` 执行物理删除，用于子表的级联删除场景。
  /// SQL 定义在 `VenueRelationMapper.xml` 中。
  ///
  /// @param venueIds 载体 ID 集合
  /// @return 删除的记录数
  int physicalDeleteByVenueIds(@Param("venueIds") Collection<Long> venueIds);

  /// 清空表（TRUNCATE TABLE）。
  ///
  /// **警告**：DDL 操作，会隐式提交事务，无法回滚。
  void truncateTable();
}
