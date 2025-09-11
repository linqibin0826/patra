package com.patra.registry.adapter.rest._internal.client;

import com.patra.registry.api.rpc.contract.PlatformFieldDictHttpApi;
import com.patra.registry.api.rpc.dto.PlatformFieldDictApiResp;
import com.patra.registry.app.usecase.PlatformFieldDictQueryUseCase;
import com.patra.registry.adapter.rest._internal.converter.PlatformFieldDictApiConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台字段字典内部 HTTP 提供方实现。
 *
 * <p>适配层仅做参数接收、日志与模型转换，不包含业务逻辑。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PlatformFieldDictApiImpl implements PlatformFieldDictHttpApi {

    private final PlatformFieldDictQueryUseCase useCase;
    private final PlatformFieldDictApiConverter converter;

    @Override
    public List<PlatformFieldDictApiResp> listAll() {
        log.info("Received request to list platform field dict");
        var views = useCase.listAll();
        return converter.toApiRespList(views);
    }
}
