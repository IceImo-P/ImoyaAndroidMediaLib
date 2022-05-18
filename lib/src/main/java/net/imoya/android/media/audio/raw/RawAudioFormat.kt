package net.imoya.android.media.audio.raw

import android.media.AudioFormat
import android.media.MediaFormat
import net.imoya.android.media.audio.AudioUtility

/**
 * [RawAudio] のデータ形式を表します。
 *
 * @param mediaFormat [MediaFormat]
 */
class RawAudioFormat(private val mediaFormat: MediaFormat) {
    private lateinit var audioFormatCache: AudioFormat

    /**
     * データ形式を表す [AudioFormat]
     */
    val audioFormat: AudioFormat
        get() {
            if (!this::audioFormatCache.isInitialized) {
                audioFormatCache = AudioUtility.toAudioFormatFrom(mediaFormat)
            }
            return audioFormatCache
        }

    /**
     * チャンネル数
     */
    val channels: Int
        get() = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

    /**
     * 1 チャンネル の 1 サンプルを構成するバイト数
     */
    val bytesPerSampleAtChannel: Int
        get() = when (audioFormat.encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> throw IllegalArgumentException("Unsupported PCM encoding: ${audioFormat.encoding}")
        }

    /**
     * 全チャンネルの 1 サンプルを構成するバイト数
     */
    val bytesPerSample: Int
        get() = audioFormat.channelCount * bytesPerSampleAtChannel

    /**
     * 1 秒当たりのサンプル数(サンプリング周波数, サンプリングレートと同値)
     */
    val samplesPerSecond: Int
        get() = audioFormat.sampleRate

    override fun toString(): String {
        return "audio/raw, $mediaFormat"
    }
}