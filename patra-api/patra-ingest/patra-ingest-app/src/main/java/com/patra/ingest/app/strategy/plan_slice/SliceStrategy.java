package com.patra.ingest.app.strategy.plan_slice;

import com.patra.ingest.app.strategy.plan_slice.model.SliceContext;
import com.patra.ingest.app.strategy.plan_slice.model.SliceDraft;
import java.util.List;

public interface SliceStrategy {
    String code();
    List<SliceDraft> slice(SliceContext context);
}
