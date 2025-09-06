package com.patra.registry.app.service;

import com.patra.registry.app.view.ProvenanceSummary;
import com.patra.registry.domain.aggregate.LiteratureProvenance;
import com.patra.registry.domain.enums.LiteratureProvenanceCode;
import com.patra.registry.app.port.out.LiteratureProvenanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 文献数据源应用服务
 */
@Service
@RequiredArgsConstructor
public class LiteratureProvenanceService {
    
    private final LiteratureProvenanceRepository repository;


    /**
     * 分页查询文献数据源
     */
    public List<ProvenanceSummary> findAll() {
        return repository.findAll();
    }
    
    /**
     * 根据业务键查找文献数据源
     */
    public Optional<LiteratureProvenance> findByCode(LiteratureProvenanceCode code) {
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
     * 删除文献数据源
     */
    public void deleteByCode(LiteratureProvenanceCode code) {
        repository.deleteByCode(code);
    }

}
