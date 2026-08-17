package com.nhnacademy.insightonfront.adapter.core.weather.dto;

/** 초단기실황. */
public record CurrentWeatherDto(String temp, String humidity, String hourlyRainFall, String precipitationType) {
}
