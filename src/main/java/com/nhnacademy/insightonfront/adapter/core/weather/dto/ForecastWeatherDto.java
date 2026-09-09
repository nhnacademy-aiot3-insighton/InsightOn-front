package com.nhnacademy.insightonfront.adapter.core.weather.dto;

/**
 * 단기예보. skyStatus/forecastPrecipitationType은 core의 WeatherCodeMapper가 기상청 원본 코드를
 * 한글 문자열로 이미 변환해서 준다 — enum이 아니라 순수 String이라 예상 못 한 값이 오면
 * "알수없음(원본코드)" 형태로 그대로 온다(방어적으로 다뤄야 함).
 * <p>skyStatus: 맑음 / 구름많음 / 흐림. forecastPrecipitationType: 없음 / 비 / 비/눈 / 눈 / 소나기.
 */
public record ForecastWeatherDto(String temperature, String maxTemp, String minTemp, String skyStatus,
                                  String rainPrecipitation, String forecastPrecipitationType) {
}
