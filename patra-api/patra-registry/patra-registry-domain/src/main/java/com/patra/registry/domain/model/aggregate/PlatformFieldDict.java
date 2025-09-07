package com.patra.registry.domain.model.aggregate;

import com.patra.registry.domain.model.enums.Cardinality;
import com.patra.registry.domain.model.enums.DataType;
import com.patra.registry.domain.model.enums.DateType;
import com.patra.registry.domain.model.vo.RecordRemark;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 平台字段字典聚合根
 * docref: /docs/domain/model/aggregate/PlatformFieldDict.txt
 */
@Value
@Builder
public class PlatformFieldDict {
    
    /**
     * 聚合根ID（技术键）
     */
    Long id;
    
    /**
     * 平台统一字段键（业务键，如 pub_date/title_abstract）
     */
    String fieldKey;
    
    /**
     * 数据类型
     */
    DataType dataType;
    
    /**
     * 基数：单值/多值
     */
    Cardinality cardinality;
    
    /**
     * 是否日期字段（DateLens 判定用）
     */
    Boolean isDate;
    
    /**
     * 仅日期类使用的 datetype 映射
     */
    DateType datetype;
    
    /**
     * 记录备注
     */
    List<RecordRemark> recordRemarks;
    
    /**
     * 乐观锁版本号
     */
    Long version;
    
    // 业务方法占位符
    // TODO: 实现业务逻辑方法
}
