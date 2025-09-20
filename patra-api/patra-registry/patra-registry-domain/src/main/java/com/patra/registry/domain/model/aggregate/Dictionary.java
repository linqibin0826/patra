package com.patra.registry.domain.model.aggregate;

import com.patra.registry.domain.model.vo.DictionaryAlias;
import com.patra.registry.domain.model.vo.DictionaryItem;
import com.patra.registry.domain.model.vo.DictionaryType;
import com.patra.registry.domain.model.vo.ValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 字典聚合根（仅查询侧）。
 *
 * <p>用于 CQRS 的查询操作，不支持命令修改。聚合封装类型元数据、字典项与别名，
 * 并提供查询、校验与业务规则相关的领域逻辑。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class Dictionary {
    
    /** 字典类型元数据 */
    private final DictionaryType type;
    
    /** 字典项列表（构造后不可变） */
    private final List<DictionaryItem> items;
    
    /** 字典项的外部系统别名列表（构造后不可变） */
    private final List<DictionaryAlias> aliases;
    
    /**
     * 构造函数（含类型、项与别名）。
     */
    public Dictionary(DictionaryType type, List<DictionaryItem> items, List<DictionaryAlias> aliases) {
        if (type == null) {
            throw new IllegalArgumentException("Dictionary type cannot be null");
        }
        this.type = type;
        this.items = items != null ? Collections.unmodifiableList(new ArrayList<>(items)) : Collections.emptyList();
        this.aliases = aliases != null ? Collections.unmodifiableList(new ArrayList<>(aliases)) : Collections.emptyList();
    }
    
    /** 仅含类型与项（无别名）的构造函数。 */
    public Dictionary(DictionaryType type, List<DictionaryItem> items) {
        this(type, items, Collections.emptyList());
    }
    
    /**
     * 按项编码查找（忽略已删除）。
     */
    public Optional<DictionaryItem> findItemByCode(String itemCode) {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Item code cannot be null or empty");
        }
        
        return items.stream()
                .filter(item -> !item.deleted())
                .filter(item -> item.itemCode().equals(itemCode.trim()))
                .findFirst();
    }
    
    /** 查找默认项（只考虑可用且未删除）。 */
    public Optional<DictionaryItem> findDefaultItem() {
        return items.stream()
                .filter(DictionaryItem::isAvailable)
                .filter(DictionaryItem::isDefault)
                .findFirst();
    }
    
    /** 获取所有可用项（排序：sort_order 升序，其次 item_code 升序）。 */
    public List<DictionaryItem> getEnabledItems() {
        return items.stream()
                .filter(DictionaryItem::isAvailable)
                .sorted(Comparator.comparing(DictionaryItem::sortOrder)
                        .thenComparing(DictionaryItem::itemCode))
                .toList();
    }
    
    /** 获取所有可见项（未删除，忽略启用状态；同样按排序规则）。 */
    public List<DictionaryItem> getVisibleItems() {
        return items.stream()
                .filter(DictionaryItem::isVisible)
                .sorted(Comparator.comparing(DictionaryItem::sortOrder)
                        .thenComparing(DictionaryItem::itemCode))
                .toList();
    }
    
    /**
     * 校验某项引用是否有效（需存在、启用且未删除）。
     */
    public ValidationResult validateItemReference(String itemCode) {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Item code cannot be null or empty");
        }
        
        Optional<DictionaryItem> item = findItemByCode(itemCode);
        if (item.isEmpty()) {
            return ValidationResult.notFound(type.typeCode(), itemCode);
        }
        
        DictionaryItem foundItem = item.get();
        if (!foundItem.enabled()) {
            return ValidationResult.disabled(type.typeCode(), itemCode);
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 通过外部系统别名查找字典项（需匹配来源系统与外部编码）。
     */
    public Optional<DictionaryItem> findByAlias(String sourceSystem, String externalCode) {
        if (sourceSystem == null || sourceSystem.trim().isEmpty()) {
            throw new IllegalArgumentException("Source system cannot be null or empty");
        }
        if (externalCode == null || externalCode.trim().isEmpty()) {
            throw new IllegalArgumentException("External code cannot be null or empty");
        }
        
        // Find matching alias
        Optional<DictionaryAlias> matchingAlias = aliases.stream()
                .filter(alias -> alias.sourceSystem().equalsIgnoreCase(sourceSystem.trim()))
                .filter(alias -> alias.externalCode().equals(externalCode.trim()))
                .findFirst();
        
        if (matchingAlias.isEmpty()) {
            return Optional.empty();
        }
        
        // 说明：真实实现需要从别名中得到对应 itemCode 并完成映射（可能涉及仓储查询）
        return Optional.empty();
    }
    
    /** 是否存在默认项。 */
    public boolean hasDefaultItem() {
        return findDefaultItem().isPresent();
    }
    
    /** 是否存在多个默认项（数据完整性风险）。 */
    public boolean hasMultipleDefaultItems() {
        long defaultCount = items.stream()
                .filter(DictionaryItem::isAvailable)
                .filter(DictionaryItem::isDefault)
                .count();
        return defaultCount > 1;
    }
    
    /** 可用项数量。 */
    public int getEnabledItemCount() {
        return (int) items.stream()
                .filter(DictionaryItem::isAvailable)
                .count();
    }
    
    /** 所有项总数（含禁用/删除）。 */
    public int getTotalItemCount() {
        return items.size();
    }
    
    /** 获取类型元数据。 */
    public DictionaryType getType() {
        return type;
    }
    
    /** 获取全部字典项（不可变视图）。 */
    public List<DictionaryItem> getItems() {
        return items;
    }
    
    /** 获取全部别名（不可变视图）。 */
    public List<DictionaryAlias> getAliases() {
        return aliases;
    }
}
