// ingest-app
package com.patra.ingest.app.port.outbound;


import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.model.registry.ProvenanceConfigSnapshot;

/**
 * 只暴露中立模型与简单入参，屏蔽外部契约
 */
public interface PatraRegistryProvenancePort {

    ProvenanceConfigSnapshot getProvenanceConfigSnapshot(ProvenanceCode provenanceCode);
}
