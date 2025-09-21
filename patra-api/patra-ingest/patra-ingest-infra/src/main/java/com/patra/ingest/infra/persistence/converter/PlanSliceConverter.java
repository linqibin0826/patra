package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.entity.PlanSlice;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.vo.SliceSpec;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import org.springframework.stereotype.Component;

@Component
public class PlanSliceConverter {
    public PlanSlice toDomain(PlanSliceDO source){
        if(source==null) return null;
        SliceStatus status = source.getStatusCode()==null? null : SliceStatus.valueOf(source.getStatusCode());
        // sliceSpec 先不反序列化为复杂结构，留待后续实现
        SliceSpec spec = null;
        return new PlanSlice(
            source.getId(),
            source.getPlanId(),
            source.getProvenanceCode(),
            source.getSliceNo(),
            source.getSliceSignatureHash(),
            source.getExprHash(),
            spec,
            status
        );
    }
    public PlanSliceDO toDO(PlanSlice slice){
        if(slice==null) return null;
        return PlanSliceDO.builder()
            .id(slice.getId())
            .planId(slice.getPlanId())
            .provenanceCode(slice.getProvenanceCode())
            .sliceNo(slice.getSliceNo())
            .sliceSignatureHash(slice.getSliceSignatureHash())
            .exprHash(slice.getExprHash())
            .sliceSpec(null) // TODO serialize slice.spec
            .statusCode(slice.getStatus()==null? null : slice.getStatus().name())
            .build();
    }
}
