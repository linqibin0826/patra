package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * {@code reg_provenance} 表的只读 Mapper。
 */

public interface RegProvenanceMapper extends BaseMapper<RegProvenanceDO> {

    @Select("SELECT * FROM reg_provenance WHERE provenance_code = #{code} AND deleted = 0")
    Optional<RegProvenanceDO> selectByCode(@Param("code") String code);

    @Select("SELECT * FROM reg_provenance WHERE deleted = 0 ORDER BY provenance_code")
    List<RegProvenanceDO> selectAllActive();
}
