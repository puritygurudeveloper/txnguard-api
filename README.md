# txnguard — real-time payment screening and fraud case management

A Spring Boot service that screens card authorisations against five fraud
heuristics, returns an explainable decision inside the authorisation window, and
raises analyst review cases for everything it cannot settle on its own.

Java 21, hexagonal architecture, 64 tests, 95% instruction coverage enforced in
CI.

```
POST /api/v1/screening
{"transactionId":"...","cardholderId":"...","amount":"9000.00","currency":"USD",
 "channel":"ECOMMERCE","countryCode":"RO","merchantCategoryCode":"6051", ...}

200 OK
{"riskScore": 81, "decision": "DECLINE", "signals": [
  {"code":"AMOUNT_DEVIATION","points":45,
   "explanation":"Amount 9000.00 USD is 450.0x the cardholder's median of 20.00 USD"},
  {"code":"UNFAMILIAR_CONTEXT","points":26,"explanation":"Unfamiliar: country RO, merchant merch-8f2a"},
  {"code":"HIGH_RISK_MCC","points":15,"explanation":"Merchant category 6051 carries elevated chargeback risk"}]}
```

## Every decision has to be explainable

A declined payment produces a customer call within minutes. "The model said so"
is not an answer an analyst can give, so a signal is not a score — it is a code,
a weight, and a sentence a human can read. The explanations above are generated,
not templated by hand, and they are what lands on the review case.

This constraint drives the architecture. A rule is a **pure function** of
`(transaction, profile, configuration)`:

```java
public interface FraudRule {
    String code();
    Optional<RuleSignal> evaluate(Transaction transaction, CardholderProfile profile);
}
```

No I/O, no clock, no mutable state. Everything a rule needs arrives in a
`CardholderProfile` snapshot assembled once per request. That is what makes a
six-month-old decision reproducible from the audit record during a chargeback
dispute, and it is why every rule is unit-testable without a Spring context.

## Design decisions worth defending

**A `DECLINE` returns HTTP 200.** The screening call succeeded; the decline is
its answer. Returning 4xx conflates "your request was malformed" with "the answer
is no", and every client then has to special-case it.

**The engine fails open.** If a rule throws, it is logged at `ERROR` and skipped,
and screening continues with the remaining four. A payment network has a hard
timeout, and declining every transaction in the estate because one heuristic has
a NullPointerException is a far worse outage than screening slightly less well.

**Scores saturate at 100.** Three independent strong signals must not dilute each
other into a pass, and they must not wrap.

**Money is never a `double`.** `Money` carries its currency, and mixing
currencies throws. A screening engine that compares a JPY amount against a USD
threshold does not fail — it silently approves a transaction a hundred times
over the limit. The scale is pinned to the currency's minor unit on construction,
so `10.00 USD` and `10.000 USD` are equal, which raw `BigDecimal.equals` denies.
Rounding is half-even; half-up biases a book of millions of transactions upward.

**Thresholds are configuration.** Fraud patterns move faster than release cycles.
`ScreeningProperties` validates itself on startup and refuses a configuration
where `declineThreshold <= reviewThreshold`, which would mean no transaction is
ever merely reviewed.

**`ReviewCase` is an immutable record.** Case state is the part of the system most
likely to be touched concurrently — two analysts working the same queue — so an
illegal interleaving is made impossible to express rather than merely unlikely.
The JPA entity is a separate class with `@Version` optimistic locking; letting
JPA's need for a no-arg constructor and mutable fields reach the aggregate would
forfeit the property that makes it safe.

## The rules

| rule | fires on | notes |
|---|---|---|
| `AMOUNT_DEVIATION` | amount ≥ 8× the cardholder's **median** | median, not mean: one prior large purchase would otherwise raise the bar enough to hide the next one, which is exactly the pattern a card tester produces. Points scale logarithmically, so 200× is not twenty times worse than 10× — both are "far outside normal" |
| `VELOCITY` | > 5 transactions in 10 minutes | the signature of an automated card-testing script |
| `IMPOSSIBLE_TRAVEL` | implied travel > 900 km/h | card-present **only**, both ends |
| `UNFAMILIAR_CONTEXT` | ≥ 2 of {new country, new merchant, young account} | any one alone is ordinary behaviour and scores nothing |
| `HIGH_RISK_MCC` | gambling, quasi-cash, money transfer | deliberately weak: the category is a property of the merchant, so scoring it highly declines every legitimate customer of an entire industry |

`IMPOSSIBLE_TRAVEL` applying only to card-present transactions is the one most
often got wrong. An e-commerce purchase carries the *merchant's* geolocation, not
the cardholder's. Treating it as a position fix raises an impossible-travel alert
every time somebody buys from an overseas website — a false positive rate high
enough to get the rule switched off, which is how a real control dies.

Distance uses Haversine rather than the spherical law of cosines, which loses
precision catastrophically at short range — precisely the regime that separates
two terminals in the same city from two in neighbouring countries.

## Two bugs the tests found

**A transaction counted itself.** Screening is idempotent on `transactionId`
because payment networks retry aggressively on timeout. But on replay the
transaction is *already in its own history*, and `VelocityRule` filtered peers by
timestamp alone — so the second delivery of a retried message counted itself
towards its own burst and could manufacture a decline. History windows now
exclude by transaction identity, not by time.

**An unreachable guard.** `ImpossibleTravelRule` had a branch for two
transactions at the same instant — real clock skew in a payment feed. It could
never fire, because `lastLocatedBefore` used a strict `isBefore`. The test
written to exercise the branch failed on the first run, which is the only reason
it was found: the code looked correct and was dead.

Both are in the commit log rather than quietly rewritten, because they are more
informative than the code that replaced them.

## Architecture

```
domain/           Money, Transaction, GeoPoint, ReviewCase, RiskAssessment
domain/rule/      FraudRule and the five implementations, RuleEngine
application/      ScreeningService, CaseService, and the outbound ports
adapter/web/      controllers, DTOs, RFC 7807 problem responses
adapter/persistence/  JPA case store, in-memory behavioural history
config/           ScreeningProperties, Clock bean
```

Dependencies point inwards. `application` declares the ports
(`TransactionHistory`, `CaseRepository`); `adapter` implements them. The domain
imports nothing from Spring except `@Component`, and swapping the in-memory
history for a real feature store is a one-class change.

The `Clock` is a bean rather than a static call, which is what makes assertions
about velocity windows and timestamps deterministic instead of flaky.

## Running it

```bash
./mvnw verify                 # 64 tests, coverage gate at 80% instruction / 70% branch
./mvnw spring-boot:run        # http://localhost:8080

docker build -t txnguard . && docker run -p 8080:8080 txnguard
```

CI builds on Java 21 and 22, enforces the coverage gate, then builds the image
and refuses to pass until the container reports `/actuator/health` as `UP` —
a packaging failure should not wait until deployment to surface.

## Scope

The in-memory `TransactionHistory` stands in for a feature store; it is bounded
at 100 transactions per cardholder and written to be correct under concurrency,
but it does not survive a restart. There is no authentication — in a real estate
this sits behind a gateway doing mTLS. Screening is synchronous and single-node;
production would need a shared velocity counter, since per-instance state lets an
attacker spread a burst across replicas.

The rules are hand-written heuristics, not a model. That is the right starting
point — they are explainable and need no labelled data — but the natural next
step is a gradient-boosted model scoring alongside them, with the rules retained
as a floor for the cases where a decline must be justified in writing.
