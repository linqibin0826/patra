package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.aggregate.PlanSlice;
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
        SliceSpec spec = null; // TODO: 从source.getSliceSpec() JSON反序列化
        return new PlanSlice(
            source.getId(),
            source.getPlanId(),
            source.getProvenanceCode(),
            source.getSliceNo(),
            source.getSliceSignatureHash(),
            spec,
            source.getExprHash(),
            source.getExprSnapshot(),
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
            .sliceSpec(null) // TODO: 序列化slice.getSliceSpec()为JSON
            .exprHash(slice.getExprHash())
            .exprSnapshot(slice.getExprSnapshot())
            .statusCode(slice.getStatus()==null? null : slice.getStatus().name())
            .build();
    }
}
