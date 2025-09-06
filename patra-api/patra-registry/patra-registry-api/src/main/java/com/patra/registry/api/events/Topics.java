package com.patra.registry.api.events;

/**
 * 事件主题常量
 */
public class Topics {

    // LiteratureProvenance 聚合事件主题
    public static final String LITERATURE_PROVENANCE_CREATED = "registry.literature-provenance.created";
    public static final String LITERATURE_PROVENANCE_UPDATED = "registry.literature-provenance.updated";
    public static final String LITERATURE_PROVENANCE_ACTIVATED = "registry.literature-provenance.activated";
    public static final String LITERATURE_PROVENANCE_DEACTIVATED = "registry.literature-provenance.deactivated";
    public static final String LITERATURE_PROVENANCE_DELETED = "registry.literature-provenance.deleted";

    // PlatformFieldDict 聚合事件主题
    public static final String PLATFORM_FIELD_DICT_CREATED = "registry.platform-field-dict.created";
    public static final String PLATFORM_FIELD_DICT_UPDATED = "registry.platform-field-dict.updated";
    public static final String PLATFORM_FIELD_DICT_PUBLISHED = "registry.platform-field-dict.published";
    public static final String PLATFORM_FIELD_DICT_DEPRECATED = "registry.platform-field-dict.deprecated";
    public static final String PLATFORM_FIELD_DICT_DELETED = "registry.platform-field-dict.deleted";
}
