package com.patra.ingest.adapter.out.registry;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.adapter.out.registry.converter.ProvenanceConfigSnapshotConverter;
import com.patra.ingest.app.port.outbound.ProvenancePort;
import com.patra.ingest.domain.exception.IngestConfigurationException;
import com.patra.ingest.domain.model.enums.Endpoint;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.registry.api.rpc.client.ProvenanceClient;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceConfigResp;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;

/**
 * 调用 patra-registry 的出站适配器，实现来源配置查询。
 *
 * <p>遵循六边形架构模式，作为基础设施层组件实现应用层端口。
 * 负责从 Registry 服务获取完整的 Provenance 配置快照，支持失败重试和错误处理。</p>
 *
 * <p>特性：
 * - 完整的错误处理和日志记录
 * - 使用 MapStruct 进行类型安全的数据转换
 * - 优雅降级，返回最小可用配置</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvenancePortAdapter implements ProvenancePort {

    private final ProvenanceClient provenanceClient;
    private final ProvenanceConfigSnapshotConverter converter;

    @Override
    public ProvenanceConfigSnapshot fetchConfig(ProvenanceCode provenanceCode, Endpoint endpointCode, OperationCode operationCode) {
        String code = provenanceCode.getCode();
        String endpoint = endpointCode.name();
        String taskType = operationCode.name(); // 在registry域中交taskType
        Instant queryTime = Instant.now();

        log.debug("开始获取来源配置, code={}, taskType={}, endpoint={}, at={}", 
                code, taskType, endpoint, queryTime);

        try {
            ProvenanceConfigResp resp = provenanceClient.getConfiguration(code, taskType, endpoint, queryTime);
            
            if (resp == null) {
                log.warn("Registry 返回空配置响应, provenanceCode={}, taskType={}, endpoint={}", 
                        code, taskType, endpoint);
                return createMinimalSnapshot(code);
            }

            ProvenanceConfigSnapshot snapshot = converter.convert(resp);
            
            log.debug("成功获取来源配置, code={}, snapshot={}", code, snapshot);
            return snapshot;
            
        } catch (FeignException ex) {
            return handleFeignException(ex, code, taskType, endpoint, taskType, endpoint);
        } catch (Exception ex) {
            String msg = String.format("获取来源配置时发生未知错误, code=%s, taskType=%s, endpoint=%s", 
                    code, taskType, endpoint);
            log.error(msg, ex);
            throw new IngestConfigurationException(code, taskType, endpoint, msg, ex);
        }
    }

    /**
     * 处理 Feign 异常。
     */
    private ProvenanceConfigSnapshot handleFeignException(FeignException ex, String code, 
                                                          String taskType, String endpoint,
                                                          String operationCode, String endpointName) {
        int status = ex.status();
        String responseBody = ex.contentUTF8();
        
        if (status == 404) {
            log.warn("来源配置未找到, code={}, taskType={}, endpoint={}, status={}", 
                    code, taskType, endpoint, status);
            return createMinimalSnapshot(code);
        }
        
        if (status >= 400 && status < 500) {
            String msg = String.format("Registry 客户端错误, code=%s, status=%d, response=%s", 
                    code, status, responseBody);
            log.error(msg);
            throw new IngestConfigurationException(code, operationCode, endpointName, msg, ex);
        }
        
        if (status >= 500 && status < 600) {
            String msg = String.format("Registry 服务端错误, code=%s, status=%d, response=%s", 
                    code, status, responseBody);
            log.error(msg);
            log.warn("Registry 服务不可用，使用最小配置降级, provenanceCode={}", code);
            return createMinimalSnapshot(code);
        }
        
        String msg = String.format("Registry 调用失败, code=%s, status=%d, response=%s", 
                code, status, responseBody);
        log.error(msg);
        throw new IngestConfigurationException(code, operationCode, endpointName, msg, ex);
    }

    /**
     * 创建最小可用配置快照。
     */
    private ProvenanceConfigSnapshot createMinimalSnapshot(String provenanceCode) {
        log.info("创建最小可用配置快照, provenanceCode={}", provenanceCode);
        
        // 创建最小的 ProvenanceInfo
        ProvenanceConfigSnapshot.ProvenanceInfo minimalProvenance = 
                new ProvenanceConfigSnapshot.ProvenanceInfo(
                        null, // id
                        provenanceCode, // code
                        null, // name
                        null, // baseUrlDefault
                        null, // timezoneDefault
                        null, // docsUrl
                        true, // active
                        null  // lifecycleStatusCode
                );
        
        return new ProvenanceConfigSnapshot(
                minimalProvenance,
                null, // endpoint
                null, // windowOffset
                null, // pagination
                null, // http
                null, // batching
                null, // retry
                null, // rateLimit
                Collections.emptyList() // credentials
        );
    }

    /**
     * 规范化任务类型。
     */
    private String normalizeTaskType(String operationCode) {
        if (operationCode == null) {
            return null;
        }
        String trimmed = operationCode.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    /**
     * 规范化端点名称。
     */
    private String normalizeEndpointName(String endpointName) {
        if (endpointName == null) {
            return null;
        }
        String trimmed = endpointName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
