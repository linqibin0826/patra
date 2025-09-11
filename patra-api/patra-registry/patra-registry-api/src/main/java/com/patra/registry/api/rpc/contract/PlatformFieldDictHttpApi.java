package com.patra.registry.api.rpc.contract;

import com.patra.registry.api.rpc.dto.PlatformFieldDictApiResp;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 平台字段字典 HTTP 契约（只读）。
 *
 * 不需要入参，返回全量/当前可用的字段字典列表。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlatformFieldDictHttpApi {

    String BASE_PATH = "/_internal/platform-field-dict";

    @GetMapping(BASE_PATH)
    List<PlatformFieldDictApiResp> listAll();
}
