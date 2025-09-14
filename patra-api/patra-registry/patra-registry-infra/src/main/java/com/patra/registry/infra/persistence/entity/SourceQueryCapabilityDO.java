package com.patra.registry.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.registry.domain.model.enums.RangeKind;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据源查询能力数据对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@TableName(value = "reg_source_query_capability", autoResultMap = true)
@EqualsAndHashCode(callSuper = true)
public class SourceQueryCapabilityDO extends BaseDO {
    
    /**
     * 逻辑外键→reg_literature_provenance.id
     */
    private Long literatureProvenanceId;
    
    /**
     * 内部字段键，如 ti/ab/tiab/la/pt/dp/owner
     */
    private String fieldKey;
    
    /**
     * 允许的操作符集合：["TERM","IN","RANGE","EXISTS","TOKEN"]
     */
    private JsonNode ops;
    
    /**
     * 允许取反的操作符子集；NULL 表示与 ops 相同
     */
    private JsonNode negatableOps;
    
    /**
     * 该字段是否允许 NOT 取反（总开关）
     */
    private Boolean supportsNot;
    
    /**
     * TERM 允许的匹配策略：["PHRASE","EXACT","ANY"]
     */
    private JsonNode termMatches;
    
    /**
     * TERM 是否允许大小写敏感
     */
    private Boolean termCaseSensitiveAllowed;
    
    /**
     * TERM 是否允许空白
     */
    private Boolean termAllowBlank;
    
    /**
     * TERM 最小长度；0 不限制
     */
    private Integer termMinLen;
    
    /**
     * TERM 最大长度；0 不限制
     */
    private Integer termMaxLen;
    
    /**
     * TERM 值正则（可选）
     */
    private String termPattern;
    
    /**
     * IN 最大项数；0 不限制
     */
    private Integer inMaxSize;
    
    /**
     * IN 是否允许大小写敏感
     */
    private Boolean inCaseSensitiveAllowed;
    
    /**
     * 范围类型
     */
    private RangeKind rangeKind;
    
    /**
     * 允许省略 from（-∞, x]
     */
    private Boolean rangeAllowOpenStart;
    
    /**
     * 允许省略 to [x, +∞)
     */
    private Boolean rangeAllowOpenEnd;
    
    /**
     * 允许无穷端闭区间（如 (-∞,x]）
     */
    private Boolean rangeAllowClosedAtInfty;
    
    /**
     * 最小日期（DATE）
     */
    private LocalDate dateMin;
    
    /**
     * 最大日期（DATE）
     */
    private LocalDate dateMax;
    
    /**
     * 最小时间（DATETIME，UTC）
     */
    private Instant datetimeMin;
    
    /**
     * 最大时间（DATETIME，UTC）
     */
    private Instant datetimeMax;
    
    /**
     * 最小数值（NUMBER）
     */
    private BigDecimal numberMin;
    
    /**
     * 最大数值（NUMBER）
     */
    private BigDecimal numberMax;
    
    /**
     * 是否支持 EXISTS
     */
    private Boolean existsSupported;
    
    /**
     * 允许的 token 种类集合（小写），如 ["owner","pmcid"]
     */
    private JsonNode tokenKinds;
    
    /**
     * token 值正则（可选）
     */
    private String tokenValuePattern;
}
