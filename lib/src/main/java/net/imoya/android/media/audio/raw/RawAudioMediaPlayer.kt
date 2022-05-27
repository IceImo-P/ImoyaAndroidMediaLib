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
import android.media.MediaPlayer
import net.imoya.android.media.MediaLog
import net.imoya.android.media.OnMemoryDataSource
import net.imoya.android.media.audio.wav.RawToWavConverter
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * [MediaPlayer] を使用して、1個の [RawAudio] を再生します。
 */
class RawAudioMediaPlayer {
    /**
     * 音声の用途を表す [AudioAttributes].USAGE_* 値
     */
    var audioUsage = AudioAttributes.USAGE_MEDIA

    /**
     * 音声の種別を表す [AudioAttributes].CONTENT_TYPE_* 値
     */
    var contentType = AudioAttributes.CONTENT_TYPE_UNKNOWN

    /**
     * 結合済み音声データを再生する [MediaPlayer]
     */
    private var mediaPlayer: MediaPlayer? = null

    /**
     * スレッド制御用 [ReentrantLock]
     */
    private var lock: ReentrantLock = ReentrantLock()

    /**
     * スレッド制御用 [Condition]
     */
    private var condition: Condition = lock.newCondition()

    /**
     * 音声の再生に使用しているリソースを解放します。
     */
    fun release() {
        cleanupMediaPlayer()
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
        try {
            val wav = RawToWavConverter.convert(audio)
            val dataSource = OnMemoryDataSource(wav)
            val mp = MediaPlayer()
            mediaPlayer = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(audioUsage)
                    .setContentType(contentType)
                    .build()
            )
            mp.setDataSource(dataSource)
            mp.prepare()
            mp.setOnCompletionListener {
                lock.withLock {
                    condition.signalAll()
                }
            }
//            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
//                @Override
//                public void onCompletion(MediaPlayer mp) {
//                    synchronized (RawAudioMediaPlayer.this) {
//                        RawAudioMediaPlayer.this.notifyAll();
//                    }
//                }
//            });
            mp.start()
            lock.withLock {
                while (mp.isPlaying) {
                    try {
                        condition.await(1000, TimeUnit.MILLISECONDS)
                    } catch (e: InterruptedException) {
                        MediaLog.d(TAG, "playWithMediaPlayer: interrupted", e)
                    }
                }
            }
        } catch (e: IOException) {
            MediaLog.v(TAG, "play: Exception at play", e)
        } finally {
            cleanupMediaPlayer()
        }
    }

    private fun cleanupMediaPlayer() {
        MediaLog.v(TAG, "cleanupMediaPlayer: start")
        try {
            lock.withLock {
                val mp = mediaPlayer
                if (mp != null) {
                    if (mp.isPlaying) {
                        try {
                            mp.stop()
                        } catch (ex: IllegalStateException) {
                            MediaLog.i(TAG, "cleanupMediaPlayer: ERROR on stop MediaPlayer", ex)
                        }
                    }
                    mp.release()
                    mediaPlayer = null
                    condition.signalAll()
                }
            }
        } catch (tr: Throwable) {
            MediaLog.w(TAG, "cleanupMediaPlayer: ERROR", tr)
        }

        MediaLog.v(TAG, "cleanupMediaPlayer: end")
    }

    companion object {
        /**
         * Tag for log
         */
        private const val TAG = "ImoMediaLib.RawAudioMPlyr"
    }
}
