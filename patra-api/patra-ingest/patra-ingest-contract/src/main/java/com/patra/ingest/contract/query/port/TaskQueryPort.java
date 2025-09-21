package com.patra.ingest.contract.query.port;

import com.patra.ingest.contract.query.view.TaskQuery;
import java.util.List;

public interface TaskQueryPort {
    List<TaskQuery> listByPlan(Long planId);
}
