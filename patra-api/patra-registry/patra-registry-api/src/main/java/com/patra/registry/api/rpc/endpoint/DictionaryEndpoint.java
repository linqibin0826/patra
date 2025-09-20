package com.patra.registry.api.rpc.endpoint;

import com.patra.registry.api.rpc.dto.dict.DictionaryHealthResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryItemResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryReferenceReq;
import com.patra.registry.api.rpc.dto.dict.DictionaryTypeResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryValidationResp;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典服务对内 HTTP API 契约。
 *
 * <p>定义供各子系统（通过 Feign 客户端）调用的只读查询接口，遵循 CQRS 的查询侧约束，
 * 在微服务间提供清晰稳定的契约边界。所有端点均以内部路径前缀 {@code /_internal/dictionaries/**}
 * 区分于对外公开的 API，返回专用的 API DTO，确保跨服务的数据结构一致。</p>
 *
 * <p>支持能力：</p>
 * <ul>
 *   <li>按类型与编码查询字典项</li>
 *   <li>按类型查询启用项列表</li>
 *   <li>按类型查询默认项</li>
 *   <li>批量校验字典引用</li>
 *   <li>外部别名解析为内部字典项</li>
 *   <li>查询字典类型元数据</li>
 *   <li>系统健康状态监控</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.registry.api.rpc.client.DictionaryClient Feign 客户端
 */
public interface DictionaryEndpoint {
    
    /** 内部 API 的基础路径前缀 */
    String BASE_PATH = "/_internal/dictionaries";
    
    /**
     * 根据类型与编码查询字典项（仅返回启用且未删除的项）。
     *
     * @param typeCode 字典类型编码，不能为空
     * @param itemCode 字典项编码，不能为空
     * @return 若存在且可用则返回对象；不存在或不可用则返回 null
     * @throws IllegalArgumentException 当参数为空时
     */
    @GetMapping(BASE_PATH + "/types/{typeCode}/items/{itemCode}")
    DictionaryItemResp getItemByTypeAndCode(@PathVariable("typeCode") String typeCode, 
                                            @PathVariable("itemCode") String itemCode);
    
    /**
     * 查询某类型下所有启用的字典项（先按 sort_order 升序，再按 item_code 升序）。
     *
     * @param typeCode 字典类型编码，不能为空
     * @return 启用项列表；类型不存在或无启用项时返回空列表
     * @throws IllegalArgumentException 当参数为空时
     */
    @GetMapping(BASE_PATH + "/types/{typeCode}/items")
    List<DictionaryItemResp> getEnabledItemsByType(@PathVariable("typeCode") String typeCode);
    
    /**
     * 查询某类型的默认字典项（仅返回启用且未删除的默认项）。
     *
     * @param typeCode 字典类型编码，不能为空
     * @return 若存在且可用则返回；不存在或不可用则返回 null
     * @throws IllegalArgumentException 当参数为空时
     */
    @GetMapping(BASE_PATH + "/types/{typeCode}/default")
    DictionaryItemResp getDefaultItemByType(@PathVariable("typeCode") String typeCode);
    
    /**
     * 批量校验字典引用（存在且启用）。用于业务子系统在持久化前进行引用有效性校验。
     *
     * @param references 待校验的引用列表，不能为空
     * @return 与入参一一对应的校验结果列表
     * @throws IllegalArgumentException 当列表为空时
     */
    @PostMapping(BASE_PATH + "/validate")
    List<DictionaryValidationResp> validateReferences(@RequestBody List<DictionaryReferenceReq> references);
    
    /**
     * 通过外部系统别名查询字典项（兼容异构编码体系）。
     *
     * @param sourceSystem 外部系统标识，不能为空
     * @param externalCode 外部系统的编码，不能为空
     * @return 若存在映射且可用则返回；否则返回 null
     * @throws IllegalArgumentException 当参数为空时
     */
    @GetMapping(BASE_PATH + "/aliases")
    DictionaryItemResp getItemByAlias(@RequestParam("sourceSystem") String sourceSystem,
                                      @RequestParam("externalCode") String externalCode);
    
    /**
     * 查询系统内所有字典类型元数据（含项数、默认项等）。
     *
     * @return 按 type_code 排序的类型列表；无数据返回空列表
     */
    @GetMapping(BASE_PATH + "/types")
    List<DictionaryTypeResp> getAllTypes();
    
    /**
     * 获取字典系统健康状态（用于监控）。
     *
     * @return 包含健康指标与问题明细的对象
     */
    @GetMapping(BASE_PATH + "/health")
    DictionaryHealthResp getHealthStatus();
}
