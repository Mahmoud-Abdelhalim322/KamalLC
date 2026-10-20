package com.kamal.lc.captainapp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Stand-in until an SMS provider is wired: the code goes to the server log. */
@Slf4j
@Configuration
class OtpSenderConfig {

    @Bean
    @ConditionalOnMissingBean(OtpSender.class)
    OtpSender loggingOtpSender() {
        return (phone, code) -> log.warn("No SMS provider configured — captain OTP for {} is {}", phone, code);
    }
}
