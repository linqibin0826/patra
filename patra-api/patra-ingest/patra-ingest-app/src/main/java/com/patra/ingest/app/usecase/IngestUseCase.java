package com.patra.ingest.app.usecase;

import com.patra.ingest.app.usecase.command.IngestCommand;
import com.patra.ingest.app.view.JobView;

public interface IngestUseCase {
    JobView start(IngestCommand cmd); // adapter 会把外部 DTO 转成 IngestCommand 再调用
}
