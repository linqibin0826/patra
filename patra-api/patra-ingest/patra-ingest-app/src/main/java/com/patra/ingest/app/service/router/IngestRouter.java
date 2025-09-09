package com.patra.ingest.app.service.router;


import com.patra.ingest.app.service.handler.IngestHandler;
import com.patra.ingest.app.usecase.command.IngestCommand;

public interface IngestRouter {
    IngestHandler<? extends IngestCommand> route(IngestCommand cmd);
}
