package com.patra.registry.app.usecase;

import java.util.List;

/**
 * 平台字段字典查询用例。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlatformFieldDictQueryUseCase {
    List<com.patra.registry.contract.query.view.PlatformFieldDictView> listAll();
}
