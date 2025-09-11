package com.patra.registry.app.service;

import com.patra.registry.app.usecase.PlatformFieldDictQueryUseCase;
import com.patra.registry.contract.query.port.PlatformFieldDictQueryPort;
import com.patra.registry.contract.query.view.PlatformFieldDictView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 平台字段字典查询应用服务。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
public class PlatformFieldDictQueryService implements PlatformFieldDictQueryUseCase {

    private final PlatformFieldDictQueryPort port;

    @Override
    public List<PlatformFieldDictView> listAll() {
        return port.findAll();
    }
}
