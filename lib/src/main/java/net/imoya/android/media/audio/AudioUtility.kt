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

package net.imoya.android.media.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaFormat
import android.os.Build
import net.imoya.android.media.MediaFormatException
import net.imoya.android.media.MediaLog
import net.imoya.android.media.audio.AudioDecoder.AudioDecoderCallback
import net.imoya.android.media.audio.raw.RawAudio
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object AudioUtility {
    @Throws(Exception::class)
    @JvmStatic
    @Suppress("unused")
    @Deprecated("Use ResourcesToRawConverter")
    fun convertResourcesToRawAudio(context: Context, resourceIds: IntArray): Array<RawAudio> {
        MediaLog.v(TAG, "convertResourcesToRawAudio: start")

        // 重複を除いたリソースIDセットを作成する
        val idSet: MutableSet<Int> = HashSet(resourceIds.size)
        resourceIds.forEach { idSet.add(it) }

        val lock = ReentrantLock()
        val condition = lock.newCondition()
        val map: HashMap<Int, RawAudio> = HashMap(idSet.size)
        val errors = ArrayList<Exception>()

        class Callback(private val id: Int) : AudioDecoderCallback {
            override fun onEnd(decoder: AudioDecoder, data: ByteArray, format: MediaFormat) {
                MediaLog.v(TAG) { "convertResourcesToRawAudio.onEnd[$id]: start" }
                lock.withLock {
                    MediaLog.v(TAG) { "convertResourcesToRawAudio.onEnd[$id]: entering lock" }
                    map[id] = RawAudio(data, format)
                    MediaLog.v(TAG) { "convertResourcesToRawAudio.onEnd[$id]: signalAll" }
                    condition.signalAll()
                }
                MediaLog.v(TAG) { "convertResourcesToRawAudio.onEnd[$id]: end" }
            }

            override fun onError(decoder: AudioDecoder, e: java.lang.Exception) {
                MediaLog.v(TAG) { "convertResourcesToRawAudio.onError[$id]: start: $e" }
                lock.withLock {
                    MediaLog.v(TAG) { "convertResourcesToRawAudio.onError[$id]: entering lock" }
                    errors.add(e)
                    MediaLog.v(TAG) { "convertResourcesToRawAudio.onError[$id]: signalAll" }
                    condition.signalAll()
                }
                MediaLog.v(TAG) { "convertResourcesToRawAudio.onError[$id]: end" }
            }
        }

        MediaLog.v(TAG) {
            "convertResourcesToRawAudio: start parallel decoding: thread = ${Thread.currentThread().id}"
        }
        val executorService = Executors.newCachedThreadPool()
        idSet.forEach {
            executorService.execute {
                MediaLog.v(TAG) {
                    "convertResourcesToRawAudio: decoding $it, thread = ${Thread.currentThread().id}"
                }
                val decoder = AudioDecoder()
                decoder.setSource(context, it)
                decoder.convert(Callback(it))
            }
        }

//        // Android 7.0 以上のみのサポートであれば下記のコードで実装可能
//        Arrays.stream(resourceIds).distinct().parallel().forEach {
//            MediaLog.v(TAG) {
//                "convertResourcesToRawAudio: decoding $it, thread = ${Thread.currentThread().id}"
//            }
//            val decoder = AudioDecoder()
//            decoder.setSource(context, it)
//            decoder.convert(Callback(it))
//        }

        MediaLog.v(TAG, "convertResourcesToRawAudio: waiting")
        lock.withLock {
            while (errors.size == 0 && map.size < idSet.size) {
                condition.await()
//                MediaLog.d(TAG) { "convertResourcesToRawAudio: errors.size = ${errors.size}, out.size = ${map.size}, in.size = ${idSet.size}" }
            }
        }

        executorService.shutdown()

        if (errors.size > 0) {
            (0 until errors.size).forEach {
                MediaLog.w(TAG) { "convertResourcesToRawAudio: error[$it]: ${errors[it]}" }
            }
            throw errors[0]
        } else {
            MediaLog.v(TAG, "convertResourcesToRawAudio: success")
            return resourceIds.map { map[it]!! }.toTypedArray()
        }
    }

    @JvmStatic
    fun toAudioFormatFrom(mediaFormat: MediaFormat): AudioFormat {
        return AudioFormat.Builder()
            .setEncoding(getAudioEncoding(mediaFormat))
            .setSampleRate(mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
            .setChannelMask(getChannelMask(mediaFormat))
            .build()
    }

    /**
     * Returns PCM encoding property value from [MediaFormat]
     */
    @Throws(MediaFormatException::class)
    fun getAudioEncoding(mediaFormat: MediaFormat): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mediaFormat.containsKey(
                MediaFormat.KEY_PCM_ENCODING
            )
        ) {
            mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }
    }

    @Throws(MediaFormatException::class)
    private fun getChannelMask(source: MediaFormat): Int {
        return when (val value = source.getInteger(MediaFormat.KEY_CHANNEL_COUNT)) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> throw MediaFormatException("ChannelCount not supported: $value")
        }
    }

    /**
     * Tag for log
     */
    private const val TAG = "ImoMediaLib.AudioUtil"
}