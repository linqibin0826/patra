package com.patra.registry.app.view;

import com.patra.registry.domain.model.enums.LiteratureProvenanceCode;

/**
 * 数据来源概要
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceSummary(Long id, LiteratureProvenanceCode code, String name) {

}
