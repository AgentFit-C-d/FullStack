package com.agentfit.coreapi.domain.project.dto.request;
public record ProjectCreateRequestParts(ProjectCreateMetadataRequest metadata, byte[] fileBytes, String fileName, String contentType, String inputText) { }
