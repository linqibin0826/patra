package com.patra.ingest.app.service.handler;

import com.patra.ingest.app.usecase.command.IngestCommand;
import com.patra.ingest.app.view.JobView;

/**
 * 处理器总接口：不同来源/类型的组合可有不同实现
 */
public interface IngestHandler<C extends IngestCommand> {
    JobView handle(C cmd);

    default JobView handleCommand(IngestCommand cmd) {
        return handle((C) cmd);
    }
}
