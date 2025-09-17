package com.patra.ingest.app.service.slicing;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 切片策略工厂：根据数据源与采集类型选择合适的策略实现。
 * 可通过构造注入多实现，后续扩展其它数据源/类型时只需新增 Bean。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@RequiredArgsConstructor
public class SlicingStrategyFactory {

    private final List<SlicingStrategy> strategies;

    public SlicingStrategy select(ProvenanceCode provenance, IngestOperationType operation) {
        return strategies.stream()
                .filter(s -> s.supports(provenance, operation))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No slicing strategy for " + provenance + "/" + operation));
    }
}
