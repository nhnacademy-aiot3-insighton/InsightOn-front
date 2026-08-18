package com.nhnacademy.insightonfront.adapter.core.weather.dto;

public record WeatherDataDto(CurrentWeatherDto current, ForecastWeatherDto forecast,
                              UltraForecastWeatherDto ultraForecastWeather, AirQualityDto airQuality) {
}
