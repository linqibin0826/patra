package com.patra.registry.domain.model.aggregate;

import com.patra.registry.domain.model.enums.LiteratureProvenanceCode;
import com.patra.registry.domain.model.enums.MatchType;
import com.patra.registry.domain.model.enums.QueryOperation;
import com.patra.registry.domain.model.enums.RangeKind;
import com.patra.registry.domain.model.vo.RecordRemark;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 查询能力实体（LiteratureProvenance聚合内的实体）
 * docref: /docs/domain/model/aggregate/LiteratureProvenance.QueryCapability.txt
 */
@Value
@Builder
public class QueryCapability {
    
    /**
     * 实体ID（技术键）
     */
    Long id;
    
    /**
     * 所属文献数据源ID
     */
    Long literatureProvenanceId;

    /**
     * 所属文献数据源代码
     */
    LiteratureProvenanceCode literatureProvenanceCode;
    
    /**
     * 内部字段键，如 ti/ab/tiab/la/pt/dp/owner
     */
    String fieldKey;
    
    /**
     * 允许的操作符集合：["TERM","IN","RANGE","EXISTS","TOKEN"]
     */
    Set<QueryOperation> ops;
    
    /**
     * 允许取反的操作符子集；NULL 表示与 ops 相同
     */
    Set<QueryOperation> negatableOps;
    
    /**
     * 该字段是否允许 NOT 取反（总开关）
     */
    Boolean supportsNot;
    
    /**
     * TERM 允许的匹配策略：["PHRASE","EXACT","ANY"]
     */
    Set<MatchType> termMatches;
    
    /**
     * TERM 是否允许大小写敏感
     */
    Boolean termCaseSensitiveAllowed;
    
    /**
     * TERM 是否允许空白
     */
    Boolean termAllowBlank;
    
    /**
     * TERM 最小长度；0 不限制
     */
    Integer termMinLen;
    
    /**
     * TERM 最大长度；0 不限制
     */
    Integer termMaxLen;
    
    /**
     * TERM 值正则（可选）
     */
    String termPattern;
    
    /**
     * IN 最大项数；0 不限制
     */
    Integer inMaxSize;
    
    /**
     * IN 是否允许大小写敏感
     */
    Boolean inCaseSensitiveAllowed;
    
    /**
     * 范围类型
     */
    RangeKind rangeKind;
    
    /**
     * 允许省略 from（-∞, x]
     */
    Boolean rangeAllowOpenStart;
    
    /**
     * 允许省略 to [x, +∞)
     */
    Boolean rangeAllowOpenEnd;
    
    /**
     * 允许无穷端闭区间（如 (-∞,x]）
     */
    Boolean rangeAllowClosedAtInfty;
    
    /**
     * 最小日期（DATE）
     */
    LocalDate dateMin;
    
    /**
     * 最大日期（DATE）
     */
    LocalDate dateMax;
    
    /**
     * 最小时间（DATETIME，UTC）
     */
    LocalDateTime datetimeMin;
    
    /**
     * 最大时间（DATETIME，UTC）
     */
    LocalDateTime datetimeMax;
    
    /**
     * 最小数值（NUMBER）
     */
    BigDecimal numberMin;
    
    /**
     * 最大数值（NUMBER）
     */
    BigDecimal numberMax;
    
    /**
     * 是否支持 EXISTS
     */
    Boolean existsSupported;
    
    /**
     * 允许的 token 种类集合（小写），如 ["owner","pmcid"]
     */
    Set<String> tokenKinds;
    
    /**
     * token 值正则（可选）
     */
    String tokenValuePattern;
    
    /**
     * 记录备注
     */
    List<RecordRemark> recordRemarks;
    
    /**
     * 乐观锁版本号
     */
    Long version;
    
    // 业务方法占位符
    // TODO: 实现查询能力验证逻辑
}
