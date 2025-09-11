package com.patra.registry.infra.mapstruct;

import com.patra.registry.domain.model.aggregate.PlatformFieldDict;
import com.patra.registry.infra.persistence.entity.PlatformFieldDictDO;
import org.mapstruct.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台字段字典聚合 MapStruct 转换器
 * docref: 红线规范 - MapStruct仅接口，@Mapper(componentModel="spring")
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
		unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlatformFieldDictConverter {

	// DO -> Aggregate
    @Mappings({
	    @Mapping(target = "recordRemarks", ignore = true)
    })
    PlatformFieldDict toAggregate(PlatformFieldDictDO src);

	// Aggregate -> DO
    @Mappings({
	    @Mapping(target = "recordRemarks", ignore = true)
    })
    PlatformFieldDictDO toDO(PlatformFieldDict src);

	// 批量：DO -> Aggregate
	default List<PlatformFieldDict> toAggregateList(List<PlatformFieldDictDO> list) {
		if (list == null || list.isEmpty()) return java.util.List.of();
		List<PlatformFieldDict> res = new ArrayList<>(list.size());
		for (PlatformFieldDictDO it : list) res.add(toAggregate(it));
		return res;
	}
}
