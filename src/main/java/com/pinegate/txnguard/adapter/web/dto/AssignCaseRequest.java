package com.pinegate.txnguard.adapter.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for assigning a case to an analyst. */
public record AssignCaseRequest(@NotBlank String analyst) {}
