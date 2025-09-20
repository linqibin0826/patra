package com.patra.registry.contract.query.view;

/**
 * 字典类型视图对象（对外消费，query.view）。
 *
 * <p>用于对外接口的简化类型信息表示，隐藏内部实现细节。</p>
 *
 * @param typeCode 类型编码
 * @param typeName 类型名称
 * @param description 类型描述
 * @param itemCount 可用项（启用）数量
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryTypeView(
    String typeCode,
    String typeName,
    String description,
    int itemCount
) {
    
    /** 带参数校验的紧凑构造器。 */
    public DictionaryTypeView {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type name cannot be null or empty");
        }
        if (itemCount < 0) {
            throw new IllegalArgumentException("Item count cannot be negative");
        }
        
        // Normalize the codes and names to ensure consistency
        typeCode = typeCode.trim();
        typeName = typeName.trim();
        description = description != null ? description.trim() : "";
    }
    
    /** 是否存在可用项。 */
    public boolean hasItems() {
        return itemCount > 0;
    }
    
    /**
     * 从 DictionaryTypeQuery 构建视图对象（对外字段）。
     *
     * @see com.patra.registry.contract.query.view.DictionaryTypeQuery
     */
    public static DictionaryTypeView fromQuery(DictionaryTypeQuery query) {
        return new DictionaryTypeView(
            query.typeCode(),
            query.typeName(),
            query.description(),
            query.enabledItemCount()
        );
    }
}
