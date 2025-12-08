package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.CursorEventDO;
import org.apache.ibatis.annotations.Mapper;

/// 游标事件 Mapper 接口 — 对游标事件表的数据访问操作。
///
/// 目的: 记录游标推进事件时间线(检测/回滚/推进),用于审计和故障排查。此处仅执行单表操作;勿在Mapper层拼接复杂的历史聚合逻辑。
///
/// @author linqibin
/// @since 0.1.0
@Mapper
public interface CursorEventMapper extends BaseMapper<CursorEventDO> {}
