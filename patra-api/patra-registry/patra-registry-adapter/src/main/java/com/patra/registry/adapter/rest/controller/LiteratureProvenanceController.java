package com.patra.registry.adapter.rest.controller;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import com.patra.registry.api.rest.dto.request.CreateLiteratureProvenanceRequest;
import com.patra.registry.api.rest.dto.request.UpdateLiteratureProvenanceRequest;
import com.patra.registry.api.rest.dto.response.LiteratureProvenanceResponse;
import com.patra.registry.app.service.LiteratureProvenanceAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文献数据源控制器
 */
@RestController
@RequestMapping("/api/registry/literature-provenances")
@RequiredArgsConstructor
public class LiteratureProvenanceController {

    private final LiteratureProvenanceAppService appService;

    /**
     * 分页查询数据源列表
     */
    @GetMapping
    public ResponseEntity<List<LiteratureProvenanceResponse>> findAll(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        // TODO: 实现分页查询逻辑
        throw new UnsupportedOperationException("findAll not implemented yet");
    }

    /**
     * 根据业务代码查询单个数据源
     */
    @GetMapping("/{code}")
    public ResponseEntity<LiteratureProvenanceResponse> findByCode(@PathVariable String code) {
        // TODO: 实现根据业务键查询逻辑
        throw new UnsupportedOperationException("findByCode not implemented yet");
    }

    /**
     * 创建新数据源
     */
    @PostMapping
    public ResponseEntity<LiteratureProvenanceResponse> create(@RequestBody CreateLiteratureProvenanceRequest request) {
        // TODO: 实现创建逻辑
        throw new UnsupportedOperationException("create not implemented yet");
    }

    /**
     * 更新数据源基本信息
     */
    @PutMapping("/{code}")
    public ResponseEntity<LiteratureProvenanceResponse> update(
            @PathVariable String code,
            @RequestBody UpdateLiteratureProvenanceRequest request) {
        // TODO: 实现更新逻辑
        throw new UnsupportedOperationException("update not implemented yet");
    }

    /**
     * 删除数据源 (逻辑删除)
     */
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        // TODO: 实现删除逻辑
        throw new UnsupportedOperationException("delete not implemented yet");
    }

    /**
     * 激活数据源
     */
    @PostMapping("/{code}:activate")
    public ResponseEntity<Void> activate(@PathVariable String code) {
        // TODO: 实现激活逻辑
        throw new UnsupportedOperationException("activate not implemented yet");
    }

    /**
     * 停用数据源
     */
    @PostMapping("/{code}:deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable String code) {
        // TODO: 实现停用逻辑
        throw new UnsupportedOperationException("deactivate not implemented yet");
    }
}
