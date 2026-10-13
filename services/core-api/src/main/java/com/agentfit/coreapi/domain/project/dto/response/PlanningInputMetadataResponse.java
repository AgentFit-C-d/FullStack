package com.agentfit.coreapi.domain.project.dto.response;
import com.agentfit.coreapi.domain.project.entity.PlanningInput;
public record PlanningInputMetadataResponse(long revision,String inputType,String fileName,String mediaType,Long byteSize,Integer characterCount,Integer pageCount) { public static PlanningInputMetadataResponse from(PlanningInput i){return i==null?null:new PlanningInputMetadataResponse(i.revision(),i.inputType(),i.fileName(),i.mediaType(),i.byteSize(),i.characterCount(),i.pageCount());} }
