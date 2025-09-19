package com.patra.registry.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.contract.query.view.LiteratureProvenanceConfigView;
import com.patra.registry.infra.persistence.entity.ProvenanceDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LiteratureProvenanceMapper extends BaseMapper<ProvenanceDO> {

    /**
     * 查询所有文献数据源的概要信息
     * @return 文献数据源概要信息列表
     */
    List<ProvenanceDO> selectProvSummaryAll();

    /**
     * 根据数据源代码查询数据源配置
     * @param code 数据源代码
     * @return 数据源配置视图
     */
    LiteratureProvenanceConfigView selectConfigByCode(@Param("code") String code);
}
