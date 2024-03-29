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
import net.imoya.android.media.MediaLog
import net.imoya.android.media.audio.AudioSequencer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 複数の [RawAudio] 音声データを結合し、連続再生する機能を提供します。
 */
class RawAudioSequencer : AudioSequencer<RawAudioSequenceItem>() {
    /**
     * 再生方法
     */
    enum class PlayerType {
        /**
         * [android.media.AudioTrack] を使用 (推奨, デフォルト)
         */
        AUDIO_TRACK,

        /**
         * [android.media.MediaPlayer] を使用
         */
        MEDIA_PLAYER,
    }

    /**
     * オーディオ形式
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var trackFormat: RawAudioFormat? = null

    /**
     * 再生方法
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var playerType: PlayerType = PlayerType.AUDIO_TRACK

    /**
     * 音量(0.0～1.0)
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var volume: Float = 1f

    /**
     * 結合済み音声データ
     */
    private var trackData: ByteBuffer? = null

    /**
     * 結合済み音声データを再生する [RawAudioPlayer]
     */
    private var rawAudioPlayer: RawAudioPlayer? = null

    /**
     * 結合済み音声データを再生する [RawAudioMediaPlayer]
     */
    private var mediaPlayer: RawAudioMediaPlayer? = null

    /**
     * スレッド制御用 [ReentrantLock]
     */
    private val lock: ReentrantLock = ReentrantLock()

    override fun createPlayableSequence() {
        val items = sequence.toTypedArray()
        val startPosition = IntArray(items.size)
        val format = items[0].rawAudio.format
        var lastEndPosition = 0

        // 総サンプル数
        var wholeLength = 0

        // 各アイテムの再生位置を決定する
        for (i in items.indices) {
            val item = items[i]

            // 再生開始位置(samples)を算出する
            var startPos = lastEndPosition +
                    item.delayMilliSeconds * format.samplesPerSecond / 1000
            if (startPos < 0) startPos = 0
            startPosition[i] = startPos

            // このアイテムの再生終了位置(samples)を算出する(次のアイテムの再生開始位置算出に使う)
            lastEndPosition = startPos + items[i].rawAudio.lengthInBytes / format.bytesPerSample

            // AudioTrack全体の長さ(samples)を算出する
            wholeLength = wholeLength.coerceAtLeast(lastEndPosition)
        }

        // 再生する音声のデータを構築する。
        val bytes = ByteArray(wholeLength * format.bytesPerSample)
        Arrays.fill(bytes, 0, bytes.size, 0.toByte())
        val data = ByteBuffer.wrap(bytes)
        for (i in items.indices) {
            writeAudio(data, format, startPosition[i], items[i].rawAudio)
        }
        trackFormat = format
        trackData = data
    }

    /**
     * 結合済みの音声を 1回再生します。
     *
     * @throws IllegalStateException [.prepare] がコールされていないか、音声の再生中です。
     * @throws RuntimeException      予期せぬエラーが発生しました。
     */
    override fun playOnce() {
        if (playerType == PlayerType.MEDIA_PLAYER) {
            playWithMediaPlayer()
        } else {
            playWithAudioTrack()
        }
    }

    /**
     * [android.media.AudioTrack], [android.media.MediaPlayer] やメモリを解放します。
     */
    override fun cleanupResources() {
        try {
            cleanupRawAudioPlayer()
            cleanupMediaPlayer()
            trackData = null
        } catch (tr: Throwable) {
            MediaLog.w(TAG, "release: ERROR", tr)
        }
    }

    /**
     * [android.media.AudioTrack] を使用して、音声を1回再生します。
     */
    private fun playWithAudioTrack() {
        val player = RawAudioPlayer()
        player.audioUsage = audioUsageField
        player.contentType = contentTypeField
        player.volume = volume
        rawAudioPlayer = player
        try {
            player.play(RawAudio(trackData!!.array(), trackFormat!!))
        } finally {
            rawAudioPlayer = null
        }
    }

    /**
     * [android.media.AudioTrack] やメモリを解放します。
     */
    private fun cleanupRawAudioPlayer() {
        MediaLog.v(TAG, "cleanupRawAudioPlayer: start")
        lock.withLock<Unit> {
            try {
                val player = rawAudioPlayer
                if (player != null) {
                    player.release()
                    rawAudioPlayer = null
                }
            } catch (tr: Throwable) {
                MediaLog.w(TAG, "cleanupRawAudioPlayer: ERROR", tr)
            }
        }
        MediaLog.v(TAG, "cleanupRawAudioPlayer: end")
    }

