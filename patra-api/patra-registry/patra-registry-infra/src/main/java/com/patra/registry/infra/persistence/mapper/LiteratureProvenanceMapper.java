package com.patra.registry.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.LiteratureProvenanceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LiteratureProvenanceMapper extends BaseMapper<LiteratureProvenanceDO> {

    /**
     * 查询所有文献数据源的概要信息
     * @return 文献数据源概要信息列表
     */
    List<LiteratureProvenanceDO> selectProvSummaryAll();
}
