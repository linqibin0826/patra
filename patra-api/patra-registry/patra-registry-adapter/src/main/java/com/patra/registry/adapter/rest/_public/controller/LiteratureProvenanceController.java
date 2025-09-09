package com.patra.registry.adapter.rest._public.controller;


import com.patra.registry.adapter.rest._public.Converter.ProvenanceWebConverter;
import com.patra.registry.adapter.rest._public.resp.dto.resp.ProvenanceSummaryResp;
import com.patra.registry.app.service.LiteratureProvenanceService;
import com.patra.registry.contract.query.view.ProvenanceSummaryView;
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
    private final ProvenanceWebConverter viewMapper;

    @GetMapping
    public ApiResponse<List<ProvenanceSummaryResp>> findAll() {
        log.info("Received request to list all literature provenances");
        List<ProvenanceSummaryView> summaries = appService.findAll();
        return ApiResponse.ok(viewMapper.toRespList(summaries));
    }
}
