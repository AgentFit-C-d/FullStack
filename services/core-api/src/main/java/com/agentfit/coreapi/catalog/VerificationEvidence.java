package com.agentfit.coreapi.catalog;

import java.time.LocalDate;

/** Source reviewed for a support check, combination, or permission mapping. */
public record VerificationEvidence(String sourceUrl, LocalDate checkedAt) {}
