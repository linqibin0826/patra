package com.patra.registry.api.rest.dto.response;

/**
 * docref.aggregate: /docs/domain/aggregate/PlatformFieldDict.txt
 * docref.api: /docs/api/rest/dto/request/PlatformFieldDictRequest.txt,/docs/api/rest/dto/response/PlatformFieldDictResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/platform-field-dicts.naming.txt
 */

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 平台字段字典响应 DTO
 */
@Data
@SuperBuilder
public class PlatformFieldDictResponse {
    
    /**
     * 聚合根ID
     */
    private Long id;
    
    /**
     * 平台统一字段键
     */
    private String fieldKey;
    
    /**
     * 数据类型
     */
    private String dataType;
    
    /**
     * 基数
     */
    private String cardinality;
    
    /**
     * 是否日期字段
     */
    private Boolean isDate;
    
    /**
     * 日期类型
     */
    private String datetype;
    
    /**
     * 乐观锁版本号
     */
    private Long version;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
