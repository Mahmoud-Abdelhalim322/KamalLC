package com.kamal.lc.captainapp.service;

/**
 * Delivers a captain's sign-in code. Swap in an SMS gateway by declaring another bean of
 * this type; until then {@link LoggingOtpSender} is used.
 */
public interface OtpSender {

    void send(String phone, String code);
}
