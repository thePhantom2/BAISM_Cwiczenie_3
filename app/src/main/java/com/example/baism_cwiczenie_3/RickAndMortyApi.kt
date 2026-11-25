package com.example.baism_cwiczenie_3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

internal data class RickAndMortyCharacter(
    val id: Int,
    val name: String,
    val status: String,
    val species: String,
    val gender: String,
    val origin: String,
    val location: String
)

internal class CharacterNotFoundException : IOException()

internal data class CharacterPage(
    val characters: List<RickAndMortyCharacter>,
    val nextPage: String?
)

private class RateLimitedException(val retryAfter: String?) : IOException("HTTP 429")

internal object RickAndMortyApi {
    private val retryDelaysSeconds = listOf(5L, 15L, 45L)
    const val CHARACTERS_URL = "https://rickandmortyapi.com/api/character"

    suspend fun getCharacterPage(url: String): CharacterPage = withContext(Dispatchers.IO) {
        val page = getPageJson(url)
        val results = page.getJSONArray("results")
        val info = page.getJSONObject("info")
        CharacterPage(
            characters = List(results.length()) { results.getJSONObject(it).toCharacter() },
            nextPage = if (info.isNull("next")) null else info.getString("next")
        )
    }

    suspend fun getCharacter(id: Int): RickAndMortyCharacter = withContext(Dispatchers.IO) {
        getJson("$CHARACTERS_URL/$id", lookupById = true).toCharacter()
    }

    private suspend fun getPageJson(url: String): JSONObject {
        for (fallbackSeconds in retryDelaysSeconds) {
            try {
                return getJson(url)
            } catch (exception: RateLimitedException) {
                val seconds = retryAfterSeconds(exception.retryAfter) ?: fallbackSeconds
                delay(seconds.coerceAtLeast(1L).coerceAtMost(Long.MAX_VALUE / 1_000) * 1_000)
            }
        }
        return getJson(url)
    }

    private fun retryAfterSeconds(header: String?): Long? {
        val value = header?.trim() ?: return null
        value.toLongOrNull()?.let { return it }
        return try {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond() -
                System.currentTimeMillis() / 1_000
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun getJson(url: String, lookupById: Boolean = false): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val status = connection.responseCode
            if (lookupById && status == HttpURLConnection.HTTP_NOT_FOUND) {
                throw CharacterNotFoundException()
            }
            if (status == 429) {
                throw RateLimitedException(connection.getHeaderField("Retry-After"))
            }
            if (status !in 200..299) {
                throw IOException("HTTP $status")
            }
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toCharacter() = RickAndMortyCharacter(
        id = getInt("id"),
        name = getString("name"),
        status = getString("status"),
        species = getString("species"),
        gender = getString("gender"),
        origin = getJSONObject("origin").getString("name"),
        location = getJSONObject("location").getString("name")
    )
}
