package com.ttareureung.api.domain.push.client;

// 푸시 서비스(브라우저 회사 서버)로 한 건 발송. 테스트에서 가짜 구현으로 교체
public interface PushGateway {

    boolean enabled();

    // 반환값은 HTTP 상태 코드. 404·410이면 만료된 구독
    int send(String endpoint, String p256dh, String auth, byte[] payload);
}
