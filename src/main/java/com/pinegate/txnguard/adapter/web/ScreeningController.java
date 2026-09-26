package com.pinegate.txnguard.adapter.web;

import com.pinegate.txnguard.adapter.web.dto.ScreeningRequest;
import com.pinegate.txnguard.adapter.web.dto.ScreeningResponse;
import com.pinegate.txnguard.application.ScreeningService;
import com.pinegate.txnguard.domain.RiskAssessment;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/screening")
class ScreeningController {

    private final ScreeningService screening;
    private final Clock clock;

    ScreeningController(ScreeningService screening, Clock clock) {
        this.screening = screening;
        this.clock = clock;
    }

    /**
     * Screen one authorisation.
     *
     * <p>Returns 200 for every decision including {@code DECLINE}: the screening call
     * succeeded, and the decline is its answer. Using a 4xx for a declined payment
     * conflates "your request was wrong" with "the answer is no", and every client then
     * has to special-case it.
     */
    @PostMapping
    ResponseEntity<ScreeningResponse> screen(@Valid @RequestBody ScreeningRequest request) {
        RiskAssessment assessment = screening.screen(request.toDomain(clock.instant()));
        return ResponseEntity.ok(ScreeningResponse.from(assessment));
    }

    @GetMapping("/rules")
    Map<String, List<String>> activeRules() {
        return Map.of("rules", screening.activeRules());
    }
}
