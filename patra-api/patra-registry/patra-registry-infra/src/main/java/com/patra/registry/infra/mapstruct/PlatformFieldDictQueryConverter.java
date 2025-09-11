package com.patra.registry.infra.mapstruct;

import com.patra.registry.contract.query.view.PlatformFieldDictView;
import com.patra.registry.infra.persistence.entity.PlatformFieldDictDO;
import org.mapstruct.*;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlatformFieldDictQueryConverter {

    @Mapping(target = "dataType", expression = "java(src.getDataType()==null?null:src.getDataType().name())")
    @Mapping(target = "cardinality", expression = "java(src.getCardinality()==null?null:src.getCardinality().name())")
    @Mapping(target = "datetype", expression = "java(src.getDatetype()==null?null:src.getDatetype().name())")
    PlatformFieldDictView toView(PlatformFieldDictDO src);

    default List<PlatformFieldDictView> toViewList(List<PlatformFieldDictDO> list) {
        if (list == null || list.isEmpty()) return java.util.List.of();
        List<PlatformFieldDictView> res = new ArrayList<>(list.size());
        for (PlatformFieldDictDO it : list) res.add(toView(it));
        return res;
    }
}
