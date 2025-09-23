package com.patra.ingest.adapter.out.registry;

import com.patra.ingest.adapter.out.registry.converter.ProvenanceConfigSnapshotConverter;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceConfigResp;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;

/**
 * ProvenanceConfigPortAdapter 编译验证测试。
 *
 * @author linqibin
 * @since 0.1.0
 */
@SpringBootTest
class ProvenanceConfigPortAdapterCompileTest {

    @Test
    void testProvenanceConfigSnapshotInstantiation() {
        // 验证 ProvenanceConfigSnapshot 可以正常实例化
        ProvenanceConfigSnapshot snapshot = new ProvenanceConfigSnapshot(
                "PUBMED",
                null,
                null,
                Collections.emptyList(),
                null,
                null,
                null,
                null,
                null
        );
        
        System.out.println("ProvenanceConfigSnapshot created successfully: " + snapshot.provenanceCode());
    }
    
    @Test
    void testConverterInterface() {
        // 这里只是验证编译通过，实际测试需要实现类
        ProvenanceConfigSnapshotConverter converter = null;
        System.out.println("Converter interface is available");
    }
}