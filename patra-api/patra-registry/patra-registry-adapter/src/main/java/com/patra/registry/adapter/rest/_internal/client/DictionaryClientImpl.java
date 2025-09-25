package com.patra.registry.adapter.rest._internal.client;

import com.patra.registry.adapter.rest._internal.convertor.DictionaryApiConvertor;
import com.patra.registry.api.rpc.dto.dict.DictionaryHealthResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryItemResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryReferenceReq;
import com.patra.registry.api.rpc.dto.dict.DictionaryTypeResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryValidationResp;
import com.patra.registry.api.rpc.client.DictionaryClient;
import com.patra.registry.app.service.DictionaryQueryAppService;
import com.patra.registry.app.service.DictionaryValidationAppService;
import com.patra.registry.domain.model.read.dictionary.DictionaryHealthQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryItemQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryTypeQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryValidationQuery;
import com.patra.registry.domain.model.vo.dictionary.DictionaryReference;
import com.patra.registry.domain.exception.dictionary.DictionaryNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * 字典内部 API 控制器实现（供子系统调用）。
 *
 * <p>实现 Feign 客户端契约，委托应用服务并完成 Query -> DTO 的转换；遵循
 * /_internal/dictionaries/** 路由前缀；严格只读，符合 CQRS 查询侧。</p>
 *
 * <p>在 404 场景下通过返回 null 交由 Feign 层处理；统一结构化日志包含请求入参，便于观测与排障。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestController
public class DictionaryClientImpl implements DictionaryClient {

    /** 字典查询应用服务 */
    private final DictionaryQueryAppService dictionaryQueryAppService;

    /** 字典校验应用服务 */
    private final DictionaryValidationAppService dictionaryValidationAppService;

    /** 契约 Query <-> HTTP DTO 转换器 */
    private final DictionaryApiConvertor dictionaryApiConvertor;

    /** 构造函数。 */
    public DictionaryClientImpl(
            DictionaryQueryAppService dictionaryQueryAppService,
            DictionaryValidationAppService dictionaryValidationAppService,
            DictionaryApiConvertor dictionaryApiConvertor) {
        this.dictionaryQueryAppService = dictionaryQueryAppService;
        this.dictionaryValidationAppService = dictionaryValidationAppService;
        this.dictionaryApiConvertor = dictionaryApiConvertor;
    }

    /** 按类型与项编码获取字典项（未找到/禁用返回 null，交由 Feign 处理 404）。 */
    @Override
    public DictionaryItemResp getItemByTypeAndCode(String typeCode, String itemCode) {
        log.info("API: Getting dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);

        try {
            Optional<DictionaryItemQuery> result = dictionaryQueryAppService.findItemByTypeAndCode(typeCode, itemCode);

            if (result.isEmpty()) {
                log.info("API: Dictionary item not found: typeCode={}, itemCode={}", typeCode, itemCode);
                throw new DictionaryNotFoundException(typeCode, itemCode);
            }

            log.info("API: Successfully returned dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
            return dictionaryApiConvertor.toItemResp(result.get());

        } catch (IllegalArgumentException e) {
            log.warn("API: Invalid parameters for dictionary item lookup: typeCode={}, itemCode={}, error={}",
                    typeCode, itemCode, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("API: Failed to get dictionary item: typeCode={}, itemCode={}, error={}",
                    typeCode, itemCode, e.getMessage(), e);
            throw e;
        }
    }

    /** 获取某类型下所有启用项（类型不存在或无启用项返回空列表）。 */
    @Override
    public List<DictionaryItemResp> getEnabledItemsByType(String typeCode) {
        log.info("API: Getting enabled dictionary items for type: typeCode={}", typeCode);

        try {
            List<DictionaryItemQuery> result = dictionaryQueryAppService.findEnabledItemsByType(typeCode);

            log.info("API: Successfully returned {} enabled dictionary items for type: typeCode={}",
                    result.size(), typeCode);
            return dictionaryApiConvertor.toItemResp(result);

        } catch (IllegalArgumentException e) {
            log.warn("API: Invalid type code for enabled items lookup: typeCode={}, error={}",
                    typeCode, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("API: Failed to get enabled dictionary items: typeCode={}, error={}",
                    typeCode, e.getMessage(), e);
            throw e;
        }
    }

    /** 获取某类型的默认项（可用且未删除；不存在或不可用返回 null）。 */
    @Override
    public DictionaryItemResp getDefaultItemByType(String typeCode) {
        log.info("API: Getting default dictionary item for type: typeCode={}", typeCode);

        try {
            Optional<DictionaryItemQuery> result = dictionaryQueryAppService.findDefaultItemByType(typeCode);

            if (result.isEmpty()) {
                log.info("API: No default dictionary item found for type: typeCode={}", typeCode);
                throw new DictionaryNotFoundException(typeCode);
            }

            log.info("API: Successfully returned default dictionary item for type: typeCode={}, itemCode={}",
                    typeCode, result.get().itemCode());
            return dictionaryApiConvertor.toItemResp(result.get());

        } catch (IllegalArgumentException e) {
            log.warn("API: Invalid type code for default item lookup: typeCode={}, error={}",
                    typeCode, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("API: Failed to get default dictionary item: typeCode={}, error={}",
                    typeCode, e.getMessage(), e);
            throw e;
        }
    }

    /** 批量校验字典引用（存在且启用）。 */
    @Override
    public List<DictionaryValidationResp> validateReferences(List<DictionaryReferenceReq> references) {
        int requestSize = references != null ? references.size() : 0;
        log.info("API: Validating {} dictionary references in batch", requestSize);

        try {
            List<DictionaryReference> domainReferences = dictionaryApiConvertor.toReference(references);
            List<DictionaryValidationQuery> result = dictionaryValidationAppService.validateReferences(domainReferences);

            long validCount = result.stream().filter(DictionaryValidationQuery::isValid).count();
            long invalidCount = result.size() - validCount;

            log.info("API: Batch validation completed: total={}, valid={}, invalid={}",
                    result.size(), validCount, invalidCount);

            if (invalidCount > 0) {
                log.warn("API: Batch validation found {} invalid references", invalidCount);
            }

            return dictionaryApiConvertor.toValidationResp(result);

        } catch (IllegalArgumentException e) {
            log.warn("API: Invalid references list for batch validation: error={}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("API: Failed to validate dictionary references: referencesCount={}, error={}",
                    requestSize, e.getMessage(), e);
            throw e;
        }
    }

    /** 通过外部系统别名获取字典项（不存在或不可用返回 null）。 */
    @Override
    public DictionaryItemResp getItemByAlias(String sourceSystem, String externalCode) {
        log.info("API: Getting dictionary item by alias: sourceSystem={}, externalCode={}",
                sourceSystem, externalCode);

        try {
            Optional<DictionaryItemQuery> result = dictionaryQueryAppService.findByAlias(sourceSystem, externalCode);

            if (result.isEmpty()) {
                log.info("API: Dictionary item not found by alias: sourceSystem={}, externalCode={}",
                        sourceSystem, externalCode);
                throw new DictionaryNotFoundException(
                        String.format("Dictionary item not found by alias: sourceSystem=%s, externalCode=%s",
                                sourceSystem, externalCode), null, null);
            }

            log.info("API: Successfully returned dictionary item by alias: sourceSystem={}, externalCode={}, itemCode={}",
                    sourceSystem, externalCode, result.get().itemCode());
            return dictionaryApiConvertor.toItemResp(result.get());

        } catch (IllegalArgumentException e) {
            log.warn("API: Invalid parameters for alias lookup: sourceSystem={}, externalCode={}, error={}",
                    sourceSystem, externalCode, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("API: Failed to get dictionary item by alias: sourceSystem={}, externalCode={}, error={}",
                    sourceSystem, externalCode, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get all dictionary types in the system.
     * Retrieves metadata for all dictionary types including item counts and default status.
     * Returns empty list if no dictionary types exist in the system.
     *
     * @return List of all dictionary type responses ordered by type_code, empty list if no types exist
     */
    @Override
    public List<DictionaryTypeResp> getAllTypes() {
        log.info("API: Getting all dictionary types");

        try {
            List<DictionaryTypeQuery> result = dictionaryQueryAppService.findAllTypes();

            log.info("API: Successfully returned {} dictionary types", result.size());
            return dictionaryApiConvertor.toTypeResp(result);

        } catch (Exception e) {
            log.error("API: Failed to get all dictionary types: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get dictionary system health status for monitoring.
     * Provides comprehensive health metrics including item counts, integrity issues,
     * and configuration problems for monitoring systems and health check endpoints.
     *
     * @return DictionaryHealthResp containing system health metrics and issue details
     */
    @Override
    public DictionaryHealthResp getHealthStatus() {
        log.info("API: Getting dictionary system health status");

        try {
            DictionaryHealthQuery result = dictionaryValidationAppService.getHealthStatus();

            // Log health status summary
            if (result.isHealthy()) {
                log.info("API: Dictionary system health check: HEALTHY - {} types, {} items ({} enabled)",
                        result.totalTypes(), result.totalItems(), result.enabledItems());
            } else {
                log.warn("API: Dictionary system health check: ISSUES DETECTED - {} types without defaults, {} types with multiple defaults",
                        result.typesWithoutDefault().size(), result.typesWithMultipleDefaults().size());
            }

            return dictionaryApiConvertor.toHealthResp(result);

        } catch (Exception e) {
            log.error("API: Failed to get dictionary health status: error={}", e.getMessage(), e);
            throw e;
        }
    }
}
