package com.patra.ingest.adapter.outbound.rest;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.adapter.outbound.rest.converter.ProvenanceConfigSnapshotConverter;
import com.patra.ingest.app.port.ProvenancePort;
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
/**
 * Registry 出站适配器：负责访问 patra-registry 获取来源配置。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvenancePortAdapter implements ProvenancePort {

    /** Registry RPC 客户端 */
    private final ProvenanceClient provenanceClient;
    /** 配置快照转换器 */
    private final ProvenanceConfigSnapshotConverter converter;

    /**
     * 调用 Registry 获取来源配置。
     */
    @Override
    public ProvenanceConfigSnapshot fetchConfig(ProvenanceCode provenanceCode, Endpoint endpointCode, OperationCode operationCode) {
        String code = provenanceCode.getCode();
        String endpoint = endpointCode.name();
        String taskType = operationCode.name();
        Instant queryTime = Instant.now();

        log.debug("Requesting provenance config, code={}, taskType={}, endpoint={}, at={}",
                code, taskType, endpoint, queryTime);

        try {
            ProvenanceConfigResp resp = provenanceClient.getConfiguration(provenanceCode, taskType, endpoint, queryTime);

            if (resp == null) {
                log.warn("Registry returned empty config, code={}, taskType={}, endpoint={}", code, taskType, endpoint);
                return createMinimalSnapshot(code);
            }

            ProvenanceConfigSnapshot snapshot = converter.convert(resp);

            log.debug("Provenance config loaded, code={}, snapshot={}", code, snapshot);
            return snapshot;

        } catch (FeignException ex) {
            return handleFeignException(ex, code, taskType, endpoint);
        } catch (Exception ex) {
            String msg = String.format("Unexpected error when fetching config, code=%s, taskType=%s, endpoint=%s",
                    code, taskType, endpoint);
            log.error(msg, ex);
            throw new IngestConfigurationException(code, taskType, endpoint, msg, ex);
        }
    }

    /**
     * 处理 Feign 异常。
     */
    private ProvenanceConfigSnapshot handleFeignException(FeignException ex, String code, 
                                                          String taskType, String endpoint) {
        int status = ex.status();
        String responseBody = ex.contentUTF8();
        
        if (status == 404) {
            log.warn("Provenance config not found, code={}, taskType={}, endpoint={}, status={}",
                    code, taskType, endpoint, status);
            return createMinimalSnapshot(code);
        }

        if (status >= 400 && status < 500) {
            String msg = String.format("Registry client error, code=%s, status=%d, response=%s",
                    code, status, responseBody);
            log.error(msg);
            throw new IngestConfigurationException(code, taskType, endpoint, msg, ex);
        }

        if (status >= 500 && status < 600) {
            String msg = String.format("Registry server error, code=%s, status=%d, response=%s",
                    code, status, responseBody);
            log.error(msg);
            log.warn("Registry unavailable, fallback to minimal snapshot, code={}", code);
            return createMinimalSnapshot(code);
        }

        String msg = String.format("Registry call failed, code=%s, status=%d, response=%s",
                code, status, responseBody);
        log.error(msg);
        throw new IngestConfigurationException(code, taskType, endpoint, msg, ex);
    }

    /**
     * 创建最小可用配置快照。
     */
    private ProvenanceConfigSnapshot createMinimalSnapshot(String provenanceCode) {
        log.info("Creating minimal provenance snapshot, code={}", provenanceCode);
        
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

}
