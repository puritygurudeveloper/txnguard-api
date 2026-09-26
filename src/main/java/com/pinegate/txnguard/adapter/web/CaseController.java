package com.pinegate.txnguard.adapter.web;

import com.pinegate.txnguard.adapter.web.dto.AssignCaseRequest;
import com.pinegate.txnguard.adapter.web.dto.CaseView;
import com.pinegate.txnguard.adapter.web.dto.ResolveCaseRequest;
import com.pinegate.txnguard.application.CaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cases")
class CaseController {

    private final CaseService cases;

    CaseController(CaseService cases) {
        this.cases = cases;
    }

    @GetMapping
    List<CaseView> queue(@RequestParam(defaultValue = "50") int limit) {
        return cases.openQueue(limit).stream().map(CaseView::from).toList();
    }

    @GetMapping("/count")
    Map<String, Long> openCount() {
        return Map.of("open", cases.openCount());
    }

    @GetMapping("/{caseId}")
    CaseView get(@PathVariable String caseId) {
        return CaseView.from(cases.get(caseId));
    }

    @PostMapping("/{caseId}/assign")
    CaseView assign(@PathVariable String caseId, @Valid @RequestBody AssignCaseRequest request) {
        return CaseView.from(cases.assign(caseId, request.analyst()));
    }

    @PostMapping("/{caseId}/resolve")
    CaseView resolve(@PathVariable String caseId, @Valid @RequestBody ResolveCaseRequest request) {
        return CaseView.from(cases.resolve(caseId, request.outcome(), request.notes()));
    }
}
