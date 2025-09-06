package com.patra.registry.api.rest.dto.request;

/**
 * docref.aggregate: /docs/domain/aggregate/PlatformFieldDict.txt
 * docref.api: /docs/api/rest/dto/request/PlatformFieldDictRequest.txt,/docs/api/rest/dto/response/PlatformFieldDictResponse.txt
 * docref.adapter: /docs/adapter/rest/controller/platform-field-dicts.naming.txt
 */

import lombok.Data;

/**
 * 平台字段字典请求 DTO
 */
@Data
public class PlatformFieldDictRequest {
    
    /**
     * 平台统一字段键，小写蛇形命名，长度1-64
     */
    private String fieldKey;
    
    /**
     * 数据类型：date/datetime/number/text/keyword/boolean/token
     */
    private String dataType;
    
    /**
     * 基数：single/multi
     */
    private String cardinality;
    
    /**
     * 是否日期字段
     */
    private Boolean isDate;
    
    /**
     * 日期类型：PDAT/EDAT/MHDA
     */
    private String datetype;
    
    /**
     * 乐观锁版本号
     */
    private Long version;
}
