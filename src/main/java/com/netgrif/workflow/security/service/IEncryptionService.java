package com.netgrif.workflow.security.service;

public interface IEncryptionService {

    String encrypt(String value);

    String encrypt(String value, String algorithm);

    String decrypt(String value);

    String decrypt(String value, String algorithm);
}