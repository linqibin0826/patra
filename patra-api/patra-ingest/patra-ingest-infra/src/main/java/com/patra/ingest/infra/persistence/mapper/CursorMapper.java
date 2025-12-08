package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import org.apache.ibatis.annotations.Mapper;

/// 游标 Mapper 接口 — 对游标表的数据访问操作。
///
/// 存储来自外部数据源的增量同步位置(偏移量/水位),支持幂等性和从检查点恢复流程。如果后续需要按数据源+操作查询,请添加清晰命名的方法(如
/// findByProvenanceAndOperation)。
///
/// @author linqibin
/// @since 0.1.0
@Mapper
public interface CursorMapper extends BaseMapper<CursorDO> {}
