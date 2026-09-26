package com.pinegate.txnguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TxnGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(TxnGuardApplication.class, args);
    }
}
