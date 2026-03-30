package com.example.youtubetranscript

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

object TranscriptParser {

    private const val TAG = "TranscriptParser"

    /**
     * Extracts a YouTube video ID from a full URL or returns the raw string
     * if it is already a plain 11-character video ID.
     */
    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()

        // Plain video ID
        if (trimmed.matches(Regex("[A-Za-z0-9_\\-]{11}"))) {
            return trimmed
        }

        // youtu.be short URL
        Regex("youtu\\.be/([A-Za-z0-9_\\-]{11})")
            .find(trimmed)?.groupValues?.get(1)?.let { return it }

        // Standard watch URL (?v= or &v=)
        Regex("[?&]v=([A-Za-z0-9_\\-]{11})")
            .find(trimmed)?.groupValues?.get(1)?.let { return it }

        // Shorts URL
        Regex("/shorts/([A-Za-z0-9_\\-]{11})")
            .find(trimmed)?.groupValues?.get(1)?.let { return it }

        return null
    }

    /**
     * Searches the raw HTML of a YouTube watch page for the embedded
     * ytInitialPlayerResponse JSON blob, then extracts the captionTracks array.
     *
     * Returns a list of Pair(languageName, captionUrl).
     */
    fun extractCaptionTracks(html: String): List<Pair<String, String>> {
        val marker = "ytInitialPlayerResponse"
        val markerIndex = html.indexOf(marker)
        if (markerIndex == -1) {
            throw IllegalStateException("ytInitialPlayerResponse not found in page HTML")
        }

        val braceStart = html.indexOf('{', markerIndex)
        if (braceStart == -1) {
            throw IllegalStateException("Could not locate JSON object start")
        }

        val jsonString = extractBalancedJson(html, braceStart)
        val root = JSONObject(jsonString)

        val captions = root.optJSONObject("captions")
            ?: throw IllegalStateException("No captions object in player response")

        val tracklistRenderer = captions.optJSONObject("playerCaptionsTracklistRenderer")
            ?: throw IllegalStateException("No playerCaptionsTracklistRenderer")

        val tracksArray: JSONArray = tracklistRenderer.optJSONArray("captionTracks")
            ?: throw IllegalStateException("No captionTracks — video may have no captions")

        val result = mutableListOf<Pair<String, String>>()
        for (i in 0 until tracksArray.length()) {
            val track = tracksArray.getJSONObject(i)
            val url = track.optString("baseUrl", "")
            val lang = track.optJSONObject("name")?.optString("simpleText", "Unknown") ?: "Unknown"
            if (url.isNotEmpty()) {
                result.add(Pair(lang, url))
            }
        }

        if (result.isEmpty()) {
            throw IllegalStateException("captionTracks array was empty")
        }

        return result
    }

    /**
     * Extracts the video title from the ytInitialPlayerResponse JSON.
     */
    fun extractVideoTitle(html: String): String? {
        return try {
            val marker = "ytInitialPlayerResponse"
            val markerIndex = html.indexOf(marker)
            if (markerIndex == -1) return null
            val braceStart = html.indexOf('{', markerIndex)
            if (braceStart == -1) return null
            val jsonString = extractBalancedJson(html, braceStart)
            JSONObject(jsonString).optJSONObject("videoDetails")?.optString("title")
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract video title", e)
            null
        }
    }

    /**
     * Parses YouTube's TimedText XML into a list of TranscriptEntry.
     *
     * XML structure:
     * <transcript>
     *   <text start="0.5" dur="2.3">Hello world</text>
     * </transcript>
     */
    fun parseCaptionXml(xml: String): List<TranscriptEntry> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(xml)))

        val entries = mutableListOf<TranscriptEntry>()
        val nodes = document.getElementsByTagName("text")

        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val attrs = node.attributes

            val start = attrs.getNamedItem("start")?.nodeValue?.toFloatOrNull() ?: continue
            val dur = attrs.getNamedItem("dur")?.nodeValue?.toFloatOrNull() ?: 0f
            val cleanText = node.textContent?.replace('\n', ' ')?.trim() ?: continue

            if (cleanText.isNotEmpty()) {
                entries.add(TranscriptEntry(start, dur, cleanText))
            }
        }

        return entries
    }

    /**
     * Formats the full transcript as plain text suitable for sharing or saving.
     */
    fun formatAsText(videoTitle: String?, entries: List<TranscriptEntry>): String {
        val sb = StringBuilder()
        if (!videoTitle.isNullOrBlank()) {
            sb.appendLine(videoTitle)
            sb.appendLine("=".repeat(videoTitle.length.coerceAtMost(60)))
            sb.appendLine()
        }
        for (entry in entries) {
            sb.appendLine("[${entry.timestamp}]  ${entry.text}")
        }
        return sb.toString()
    }

    private fun extractBalancedJson(src: String, startIndex: Int): String {
        var depth = 0
        var inString = false
        var escape = false

        for (i in startIndex until src.length) {
            val c = src[i]
            when {
                escape -> escape = false
                inString && c == '\\' -> escape = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) {
                        return src.substring(startIndex, i + 1)
                    }
                }
            }
        }
        throw IllegalStateException("Unbalanced JSON — could not extract player response")
    }
}
