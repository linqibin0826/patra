package com.patra.registry.infra.persistence.repository;

import com.patra.registry.domain.model.aggregate.PlatformFieldDict;
import com.patra.registry.domain.port.PlatformFieldDictRepository;
import com.patra.registry.infra.mapstruct.PlatformFieldDictConverter;
import com.patra.registry.infra.persistence.mapper.PlatformFieldDictMapper;
import com.patra.registry.infra.persistence.entity.PlatformFieldDictDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 平台字段字典仓储实现
 * docref: /docs/domain/port/PlatformFieldDictRepository.txt
 */
@Repository
@RequiredArgsConstructor
public class PlatformFieldDictRepositoryImpl implements PlatformFieldDictRepository {
    
    private final PlatformFieldDictMapper mapper;
    private final PlatformFieldDictConverter converter;
    
    @Override
    public Optional<PlatformFieldDict> findByFieldKey(String fieldKey) {
    var q = new LambdaQueryWrapper<PlatformFieldDictDO>()
        .eq(PlatformFieldDictDO::getFieldKey, fieldKey)
        .last("limit 1");
    var doObj = mapper.selectOne(q);
    return Optional.ofNullable(doObj).map(converter::toAggregate);
    }
    
    @Override
    public Optional<PlatformFieldDict> findById(Long id) {
    var doObj = mapper.selectById(id);
    return Optional.ofNullable(doObj).map(converter::toAggregate);
    }
    
    @Override
    public PlatformFieldDict save(PlatformFieldDict aggregate) {
        PlatformFieldDictDO toSave = converter.toDO(aggregate);
        if (toSave.getId() == null) {
            mapper.insert(toSave);
        } else {
            mapper.updateById(toSave);
        }
        return converter.toAggregate(toSave);
    }
    
    @Override
    public List<PlatformFieldDict> findAll(int offset, int limit) {
    var q = new LambdaQueryWrapper<PlatformFieldDictDO>()
        .orderByAsc(PlatformFieldDictDO::getFieldKey)
        .last("limit " + Math.max(0, limit) + " offset " + Math.max(0, offset));
    var list = mapper.selectList(q);
    return converter.toAggregateList(list);
    }
    
    @Override
    public void deleteByFieldKey(String fieldKey) {
    var q = new LambdaQueryWrapper<PlatformFieldDictDO>()
        .eq(PlatformFieldDictDO::getFieldKey, fieldKey);
    mapper.delete(q);
    }
    
    @Override
    public boolean existsByFieldKey(String fieldKey) {
    var q = new LambdaQueryWrapper<PlatformFieldDictDO>()
        .eq(PlatformFieldDictDO::getFieldKey, fieldKey);
    Long cnt = mapper.selectCount(q);
    return cnt != null && cnt > 0;
    }
}