    /**
     * [android.media.MediaPlayer] を使用して、音声を1回再生します。
     */
    private fun playWithMediaPlayer() {
        val player = RawAudioMediaPlayer()
        player.audioUsage = audioUsageField
        player.contentType = contentTypeField
        mediaPlayer = player
        try {
            player.play(RawAudio(trackData!!.array(), trackFormat!!))
        } finally {
            mediaPlayer = null
        }
    }

    /**
     * [android.media.MediaPlayer] やメモリを解放します。
     */
    @Synchronized
    private fun cleanupMediaPlayer() {
        MediaLog.v(TAG, "cleanupMediaPlayer: start")
        lock.withLock<Unit> {
            try {
                val player = mediaPlayer
                if (player != null) {
                    player.release()
                    mediaPlayer = null
                }
            } catch (tr: Throwable) {
                MediaLog.w(TAG, "cleanupMediaPlayer: ERROR", tr)
            }
        }
        MediaLog.v(TAG, "cleanupMediaPlayer: end")
    }

    companion object {
        /**
         * Tag for log
         */
        private const val TAG = "ImoMediaLib.RawAudioSeq"

        private fun writeAudio(
            dest: ByteBuffer,
            format: RawAudioFormat,
            startPosition: Int,
            rawAudio: RawAudio
        ) {
            val blockAlign = rawAudio.format.bytesPerSample
            val encoding = rawAudio.format.audioFormat.encoding
            val samples = rawAudio.lengthInBytes / blockAlign
            val source = ByteBuffer.wrap(rawAudio.data)
            when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> mergeBuf8(
                    dest,
                    startPosition,
                    samples,
                    format.channels,
                    source
                )
                AudioFormat.ENCODING_PCM_16BIT -> mergeBuf16(
                    dest,
                    startPosition,
                    samples,
                    format.channels,
                    source
                )
                AudioFormat.ENCODING_PCM_FLOAT -> mergeBufFloat(
                    dest,
                    startPosition,
                    samples,
                    format.channels,
                    source
                )
                else -> MediaLog.e(TAG) { "writeAudio: Unknown PCM encoding value: $encoding" }
            }
        }

        private fun mergeBuf8(
            dest: ByteBuffer,
            startPosition: Int,
            samples: Int,
            channels: Int,
            source: ByteBuffer
        ) {
            var s: Int
            var destPosition = startPosition
            for (i in 0 until samples * channels) {
                s = dest[destPosition] + source[i]
                if (s > 127) {
                    s = 127
                } else if (s < -128) {
                    s = -128
                }
                dest.put(destPosition, (s + 128).toByte())
                destPosition++
            }
        }

        private fun mergeBuf16(
            dest: ByteBuffer,
            startPosition: Int,
            samples: Int,
            channels: Int,
            source: ByteBuffer
        ) {
            var s: Int
            var destPosition = startPosition
            val sourceBuffer = source.order(ByteOrder.nativeOrder()).asShortBuffer()
            val destBuffer = dest.order(ByteOrder.nativeOrder()).asShortBuffer()
            for (i in 0 until samples * channels) {
                s = destBuffer[destPosition] + sourceBuffer[i]
                if (s > Short.MAX_VALUE) {
//                    MediaLog.d(TAG, "mergeBuf16: clipped (>32767)");
                    s = Short.MAX_VALUE.toInt()
                } else if (s < Short.MIN_VALUE) {
//                    MediaLog.d(TAG, "mergeBuf16: clipped (<-32768)");
                    s = Short.MIN_VALUE.toInt()
                }
                destBuffer.put(destPosition, s.toShort())
                destPosition++
            }
        }

        private fun mergeBufFloat(
            dest: ByteBuffer,
            startPosition: Int,
            samples: Int,
            channels: Int,
            source: ByteBuffer
        ) {
            var s: Float
            var destPosition = startPosition
            val sourceBuffer = source.order(ByteOrder.nativeOrder()).asFloatBuffer()
            val destBuffer = dest.order(ByteOrder.nativeOrder()).asFloatBuffer()
            for (i in 0 until samples * channels) {
                s = destBuffer[destPosition] + sourceBuffer[i]
                if (s > 1.0f) {
//                    MediaLog.d(TAG, "mergeBufFloat: clipped (> 1.0)");
                    s = 1.0f
                } else if (s < -1.0f) {
//                    MediaLog.d(TAG, "mergeBufFloat: clipped (< -1.0)");
                    s = -1.0f
                }
                destBuffer.put(destPosition, s)
                destPosition++
            }
        }
    }
}