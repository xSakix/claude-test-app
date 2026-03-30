package com.example.youtubetranscript

data class TranscriptEntry(
    val startSeconds: Float,
    val durationSeconds: Float,
    val text: String
) {
    val timestamp: String get() {
        val totalSec = startSeconds.toLong()
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
