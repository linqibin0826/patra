package com.patra.ingest.app.service;

import com.patra.ingest.app.service.router.IngestRouter;
import com.patra.ingest.app.usecase.IngestUseCase;
import com.patra.ingest.app.usecase.command.IngestCommand;
import com.patra.ingest.app.view.JobView;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class IngestAppService implements IngestUseCase {

    private final IngestRouter router;

    @Override
    public JobView start(IngestCommand cmd) {
        return router.route(cmd).handleCommand(cmd);
    }
}
