package com.patra.registry.domain.port;

import com.patra.registry.domain.model.aggregate.LiteratureProvenance;

import java.util.List;
import java.util.Optional;

/**
 * 文献数据源仓储端口
 * docref: /docs/domain/port/LiteratureProvenanceRepository.txt
 */
public interface LiteratureProvenanceRepository {
    
    /**
     * 根据业务键查找
     * @param code 数据源代码
     * @return 文献数据源
     */
    Optional<LiteratureProvenance> findByCode(String code);
    
    /**
     * 根据ID查找
     * @param id 聚合根ID
     * @return 文献数据源
     */
    Optional<LiteratureProvenance> findById(Long id);
    
    /**
     * 保存聚合
     * @param aggregate 文献数据源聚合
     * @return 保存后的聚合
     */
    LiteratureProvenance save(LiteratureProvenance aggregate);
    
    /**
     * 分页查询
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 文献数据源列表
     */
    List<LiteratureProvenance> findAll(int offset, int limit);
    
    /**
     * 根据业务键删除（逻辑删除）
     * @param code 数据源代码
     */
    void deleteByCode(String code);
    
    /**
     * 检查业务键是否存在
     * @param code 数据源代码
     * @return 是否存在
     */
    boolean existsByCode(String code);
}
