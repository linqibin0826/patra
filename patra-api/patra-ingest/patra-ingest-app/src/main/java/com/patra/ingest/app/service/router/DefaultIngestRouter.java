package com.patra.ingest.app.service.router;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.service.handler.IngestHandler;
import com.patra.ingest.app.usecase.command.HarvestCommand;
import com.patra.ingest.app.usecase.command.IngestCommand;
import lombok.RequiredArgsConstructor;
import java.util.Map;

@RequiredArgsConstructor
public class DefaultIngestRouter implements IngestRouter {

    private final Map<ProvenanceCode, IngestHandler<HarvestCommand>> harvestHandlersByProv;

    @Override
    public IngestHandler<? extends IngestCommand> route(IngestCommand cmd) {
        if (cmd instanceof HarvestCommand hc) {
            var handler = harvestHandlersByProv.get(hc.provenance());
            if (handler == null) throw new IllegalArgumentException("No Harvest handler for provenance=" + hc.provenance().getCode());
            return handler;
        }
        throw new UnsupportedOperationException("Command type not supported yet: " + cmd.getClass().getSimpleName());
    }
}
