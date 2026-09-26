package com.pinegate.txnguard.adapter.web.dto;

import com.pinegate.txnguard.domain.CaseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for resolving a case. Notes are mandatory, not a courtesy. */
public record ResolveCaseRequest(
        @NotNull CaseStatus outcome,
        @NotBlank @Size(max = 2000) String notes) {}
