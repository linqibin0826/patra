package com.patra.registry.domain.model.aggregate;

import com.patra.registry.domain.model.vo.RecordRemark;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * 文献数据源配置实体（LiteratureProvenance聚合内的实体）
 * docref: /docs/domain/model/aggregate/LiteratureProvenance.LiteratureProvenanceConfig.txt
 */
@Value
@Builder
public class LiteratureProvenanceConfig {

    /**
     * 实体ID（技术键）
     */
    Long id;

    /**
     * 所属文献数据源ID
     */
    Long literatureProvenanceId;

    /**
     * 窗口计算/展示的时区;写库仍用UTC
     */
    String timezone;

    /**
     * 失败最大重试次数;NULL=使用应用默认
     */
    Integer retryMax;

    /**
     * 重试退避毫秒;与 retry_max 配合
     */
    Integer backoffMs;

    /**
     * 每秒请求上限(令牌桶);NULL=不限流或用应用默认
     */
    Integer rateLimitPerSec;

    /**
     * 列表/搜索页大小(ESearch/列表等);NULL=用默认
     */
    Integer searchPageSize;

    /**
     * 详情批大小(EFetch/works 批);NULL=用默认
     */
    Integer fetchBatchSize;

    /**
     * 单窗口最多 UID(超出则二次切窗);NULL=用默认
     */
    Integer maxSearchIdsPerWindow;

    /**
     * 增量窗口重叠天数(迟到兜底);NULL=用默认
     */
    Integer overlapDays;

    /**
     * 重试抖动系数(0~1);NULL=用默认
     */
    Double retryJitter;

    /**
     * 是否写入 acc_*(当来源返回 OA/下载位时)
     */
    Boolean enableAccess;

    /**
     * 默认增量字段(如 PubMed: EDAT/PDAT/MHDA;Crossref: index-date 等)
     */
    String dateFieldDefault;

    /**
     * API 基址
     */
    String baseUrl;

    /**
     * 鉴权配置(建议仅存凭据引用ID/issuer,真正密钥放 Vault/KMS/ENV)
     */
    String auth;

    /**
     * 默认 HTTP 头(如 User-Agent/From/email 等)
     */
    Map<String, String> headers;

    /**
     * 记录备注
     */
    List<RecordRemark> recordRemarks;

    /**
     * 乐观锁版本号
     */
    Long version;

    // 业务方法占位符
    // TODO: 实现配置验证和获取有效值的方法
}
