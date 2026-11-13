package com.lowas.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

const val SR = 44100

val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/**
 * About a quarter of a second of audio (16-bit mono). The beat indicator and vibration follow the
 * device's real playback position, so a deep buffer costs nothing in timing, and it rides out the
 * short stalls the app has while it redraws, such as when the interface style changes.
 */
const val CUSHION_BYTES = SR / 4 * 2

/** Builds a mono 16-bit streaming AudioTrack with a deep buffer so brief stalls never reach the speaker. */
fun buildTrack(minBytes: Int = 0): AudioTrack {
    val minBuf = AudioTrack.getMinBufferSize(
        SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
    return AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SR)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(maxOf(minBuf * 2, minBytes, CUSHION_BYTES))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
        .build()
}
