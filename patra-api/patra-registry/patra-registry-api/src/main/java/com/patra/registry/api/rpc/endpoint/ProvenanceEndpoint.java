package com.patra.registry.api.rpc.endpoint;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceResp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.List;

/**
 * Provenance 配置内部 API 契约。
 */
public interface ProvenanceEndpoint {

    String BASE_PATH = "/_internal/provenances";

    /**
     * 列出所有来源。
     */
    @GetMapping(BASE_PATH)
    List<ProvenanceResp> listProvenances();

    /**
     * 查询单个来源。
     */
    @GetMapping(BASE_PATH + "/{code}")
    ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);

    /**
     * 加载来源配置聚合。
     */
    @GetMapping(BASE_PATH + "/{code}/config")
    ProvenanceConfigResp getConfiguration(@PathVariable("code") ProvenanceCode code,
                                          @RequestParam(value = "taskType", required = false) String taskType,
                                          @RequestParam(value = "endpointName", required = false) String endpointName,
                                          @RequestParam(value = "at", required = false) Instant at);
}
