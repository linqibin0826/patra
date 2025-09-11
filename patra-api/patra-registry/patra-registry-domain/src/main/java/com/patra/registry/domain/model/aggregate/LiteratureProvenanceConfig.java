package com.patra.registry.domain.model.aggregate;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.model.vo.RecordRemark;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * 文献数据源配置实体（属于 LiteratureProvenance 聚合内的实体）。
 *
 * <p>职责：承载与上游数据源交互所需的技术参数与行为约束（限流、重试、分页、窗口策略、默认头等）。
 * <p>边界：不包含任何技术实现（HTTP 客户端/缓存等），仅提供不变式校验能力。
 *
 * <p>关键字段说明：
 * - retryMax/backoffMs/retryJitter：重试策略参数；
 * - rateLimitPerSec：每秒令牌数；
 * - searchPageSize/fetchBatchSize：拉取分页/详情批量；
 * - maxSearchIdsPerWindow/overlapDays：增量窗口切分与重叠；
 * - headers/baseUrl/auth：对上游 API 的默认请求头、基址与鉴权引用。
 *
 * <p>本类为不可变值载体（@Value），由聚合根在变更时整体替换（replaceConfig）。
 *
 * @author linqibin
 * @since 0.1.0
 * @see LiteratureProvenance#replaceConfig(LiteratureProvenanceConfig, String)
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
     * 数据源代码（业务键）
     */
    ProvenanceCode provenanceCode;

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

    /**
     * 配置不变式校验。
     *
     * <p>校验要点：
     * - provenanceCode 必填；
     * - 数值参数范围（不可为负；长度上界/下界关系）；
     * - retryJitter 在 [0,1] 区间；
     * - headers 的 key/value 不可为空白；
     * - baseUrl 如提供则不可为空白。
     */
    public void validate() {
        Assert.notNull(provenanceCode, "config.provenanceCode is required");
        if (retryMax != null) Assert.isTrue(retryMax >= 0, "config.retryMax < 0");
        if (backoffMs != null) Assert.isTrue(backoffMs >= 0, "config.backoffMs < 0");
        if (rateLimitPerSec != null) Assert.isTrue(rateLimitPerSec >= 0, "config.rateLimitPerSec < 0");
        if (searchPageSize != null) Assert.isTrue(searchPageSize > 0, "config.searchPageSize <= 0");
        if (fetchBatchSize != null) Assert.isTrue(fetchBatchSize > 0, "config.fetchBatchSize <= 0");
        if (maxSearchIdsPerWindow != null)
            Assert.isTrue(maxSearchIdsPerWindow > 0, "config.maxSearchIdsPerWindow <= 0");
        if (overlapDays != null) Assert.isTrue(overlapDays >= 0, "config.overlapDays < 0");
        if (retryJitter != null)
            Assert.isTrue(retryJitter >= 0.0 && retryJitter <= 1.0, "config.retryJitter not in [0,1]");
        if (baseUrl != null) Assert.isFalse(StrUtil.isBlank(baseUrl), "config.baseUrl blank");
        if (!CollUtil.isEmpty(headers)) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                Assert.isFalse(StrUtil.isBlank(e.getKey()), "config.headers key blank");
                Assert.isFalse(StrUtil.isBlank(e.getValue()), "config.headers value blank");
            }
        }
    }
}
