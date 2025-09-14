package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.CursorEventDO;

/**
 * Mapper · ing_cursor_event
 * <p>仅使用 MyBatis-Plus 标准方法，禁止添加自定义方法。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface IngCursorEventMapper extends BaseMapper<CursorEventDO> {
    // 严禁添加自定义方法，所有操作通过 MyBatis-Plus 提供的标准方法实现
}
