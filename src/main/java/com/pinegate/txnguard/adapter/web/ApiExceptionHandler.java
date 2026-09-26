package com.pinegate.txnguard.adapter.web;

import com.pinegate.txnguard.application.CaseService;
import com.pinegate.txnguard.domain.Money;
import com.pinegate.txnguard.domain.ReviewCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Maps domain failures onto RFC 7807 problem responses.
 *
 * <p>The mapping lives here rather than in the controllers so that a rule added to the
 * domain cannot accidentally surface as a 500. Client errors are logged at debug and
 * conflicts at warn; only genuinely unexpected failures are logged at error, which keeps
 * the error log meaningful enough to alert on.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CaseService.CaseNotFoundException.class)
    ProblemDetail handleNotFound(CaseService.CaseNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Case not found", e.getMessage());
    }

    @ExceptionHandler(ReviewCase.CaseAlreadyResolvedException.class)
    ProblemDetail handleConflict(ReviewCase.CaseAlreadyResolvedException e) {
        log.warn("rejected write to a resolved case: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, "Case already resolved", e.getMessage());
    }

    @ExceptionHandler(Money.CurrencyMismatchException.class)
    ProblemDetail handleCurrencyMismatch(Money.CurrencyMismatchException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Currency mismatch", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(f -> f.getField()))
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", detail);
    }

    /**
     * Domain invariant violations become 400, not 500.
     *
     * <p>A blank cardholder id or an out-of-range latitude is the caller's mistake; the
     * server behaved correctly by refusing it.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        log.debug("rejected malformed request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(detail);
        return problem;
    }
}
