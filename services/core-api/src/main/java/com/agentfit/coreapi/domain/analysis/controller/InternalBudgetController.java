package com.agentfit.coreapi.domain.analysis.controller;

import com.agentfit.coreapi.domain.analysis.service.InternalInputAccessService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/budget")
public class InternalBudgetController {
    private final InternalInputAccessService inputAccessService;

    public InternalBudgetController(InternalInputAccessService inputAccessService) {
        this.inputAccessService = inputAccessService;
    }

    @PostMapping("/reserve")
    public Map<String, Object> reserve(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody BudgetReserveRequest request) {
        return inputAccessService.reserveBudget(
                authorization,
                request.attemptId(),
                request.leaseId(),
                request.pages());
    }

    public record BudgetReserveRequest(long attemptId, String leaseId, int pages) { }
}
