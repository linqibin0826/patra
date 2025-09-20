package com.patra.registry.domain.port;

import com.patra.registry.domain.model.aggregate.Dictionary;
import com.patra.registry.domain.model.vo.DictionaryHealthStatus;
import com.patra.registry.domain.model.vo.DictionaryItem;
import com.patra.registry.domain.model.vo.DictionaryType;

import java.util.List;
import java.util.Optional;

/**
 * 字典查询侧领域仓储端口。
 *
 * <p>定义 CQRS 查询模型下的数据访问契约，仅包含只读操作。</p>
 *
 * <p>提供字典聚合、单项/类型与系统健康信息的访问，保持六边形架构的边界清晰。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface DictionaryRepository {
    
    /**
     * 按类型编码查询字典聚合（含类型、项、别名；过滤逻辑删除）。
     */
    Optional<Dictionary> findByTypeCode(String typeCode);
    
    /** 查询系统内所有字典类型（按 type_code 排序）。 */
    List<DictionaryType> findAllTypes();
    
    /** 按类型与项编码查询单个字典项（过滤逻辑删除，不加载聚合）。 */
    Optional<DictionaryItem> findItemByTypeAndCode(String typeCode, String itemCode);
    
    /** 查询某类型的默认项（可用且未删除，若多条则取首条）。 */
    Optional<DictionaryItem> findDefaultItemByType(String typeCode);
    
    /** 查询某类型下的启用项（按 sort_order、item_code 排序）。 */
    List<DictionaryItem> findEnabledItemsByType(String typeCode);
    
    /** 通过外部系统别名查询字典项（存在、启用、未删除）。 */
    Optional<DictionaryItem> findByAlias(String sourceSystem, String externalCode);
    
    /** 获取字典系统健康状态（聚合统计，可能较重）。 */
    DictionaryHealthStatus getHealthStatus();
    
    /** 判断类型是否存在（轻量存在性检查）。 */
    boolean existsByTypeCode(String typeCode);
    
    /** 判断某类型下给定项是否存在（过滤逻辑删除）。 */
    boolean existsByTypeAndItemCode(String typeCode, String itemCode);
    
    /** 统计某类型下启用项数量（轻量计数）。 */
    int countEnabledItemsByType(String typeCode);
    
    /** 查询没有默认项的类型（健康检查用）。 */
    List<String> findTypesWithoutDefaults();
    
    /** 查询存在多个默认项的类型（健康检查用）。 */
    List<String> findTypesWithMultipleDefaults();
}
