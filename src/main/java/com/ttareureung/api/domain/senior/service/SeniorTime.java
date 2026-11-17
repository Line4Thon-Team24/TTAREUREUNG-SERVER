package com.ttareureung.api.domain.senior.service;

import java.time.ZoneId;

// 배포 서버가 UTC여도 "오늘"과 전화 시각은 한국 기준이어야 함
public final class SeniorTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private SeniorTime() {
    }
}
