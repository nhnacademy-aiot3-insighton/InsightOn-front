package com.nhnacademy.insightonfront.adapter.core.weather.dto;

/** pm10Grade/pm25Grade: 좋음 / 보통 / 나쁨 / 매우나쁨(없으면 "정보없음") — 순수 String, enum 아님. */
public record AirQualityDto(String pm10Value, String pm25Value, String pm10Value24, String pm25Value24,
                             String pm10Grade, String pm25Grade) {
}
