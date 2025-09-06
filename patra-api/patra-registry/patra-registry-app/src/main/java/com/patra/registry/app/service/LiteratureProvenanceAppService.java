package com.patra.registry.app.service;

/**
 * docref.aggregate: /docs/domain/aggregate/LiteratureProvenance.txt
 * docref.api: /docs/api/rest/dto/request/LiteratureProvenanceRequest.txt,/docs/api/rest/dto/response/LiteratureProvenanceResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/literature-provenances.naming.txt
 */

import com.patra.registry.domain.model.aggregate.LiteratureProvenance;
import com.patra.registry.domain.port.LiteratureProvenanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 文献数据源应用服务
 */
@Service
@RequiredArgsConstructor
public class LiteratureProvenanceAppService {
    
    private final LiteratureProvenanceRepository repository;
    
    /**
     * 根据业务键查找文献数据源
     */
    public Optional<LiteratureProvenance> findByCode(String code) {
        return repository.findByCode(code);
    }
    
    /**
     * 创建文献数据源
     */
    public LiteratureProvenance create(LiteratureProvenance aggregate) {
        // TODO: 实现创建逻辑，包括业务校验
        return repository.save(aggregate);
    }
    
    /**
     * 更新文献数据源
     */
    public LiteratureProvenance update(LiteratureProvenance aggregate) {
        // TODO: 实现更新逻辑，包括版本控制
        return repository.save(aggregate);
    }
    
    /**
     * 分页查询文献数据源
     */
    public List<LiteratureProvenance> findAll(int offset, int limit) {
        return repository.findAll(offset, limit);
    }
    
    /**
     * 删除文献数据源
     */
    public void deleteByCode(String code) {
        repository.deleteByCode(code);
    }
    
    /**
     * 激活数据源
     */
    public void activate(String code) {
        // TODO: 实现激活逻辑
        throw new UnsupportedOperationException("activate not implemented yet");
    }
    
    /**
     * 停用数据源
     */
    public void deactivate(String code) {
        // TODO: 实现停用逻辑
        throw new UnsupportedOperationException("deactivate not implemented yet");
    }
}
