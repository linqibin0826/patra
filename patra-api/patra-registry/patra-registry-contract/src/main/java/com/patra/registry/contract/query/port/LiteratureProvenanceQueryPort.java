package com.patra.registry.contract.query.port;


import com.patra.registry.contract.query.view.ProvenanceSummaryView;

import java.util.List;

public interface LiteratureProvenanceQueryPort {

    List<ProvenanceSummaryView> findAll();
}
