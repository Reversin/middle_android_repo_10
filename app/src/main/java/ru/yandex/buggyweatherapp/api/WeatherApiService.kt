package ru.yandex.buggyweatherapp.api

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import ru.yandex.buggyweatherapp.BuildConfig

interface WeatherApiService {
    
    
    companion object {
        const val BASE_URL = "http://api.openweathermap.org/data/2.5/"
    }
    
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String = BuildConfig.OW_API_KEY,
        @Query("units") units: String = "metric"
    ): Response<JsonObject>
    
    @GET("weather")
    suspend fun getWeatherByCity(
        @Query("q") cityName: String,
        @Query("appid") apiKey: String = BuildConfig.OW_API_KEY,
        @Query("units") units: String = "metric"
    ): Response<JsonObject>
    
    @GET("forecast")
    suspend fun getForecast(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String = BuildConfig.OW_API_KEY,
        @Query("units") units: String = "metric"
    ): Response<JsonObject>
}