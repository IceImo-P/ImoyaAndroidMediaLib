package net.imoya.android.media.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaFormat
import net.imoya.android.media.MediaFormatException
import net.imoya.android.media.audio.AudioDecoder.AudioDecoderCallback
import net.imoya.android.media.audio.raw.RawAudio
import net.imoya.android.util.Log
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object AudioUtility {
    @Throws(Exception::class)
    @JvmStatic
    @Suppress("unused")
    fun convertResourcesToRawAudio(context: Context, resourceIds: IntArray): Array<RawAudio> {
        Log.d(TAG, "convertResourcesToRawAudio: start")
        val lock = ReentrantLock()
        val condition = lock.newCondition()
        val map: HashMap<Int, RawAudio> = HashMap<Int, RawAudio>(resourceIds.size)
        val errors = ArrayList<Exception>()

        class Callback(private val id: Int) : AudioDecoderCallback {
            override fun onEnd(decoder: AudioDecoder, data: ByteArray, format: MediaFormat) {
                Log.d(TAG, "convertResourcesToRawAudio.onEnd[$id]: start")
                lock.withLock {
                    Log.d(TAG, "convertResourcesToRawAudio.onEnd[$id]: entering lock")
                    map[id] = RawAudio(data, format)
                    Log.d(TAG, "convertResourcesToRawAudio.onEnd[$id]: signalAll")
                    condition.signalAll()
                }
                Log.d(TAG, "convertResourcesToRawAudio.onEnd[$id]: end")
            }

            override fun onError(decoder: AudioDecoder, e: java.lang.Exception) {
                Log.d(TAG, "convertResourcesToRawAudio.onError[$id]: start: $e")
                lock.withLock {
                    Log.d(TAG, "convertResourcesToRawAudio.onError[$id]: entering lock")
                    errors.add(e)
                    Log.d(TAG, "convertResourcesToRawAudio.onError[$id]: signalAll")
                    condition.signalAll()
                }
                Log.d(TAG, "convertResourcesToRawAudio.onError[$id]: end")
            }
        }

        Log.d(
            TAG,
            "convertResourcesToRawAudio: start parallel decoding: thread = ${Thread.currentThread().id}"
        )
        Arrays.stream(resourceIds).distinct().parallel().forEach {
            Log.d(
                TAG,
                "convertResourcesToRawAudio: decoding $it, thread = ${Thread.currentThread().id}"
            )
            val decoder = AudioDecoder()
            decoder.setSource(context, it)
            decoder.convert(Callback(it))
        }

        Log.d(TAG, "convertResourcesToRawAudio: waiting")
        lock.withLock {
            while (errors.size == 0 && map.size < resourceIds.size) {
                condition.await()
            }
        }

        if (errors.size > 0) {
            (0 until errors.size).forEach {
                Log.w(TAG, "convertResourcesToRawAudio: error[$it]: ${errors[it]}")
            }
            throw errors[0]
        } else {
            Log.d(TAG, "convertResourcesToRawAudio: success")
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

    @Throws(MediaFormatException::class)
    fun getAudioEncoding(mediaFormat: MediaFormat): Int {
        return if (mediaFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
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
    private const val TAG = "ImoMediaLib.MediaUtil"
}