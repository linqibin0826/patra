package com.patra.ingest.app.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.Endpoint;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;

/**
 * 来源配置查询端口。
 * <p>应用层依赖该端口向外部服务（如 patra-registry）获取指定来源、端点与操作的配置快照。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ProvenancePort {

    /**
     * 查询来源配置快照。
     *
     * @param provenanceCode 来源代码
     * @param endpoint       端点枚举
     * @param operationCode  操作类型
     * @return 配置快照（可能为空）
     */
    ProvenanceConfigSnapshot fetchConfig(ProvenanceCode provenanceCode,
                                         Endpoint endpoint,
                                         OperationCode operationCode
    );
}
