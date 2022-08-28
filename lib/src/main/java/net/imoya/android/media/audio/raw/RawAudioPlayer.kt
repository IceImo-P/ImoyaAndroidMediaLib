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

import android.media.AudioAttributes
import android.media.AudioTrack
import net.imoya.android.media.MediaLog
import kotlin.jvm.Synchronized
import net.imoya.android.media.audio.AudioTrackUtility
import java.lang.Exception
import java.lang.IllegalStateException
import java.nio.ByteBuffer

/**
 * [AudioTrack] を使用して、1個の [RawAudio] を再生します。
 */
class RawAudioPlayer {
    /**
     * 音声の用途を表す [AudioAttributes].USAGE_* 値
     */
    var audioUsage = AudioAttributes.USAGE_MEDIA

    /**
     * 音声の種別を表す [AudioAttributes].CONTENT_TYPE_* 値
     */
    var contentType = AudioAttributes.CONTENT_TYPE_UNKNOWN

    /**
     * 音量(0.0～1.0)
     */
    var volume: Float = 1f

    /**
     * 音声を再生する [AudioTrack]
     */
    private lateinit var track: AudioTrack

    /**
     * 音声の再生に使用しているリソースを解放します。
     */
    @Synchronized
    fun release() {
        cleanupAudioTrack()
    }

    /**
     * 1個の [RawAudio] を再生します。
     *
     *
     * 再生を完了するまで、メソッドを呼び出したスレッドはブロックされます。
     *
     * @param audio 再生する音声
     */
    fun play(audio: RawAudio) {
        val format = audio.format
        val data = ByteBuffer.wrap(audio.data)
        val bytesPerSample = format.bytesPerSample
        MediaLog.v(TAG) { "play: format = $format" }

        // AudioTrack を構築する
        var bufferSize = AudioTrack.getMinBufferSize(
            format.samplesPerSecond,
            AudioTrackUtility.getChannelConfig(format.channels),
            AudioTrackUtility.getAudioEncodingForBytesPerSample(format.bytesPerSampleAtChannel)
        )
        MediaLog.v(TAG) { "play: minBufferSize = $bufferSize" }
        if (bufferSize % bytesPerSample != 0) {
            bufferSize += bytesPerSample - bufferSize % bytesPerSample
        }
        MediaLog.v(TAG) { "play: bufferSize = $bufferSize" }
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(audioUsage)
                    .setContentType(contentType)
                    .build()
            )
            .setAudioFormat(format.audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (volume != 1f /* && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP */) {
            track.setVolume(volume)
        }

        // 最初のデータをバッファに書き込む。
        MediaLog.v(TAG, "play: Writing data to AudioTrack")
        val writtenFirst = track.write(data, data.array().size, AudioTrack.WRITE_NON_BLOCKING)
//        MediaLog.v(TAG) { "play: writtenFirst = $writtenFirst" }
        MediaLog.v(TAG, "play: Starting AudioTrack")
        try {
            // 再生開始
            track.play()
            if (writtenFirst < data.array().size) {
                var written = writtenFirst
                var endSuccessfully = true
                do {
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        MediaLog.v(TAG, "play: AudioTrack maybe stopped")
                        break
                    }
//                    MediaLog.v(TAG) { "play: Writing data to AudioTrack... ($written - )" }
                    val w =
                        track.write(data, data.array().size - written, AudioTrack.WRITE_BLOCKING)
//                    MediaLog.v(TAG) { "play: Written $w bytes" }
                    if (w <= 0) {
                        endSuccessfully = false
                        break
                    } else {
                        written += w
                    }
                } while (written < data.array().size)
                MediaLog.v(TAG, "play: complete to write")

                // エラー終了していなければ、バッファの中身が再生されるまで待つ
                if (endSuccessfully) {
                    waitForBufferLength(format, bufferSize)
                }
            } else {
                // バッファの中身が再生されるまで待つ
                waitForBufferLength(format, bufferSize)
            }
        } catch (e: Exception) {
            MediaLog.v(TAG, "play: Exception at play", e)
        } finally {
            // AudioTrack の後片付け
            cleanupAudioTrack()
        }
    }

    @Synchronized
    private fun waitForBufferLength(format: RawAudioFormat, bufferSize: Int) {
        MediaLog.v(TAG, "waitForBufferLength: start")
        try {
            val millis = bufferSize / format.bytesPerSample * 1000L / format.samplesPerSecond + 1
            MediaLog.v(TAG) { "waitForBufferLength: bufSize = $bufferSize, time = $millis" }
            Thread.sleep(millis)
        } catch (ex: InterruptedException) {
            MediaLog.v(TAG, ex)
        }
        MediaLog.v(TAG, "waitForBufferLength: end")
    }

    /**
     * [AudioTrack] を安全に停止し解放します。
     */
    private fun cleanupAudioTrack() {
        MediaLog.v(TAG, "cleanupAudioTrack: start")
        try {
            if (this::track.isInitialized) {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    try {
                        track.stop()
                    } catch (ex: IllegalStateException) {
                        MediaLog.i(TAG, "cleanupAudioTrack: ERROR on stop AudioTrack", ex)
                    }
                }
                track.release()
            }
        } catch (tr: Throwable) {
            MediaLog.w(TAG, "cleanupAudioTrack: ERROR", tr)
        }
        MediaLog.v(TAG, "cleanupAudioTrack: end")
    }

    companion object {
        /**
         * Tag for log
         */
        private const val TAG = "ImoMediaLib.RawAudioPlyr"
    }
}