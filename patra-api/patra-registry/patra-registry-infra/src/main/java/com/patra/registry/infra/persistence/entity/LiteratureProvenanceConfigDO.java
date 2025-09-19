package com.patra.registry.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文献数据源配置数据对象
 */
@Data
@SuperBuilder
@NoArgsConstructor
@TableName(value = "reg_provenance_config", autoResultMap = true)
@EqualsAndHashCode(callSuper = true)
public class LiteratureProvenanceConfigDO extends BaseDO {

    /**
     * 逻辑外键→reg_provenance.id
     */
    private Long ProvenanceId;
    
    /**
     * 窗口计算/展示的时区;写库仍用UTC
     */
    private String timezone;
    
    /**
     * 失败最大重试次数;NULL=使用应用默认
     */
    private Integer retryMax;
    
    /**
     * 重试退避毫秒;与 retry_max 配合
     */
    private Integer backoffMs;
    
    /**
     * 每秒请求上限(令牌桶);NULL=不限流或用应用默认
     */
    private Integer rateLimitPerSec;
    
    /**
     * 列表/搜索页大小(ESearch/列表等);NULL=用默认
     */
    private Integer searchPageSize;
    
    /**
     * 详情批大小(EFetch/works 批);NULL=用默认
     */
    private Integer fetchBatchSize;
    
    /**
     * 单窗口最多 UID(超出则二次切窗);NULL=用默认
     */
    private Integer maxSearchIdsPerWindow;
    
    /**
     * 增量窗口重叠天数(迟到兜底);NULL=用默认
     */
    private Integer overlapDays;
    
    /**
     * 重试抖动系数(0~1);NULL=用默认
     */
    private Double retryJitter;
    
    /**
     * 是否写入 acc_*(当来源返回 OA/下载位时)
     */
    private Boolean enableAccess;
    
    /**
     * 默认增量字段(如 PubMed: EDAT/PDAT/MHDA;Crossref: index-date 等)
     */
    private String dateFieldDefault;
    
    /**
     * API 基址
     */
    private String baseUrl;
    
    /**
     * 鉴权配置(建议仅存凭据引用ID/issuer,真正密钥放 Vault/KMS/ENV)
     */
    private JsonNode auth;
    
    /**
     * 默认 HTTP 头(如 User-Agent/From/email 等)
     */
    private JsonNode headers;
}
