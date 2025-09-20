package com.patra.registry.domain.port;

import com.patra.registry.domain.model.vo.expr.ApiParamMapping;
import com.patra.registry.domain.model.vo.expr.ExprCapability;
import com.patra.registry.domain.model.vo.expr.ExprField;
import com.patra.registry.domain.model.vo.expr.ExprRenderRule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Expr 相关的仓储端口，提供只读访问能力。
 */
public interface ExprRepository {

    /**
     * 查询全部统一字段字典。
     */
    List<ExprField> findAllFields();

    /**
     * 查找当前生效的 API 参数映射（支持 TASK → SOURCE 回退）。
     *
     * @param provenanceId 来源 ID
     * @param taskType     任务类型，可为空
     * @param operationCode 操作编码（SEARCH/DETAIL/...）
     * @param stdKey       标准键
     * @param at           判定时间
     */
    Optional<ApiParamMapping> findActiveParamMapping(Long provenanceId,
                                                     String taskType,
                                                     String operationCode,
                                                     String stdKey,
                                                     Instant at);

    /**
     * 查找当前生效的字段能力（支持 TASK → SOURCE 回退）。
     */
    Optional<ExprCapability> findActiveCapability(Long provenanceId,
                                                  String taskType,
                                                  String fieldKey,
                                                  Instant at);

    /**
     * 查找当前生效的渲染规则（支持 TASK → SOURCE 回退）。
     */
    Optional<ExprRenderRule> findActiveRenderRule(Long provenanceId,
                                                  String taskType,
                                                  String fieldKey,
                                                  String opCode,
                                                  String matchTypeCode,
                                                  Boolean negated,
                                                  String valueTypeCode,
                                                  String emitTypeCode,
                                                  Instant at);
}
