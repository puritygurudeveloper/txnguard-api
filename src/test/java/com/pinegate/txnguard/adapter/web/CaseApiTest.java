package com.pinegate.txnguard.adapter.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The analyst queue, exercised through HTTP against a real database. */
@SpringBootTest
@AutoConfigureMockMvc
class CaseApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    /** Screen enough traffic to guarantee a case, and return its id. */
    private String raiseCase() throws Exception {
        String cardholder = "card-" + UUID.randomUUID();
        for (int i = 0; i < 6; i++) {
            screen(cardholder, "18.00", "US", "5411");
        }
        Map<String, Object> suspicious = body(cardholder, "7500.00", "RO", "7995");
        String transactionId = (String) suspicious.get("transactionId");
        mvc.perform(post("/api/v1/screening").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(suspicious)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value(org.hamcrest.Matchers.not("APPROVE")));

        JsonNode queue = json.readTree(mvc.perform(get("/api/v1/cases?limit=200"))
                .andReturn().getResponse().getContentAsString());
        for (JsonNode node : queue) {
            if (node.get("transactionId").asText().equals(transactionId)) {
                return node.get("caseId").asText();
            }
        }
        throw new AssertionError("no case was raised for " + transactionId);
    }

    private Map<String, Object> body(String cardholder, String amount, String country, String mcc) {
        Map<String, Object> request = new HashMap<>();
        request.put("transactionId", UUID.randomUUID().toString());
        request.put("cardholderId", cardholder);
        request.put("merchantId", "merch-" + UUID.randomUUID());
        request.put("merchantCategoryCode", mcc);
        request.put("amount", amount);
        request.put("currency", "USD");
        request.put("channel", "ECOMMERCE");
        request.put("countryCode", country);
        return request;
    }

    /** Named `screen`, not `post`: a helper called `post` shadows MockMvcRequestBuilders.post. */
    private void screen(String cardholder, String amount, String country, String mcc) throws Exception {
        mvc.perform(post("/api/v1/screening").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body(cardholder, amount, country, mcc))));
    }

    @Test
    void walksACaseThroughItsWholeLifecycle() throws Exception {
        String caseId = raiseCase();

        mvc.perform(get("/api/v1/cases/" + caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignedTo").doesNotExist());

        mvc.perform(post("/api/v1/cases/" + caseId + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"analyst":"aisha"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.assignedTo").value("aisha"));

        mvc.perform(post("/api/v1/cases/" + caseId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome":"CONFIRMED_FRAUD","notes":"cardholder confirmed by phone"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED_FRAUD"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
    }

    @Test
    void refusesToReopenAResolvedCase() throws Exception {
        String caseId = raiseCase();
        mvc.perform(post("/api/v1/cases/" + caseId + "/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"outcome":"CLEARED","notes":"customer verified the purchase"}"""));

        mvc.perform(post("/api/v1/cases/" + caseId + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"analyst":"bo"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Case already resolved"));
    }

    @Test
    void refusesAResolutionWithNoNotes() throws Exception {
        String caseId = raiseCase();
        mvc.perform(post("/api/v1/cases/" + caseId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome":"CLEARED","notes":"  "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void returnsNotFoundForAnUnknownCase() throws Exception {
        mvc.perform(get("/api/v1/cases/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Case not found"));
    }

    @Test
    void rejectsAnAbsurdQueueLimit() throws Exception {
        mvc.perform(get("/api/v1/cases?limit=99999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordersTheQueueByRiskThenAge() throws Exception {
        raiseCase();
        raiseCase();
        JsonNode queue = json.readTree(mvc.perform(get("/api/v1/cases?limit=50"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(queue.size()).isGreaterThanOrEqualTo(2);
        int previous = Integer.MAX_VALUE;
        for (JsonNode node : queue) {
            int score = node.get("riskScore").asInt();
            assertThat(score).isLessThanOrEqualTo(previous);
            previous = score;
        }
    }
}
