/*
 * Copyright (C) 2022 IceImo-P
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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