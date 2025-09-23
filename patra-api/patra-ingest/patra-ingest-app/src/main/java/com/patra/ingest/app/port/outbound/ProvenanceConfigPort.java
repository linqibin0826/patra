package com.patra.ingest.app.port.outbound;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.EndpointCode;
import com.patra.ingest.domain.model.enums.OperationType;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;

public interface ProvenanceConfigPort {

    ProvenanceConfigSnapshot fetchConfig(ProvenanceCode provenanceCode, EndpointCode endpointCode, OperationType operationCode);
}
