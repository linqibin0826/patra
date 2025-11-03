package com.patra.registry.api.dto.dict;

/**
 * 字典项元数据,暴露给子系统客户端。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>typeCode - 字典类型标识符
 *   <li>itemCode - 字典类型内的项代码
 *   <li>displayName - 项的本地化显示名称
 *   <li>description - 可选的人类可读描述
 *   <li>isDefault - 该项是否标记为默认
 *   <li>sortOrder - 字典内的排序提示
 *   <li>enabled - 该项是否当前激活
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryItemResp(
    String typeCode,
    String itemCode,
    String displayName,
    String description,
    boolean isDefault,
    int sortOrder,
    boolean enabled) {}
