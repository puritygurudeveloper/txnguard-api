package com.pinegate.txnguard.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through the real HTTP stack, the real rule engine and a real H2 database.
 * Nothing is mocked: these are the tests that would have caught a broken bean wiring or
 * a JPA mapping that does not round-trip.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScreeningApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    private Map<String, Object> request(String cardholder, String amount) {
        return Map.of(
                "transactionId", UUID.randomUUID().toString(),
                "cardholderId", cardholder,
                "merchantId", "merch-" + UUID.randomUUID(),
                "merchantCategoryCode", "5411",
                "amount", amount,
                "currency", "USD",
                "channel", "ECOMMERCE",
                "countryCode", "US");
    }

    private String screen(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/v1/screening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("an ordinary first transaction is approved")
    void approvesAnUnremarkableTransaction() throws Exception {
        String cardholder = "card-" + UUID.randomUUID();
        mvc.perform(post("/api/v1/screening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request(cardholder, "42.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.riskScore").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("a declined transaction is still a 200 — the screening call succeeded")
    void returnsOkForADecline() throws Exception {
        String cardholder = "card-" + UUID.randomUUID();
        // Build a baseline of small purchases, then present something enormous at a
        // brand new merchant in a brand new country.
        for (int i = 0; i < 6; i++) {
            screen(request(cardholder, "20.00"));
        }
        Map<String, Object> suspicious = new java.util.HashMap<>(request(cardholder, "9000.00"));
        suspicious.put("countryCode", "RO");
        suspicious.put("merchantCategoryCode", "6051");

        mvc.perform(post("/api/v1/screening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(suspicious)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskScore").value(greaterThanOrEqualTo(40)))
                .andExpect(jsonPath("$.signals[*].code").value(hasItem("AMOUNT_DEVIATION")))
                .andExpect(jsonPath("$.signals[0].explanation").isNotEmpty());
    }

    @Test
    @DisplayName("screening the same transaction twice raises only one case")
    void isIdempotentOnTransactionId() throws Exception {
        String cardholder = "card-" + UUID.randomUUID();
        for (int i = 0; i < 6; i++) {
            screen(request(cardholder, "15.00"));
        }
        Map<String, Object> big = new java.util.HashMap<>(request(cardholder, "4000.00"));
        big.put("countryCode", "BG");

        long before = openCaseCount();
        screen(big);
        long afterFirst = openCaseCount();
        screen(big);   // the network retried
        long afterRetry = openCaseCount();

        assertOneCaseRaised(before, afterFirst, afterRetry);
    }

    private void assertOneCaseRaised(long before, long afterFirst, long afterRetry) {
        org.assertj.core.api.Assertions.assertThat(afterFirst).isEqualTo(before + 1);
        org.assertj.core.api.Assertions.assertThat(afterRetry).isEqualTo(afterFirst);
    }

    private long openCaseCount() throws Exception {
        String body = mvc.perform(get("/api/v1/cases/count"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("open").asLong();
    }

    @Test
    void rejectsAMalformedRequestWithProblemDetail() throws Exception {
        Map<String, Object> bad = new java.util.HashMap<>(request("card-x", "10.00"));
        bad.put("merchantCategoryCode", "54");     // not four digits
        bad.put("countryCode", "USA");             // not alpha-2

        mvc.perform(post("/api/v1/screening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("countryCode")))
                .andExpect(jsonPath("$.detail").value(containsString("merchantCategoryCode")));
    }

    @Test
    void rejectsANegativeAmount() throws Exception {
        Map<String, Object> bad = new java.util.HashMap<>(request("card-y", "-1.00"));
        mvc.perform(post("/api/v1/screening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesTheActiveRuleSet() throws Exception {
        mvc.perform(get("/api/v1/screening/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules").value(contains(
                        "AMOUNT_DEVIATION", "HIGH_RISK_MCC", "IMPOSSIBLE_TRAVEL",
                        "UNFAMILIAR_CONTEXT", "VELOCITY")));
    }
}
