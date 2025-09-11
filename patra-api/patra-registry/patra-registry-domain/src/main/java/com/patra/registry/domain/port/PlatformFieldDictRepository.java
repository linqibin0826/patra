package com.patra.registry.domain.port;

import com.patra.registry.domain.model.aggregate.PlatformFieldDict;

import java.util.List;
import java.util.Optional;

/**
 * 平台字段字典仓储端口
 */
public interface PlatformFieldDictRepository {
    
    /**
     * 根据业务键查找
     * @param fieldKey 平台统一字段键
     * @return 平台字段字典
     */
    Optional<PlatformFieldDict> findByFieldKey(String fieldKey);
    
    /**
     * 根据ID查找
     * @param id 聚合根ID
     * @return 平台字段字典
     */
    Optional<PlatformFieldDict> findById(Long id);
    
    /**
     * 保存聚合
     * @param aggregate 平台字段字典聚合
     * @return 保存后的聚合
     */
    PlatformFieldDict save(PlatformFieldDict aggregate);
    
    /**
     * 分页查询
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 平台字段字典列表
     */
    List<PlatformFieldDict> findAll(int offset, int limit);
    
    /**
     * 根据业务键删除（逻辑删除）
     * @param fieldKey 平台统一字段键
     */
    void deleteByFieldKey(String fieldKey);
    
    /**
     * 检查业务键是否存在
     * @param fieldKey 平台统一字段键
     * @return 是否存在
     */
    boolean existsByFieldKey(String fieldKey);
}
