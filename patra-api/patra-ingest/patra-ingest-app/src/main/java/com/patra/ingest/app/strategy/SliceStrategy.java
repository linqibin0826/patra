package com.patra.ingest.app.strategy;

import com.patra.ingest.app.strategy.model.SliceContext;
import com.patra.ingest.app.strategy.model.SliceDraft;
import java.util.List;

public interface SliceStrategy {
    String code();
    List<SliceDraft> slice(SliceContext context);
}
