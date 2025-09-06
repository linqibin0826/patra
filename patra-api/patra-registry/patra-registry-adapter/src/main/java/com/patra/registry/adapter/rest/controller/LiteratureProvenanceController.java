package com.patra.registry.adapter.rest.controller;


import com.patra.registry.adapter.rest.mapping.ProvenanceRespConverter;
import com.patra.registry.adapter.rest.resp.dto.resp.ProvenanceSummaryResp;
import com.patra.registry.app.service.LiteratureProvenanceService;
import com.patra.registry.app.view.ProvenanceSummary;
import com.patra.starter.web.resp.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文献数据源控制器
 */
@Slf4j
@RestController
@RequestMapping("/literature-provenances")
@RequiredArgsConstructor
public class LiteratureProvenanceController {

    private final LiteratureProvenanceService appService;
    private final ProvenanceRespConverter viewMapper;

    @GetMapping
    public ApiResponse<List<ProvenanceSummaryResp>> findAll() {
        List<ProvenanceSummary> summaries = appService.findAll();
        return ApiResponse.ok(viewMapper.toRespList(summaries));
    }
}
