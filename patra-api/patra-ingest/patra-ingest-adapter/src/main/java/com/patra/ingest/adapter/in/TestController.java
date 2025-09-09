package com.patra.ingest.adapter.in;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.model.registry.ProvenanceConfigSnapshot;
import com.patra.ingest.app.port.outbound.PatraRegistryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TestController {

    private final PatraRegistryPort registryPort;

    @GetMapping("/test")
    public String test() {
        ProvenanceConfigSnapshot snapshot = registryPort.getProvenanceConfigSnapshot(ProvenanceCode.PUBMED);
        log.info("Fetched Provenance Config: {}", snapshot);
        return "Ingest Service is running!";
    }
}
