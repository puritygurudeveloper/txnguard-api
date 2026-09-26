package com.pinegate.txnguard.application.port;

import com.pinegate.txnguard.domain.CardholderProfile;
import com.pinegate.txnguard.domain.Transaction;

/**
 * Outbound port: where behavioural history comes from.
 *
 * <p>Declared in the application layer and implemented in the adapter layer, so the
 * domain depends on this interface and never on JPA. Swapping the H2-backed
 * implementation for a feature store is a change in one package.
 */
public interface TransactionHistory {

    CardholderProfile profileFor(String cardholderId);

    void record(Transaction transaction);
}
