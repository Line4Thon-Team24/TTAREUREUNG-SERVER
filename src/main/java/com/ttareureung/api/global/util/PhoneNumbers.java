package com.ttareureung.api.global.util;

public class PhoneNumbers {
    public static String normalize(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        return phoneNumber.replaceAll("[^0-9]", "");
    }
}
