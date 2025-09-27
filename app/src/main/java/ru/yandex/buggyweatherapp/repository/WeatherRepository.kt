package ru.yandex.buggyweatherapp.repository

import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.yandex.buggyweatherapp.api.RetrofitInstance
import ru.yandex.buggyweatherapp.model.Location
import ru.yandex.buggyweatherapp.model.WeatherData
import kotlin.coroutines.cancellation.CancellationException

class WeatherRepository {
    
    
    private val weatherApi = RetrofitInstance.weatherApi
    
    
    private var cachedWeatherData: WeatherData? = null


    suspend fun getWeatherData(
        location: Location,
        callback: (WeatherData?, Exception?) -> Unit
    ) {
        try {
            val resp = weatherApi.getCurrentWeather(location.latitude, location.longitude)

            if (!resp.isSuccessful) {
                val msg = resp.errorBody()?.string()
                withContext(Dispatchers.Main) {
                    callback(null, Exception("API ${resp.code()} ${resp.message()} ${msg ?: ""}"))
                }
                return
            }

            val json = resp.body()
            if (json == null) {
                withContext(Dispatchers.Main) { callback(null, IllegalStateException("Empty body")) }
                return
            }

            val hasMain = json.getAsJsonObject("main") != null
            val hasWeather0 = json.getAsJsonArray("weather")?.firstOrNull()?.isJsonObject == true
            if (!hasMain || !hasWeather0) {
                withContext(Dispatchers.Main) {
                    callback(null, IllegalStateException("Malformed response: main/weather[0] missing"))
                }
                return
            }

            val data = parseWeatherData(json, location)
            cachedWeatherData = data

            withContext(Dispatchers.Main) { callback(data, null) }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e("WeatherRepository", "Error fetching weather", t)
            withContext(Dispatchers.Main) { callback(null, Exception(t)) }
        }
    }


    suspend fun getWeatherByCity(cityName: String): Result<WeatherData> = runCatching {
        val resp = weatherApi.getWeatherByCity(cityName)
        if (!resp.isSuccessful) error("API ${resp.code()} ${resp.message()}")
        val json = resp.body() ?: error("Empty body")

        val loc = extractLocationFromResponse(json)
        parseWeatherData(json, loc)
    }

    private fun parseWeatherData(json: JsonObject, location: Location): WeatherData {
        val main   = json.getAsJsonObject("main") ?: JsonObject()
        val wind   = json.getAsJsonObject("wind") ?: JsonObject()
        val sys    = json.getAsJsonObject("sys")  ?: JsonObject()
        val clouds = json.getAsJsonObject("clouds") ?: JsonObject()
        val weather0 = json.getAsJsonArray("weather")?.firstOrNull()?.asJsonObject

        fun jStr(o: JsonObject?, k: String) = o?.get(k)?.takeUnless { it.isJsonNull }?.asString
        fun jDbl(o: JsonObject?, k: String) = o?.get(k)?.takeUnless { it.isJsonNull }?.asDouble
        fun jInt(o: JsonObject?, k: String) = o?.get(k)?.takeUnless { it.isJsonNull }?.asInt
        fun jLng(o: JsonObject?, k: String) = o?.get(k)?.takeUnless { it.isJsonNull }?.asLong

        return WeatherData(
            cityName    = jStr(json, "name") ?: location.name.orElse(""),
            country     = jStr(sys, "country") ?: "",
            temperature = jDbl(main, "temp") ?: 0.0,
            feelsLike   = jDbl(main, "feels_like") ?: 0.0,
            minTemp     = jDbl(main, "temp_min") ?: 0.0,
            maxTemp     = jDbl(main, "temp_max") ?: 0.0,
            humidity    = jInt(main, "humidity") ?: 0,
            pressure    = jInt(main, "pressure") ?: 0,
            windSpeed   = jDbl(wind, "speed") ?: 0.0,
            windDirection = jInt(wind, "deg") ?: 0,
            description = jStr(weather0, "description") ?: "",
            icon        = jStr(weather0, "icon") ?: "",
            cloudiness  = jInt(clouds, "all") ?: 0,
            sunriseTime = jLng(sys, "sunrise") ?: 0L,
            sunsetTime  = jLng(sys, "sunset") ?: 0L,
            timezone    = jInt(json, "timezone") ?: 0,
            timestamp   = jLng(json, "dt") ?: 0L,
            rawApiData  = json.toString(),
            rain        = jDbl(json.getAsJsonObject("rain"), "1h"),
            snow        = jDbl(json.getAsJsonObject("snow"), "1h")
        )
    }

    private fun String?.orElse(fallback: String) = this ?: fallback

    
    private fun extractLocationFromResponse(json: JsonObject): Location {
        val coord = json.getAsJsonObject("coord")
        val lat = coord.get("lat").asDouble
        val lon = coord.get("lon").asDouble
        val name = json.get("name").asString
        
        return Location(lat, lon, name)
    }
}