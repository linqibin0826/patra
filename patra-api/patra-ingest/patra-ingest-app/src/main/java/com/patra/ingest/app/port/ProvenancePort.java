package com.patra.ingest.app.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.Endpoint;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;

public interface ProvenancePort {

    ProvenanceConfigSnapshot fetchConfig(ProvenanceCode provenanceCode,
                                         Endpoint endpoint,
                                         OperationCode operationCode
    );
}
