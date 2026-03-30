package com.example.youtubetranscript

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class TranscriptResult {
    data class Success(
        val videoTitle: String?,
        val entries: List<TranscriptEntry>
    ) : TranscriptResult()

    sealed class Error : TranscriptResult() {
        object InvalidVideoId : Error()
        object NoCaptionsAvailable : Error()
        object NetworkError : Error()
        data class ParseError(val detail: String) : Error()
    }
}

class TranscriptRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetchTranscript(userInput: String): TranscriptResult {
        val videoId = TranscriptParser.extractVideoId(userInput)
            ?: return TranscriptResult.Error.InvalidVideoId

        val pageHtml = try {
            fetchYouTubePage(videoId)
        } catch (e: IOException) {
            return TranscriptResult.Error.NetworkError
        }

        val captionTracks = try {
            TranscriptParser.extractCaptionTracks(pageHtml)
        } catch (e: IllegalStateException) {
            return TranscriptResult.Error.NoCaptionsAvailable
        }

        val trackUrl = selectBestTrack(captionTracks)

        val captionXml = try {
            fetchCaptionXml(trackUrl)
        } catch (e: IOException) {
            return TranscriptResult.Error.NetworkError
        }

        val entries = try {
            TranscriptParser.parseCaptionXml(captionXml)
        } catch (e: Exception) {
            return TranscriptResult.Error.ParseError(e.message ?: "XML parse failure")
        }

        if (entries.isEmpty()) {
            return TranscriptResult.Error.NoCaptionsAvailable
        }

        val title = TranscriptParser.extractVideoTitle(pageHtml)
        return TranscriptResult.Success(title, entries)
    }

    private fun fetchYouTubePage(videoId: String): String {
        val request = Request.Builder()
            .url("https://www.youtube.com/watch?v=$videoId")
            .header("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty response body")
        }
    }

    private fun fetchCaptionXml(trackUrl: String): String {
        val xmlUrl = if ("fmt=" in trackUrl) trackUrl else "$trackUrl&fmt=xml"

        val request = Request.Builder()
            .url(xmlUrl)
            .header("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} fetching captions")
            return response.body?.string() ?: throw IOException("Empty caption response")
        }
    }

    private fun selectBestTrack(tracks: List<Pair<String, String>>): String {
        return tracks.firstOrNull { it.first.contains("English", ignoreCase = true) }?.second
            ?: tracks.first().second
    }
}
