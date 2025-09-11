package com.patra.registry.contract.query.port;

import com.patra.registry.contract.query.view.PlatformFieldDictView;

import java.util.List;

/**
 * 平台字段字典查询端口（读侧）。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlatformFieldDictQueryPort {
    List<PlatformFieldDictView> findAll();
}
