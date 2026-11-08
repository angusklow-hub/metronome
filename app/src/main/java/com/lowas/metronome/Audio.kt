package com.lowas.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

const val SR = 44100

val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** Builds a mono 16-bit streaming AudioTrack tuned for low latency. */
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
        .setBufferSizeInBytes(maxOf(minBuf * 2, minBytes, 4096))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .build()
}
