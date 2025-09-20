package com.patra.registry.api.rpc.endpoint;

import com.patra.registry.api.rpc.dto.expr.ApiParamMappingResp;
import com.patra.registry.api.rpc.dto.expr.ExprCapabilityResp;
import com.patra.registry.api.rpc.dto.expr.ExprFieldResp;
import com.patra.registry.api.rpc.dto.expr.ExprRenderRuleResp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.List;

/**
 * Expr 子域内部 API 契约。
 */
@RequestMapping
public interface ExprEndpoint {

    String BASE_PATH = "/_internal/expr";

    /** 列出统一字段字典。 */
    @GetMapping(BASE_PATH + "/fields")
    List<ExprFieldResp> listFields();

    /** 查询当前生效的参数映射。 */
    @GetMapping(BASE_PATH + "/param-mapping")
    ApiParamMappingResp getParamMapping(@RequestParam("provenanceId") Long provenanceId,
                                        @RequestParam(value = "taskType", required = false) String taskType,
                                        @RequestParam("operationCode") String operationCode,
                                        @RequestParam("stdKey") String stdKey,
                                        @RequestParam(value = "at", required = false) Instant at);

    /** 查询字段能力。 */
    @GetMapping(BASE_PATH + "/capability")
    ExprCapabilityResp getCapability(@RequestParam("provenanceId") Long provenanceId,
                                      @RequestParam(value = "taskType", required = false) String taskType,
                                      @RequestParam("fieldKey") String fieldKey,
                                      @RequestParam(value = "at", required = false) Instant at);

    /** 查询渲染规则。 */
    @GetMapping(BASE_PATH + "/render-rule")
    ExprRenderRuleResp getRenderRule(@RequestParam("provenanceId") Long provenanceId,
                                     @RequestParam(value = "taskType", required = false) String taskType,
                                     @RequestParam("fieldKey") String fieldKey,
                                     @RequestParam("opCode") String opCode,
                                     @RequestParam(value = "matchTypeCode", required = false) String matchTypeCode,
                                     @RequestParam(value = "negated", required = false) Boolean negated,
                                     @RequestParam(value = "valueTypeCode", required = false) String valueTypeCode,
                                     @RequestParam("emitTypeCode") String emitTypeCode,
                                     @RequestParam(value = "at", required = false) Instant at);
}
