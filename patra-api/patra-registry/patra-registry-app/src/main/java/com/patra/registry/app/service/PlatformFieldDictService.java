package com.patra.registry.app.service;

import com.patra.registry.domain.aggregate.PlatformFieldDict;
import com.patra.registry.app.port.out.PlatformFieldDictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 平台字段字典应用服务
 * docref: /docs/app/service/PlatformFieldDictAppService.txt
 */
@Service
@RequiredArgsConstructor
public class PlatformFieldDictService {

    private final PlatformFieldDictRepository repository;

    /**
     * 根据业务键查找平台字段字典
     */
    public Optional<PlatformFieldDict> findByFieldKey(String fieldKey) {
        return repository.findByFieldKey(fieldKey);
    }

    /**
     * 创建平台字段字典
     */
    public PlatformFieldDict create(PlatformFieldDict aggregate) {
        // TODO: 实现创建逻辑，包括业务校验
        return repository.save(aggregate);
    }

    /**
     * 更新平台字段字典
     */
    public PlatformFieldDict update(PlatformFieldDict aggregate) {
        // TODO: 实现更新逻辑，包括版本控制
        return repository.save(aggregate);
    }

    /**
     * 分页查询平台字段字典
     */
    public List<PlatformFieldDict> findAll(int offset, int limit) {
        return repository.findAll(offset, limit);
    }

    /**
     * 删除平台字段字典
     */
    public void deleteByFieldKey(String fieldKey) {
        repository.deleteByFieldKey(fieldKey);
    }
}
