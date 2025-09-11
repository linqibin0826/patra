package com.patra.registry.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.registry.contract.query.port.PlatformFieldDictQueryPort;
import com.patra.registry.contract.query.view.PlatformFieldDictView;
import com.patra.registry.infra.mapstruct.PlatformFieldDictQueryConverter;
import com.patra.registry.infra.persistence.entity.PlatformFieldDictDO;
import com.patra.registry.infra.persistence.mapper.PlatformFieldDictMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 平台字段字典查询端口实现（infra 读侧）。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Repository
@RequiredArgsConstructor
public class PlatformFieldDictQueryPortImpl implements PlatformFieldDictQueryPort {

    private final PlatformFieldDictMapper mapper;
    private final PlatformFieldDictQueryConverter converter;

    @Override
    public List<PlatformFieldDictView> findAll() {
        var list = mapper.selectList(new LambdaQueryWrapper<PlatformFieldDictDO>()
                .orderByAsc(PlatformFieldDictDO::getFieldKey));
        return converter.toViewList(list);
    }
}
