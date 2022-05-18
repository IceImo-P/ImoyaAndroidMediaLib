package net.imoya.android.media.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaFormat
import android.os.Build
import net.imoya.android.media.MediaFormatException
import net.imoya.android.media.audio.AudioDecoder.AudioDecoderCallback
import net.imoya.android.media.audio.raw.RawAudio
import net.imoya.android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object AudioUtility {
    @Throws(Exception::class)
    @JvmStatic
    @Suppress("unused")
    @Deprecated("Use ResourcesToRawConverter")
    fun convertResourcesToRawAudio(context: Context, resourceIds: IntArray): Array<RawAudio> {
        Log.v(TAG, "convertResourcesToRawAudio: start")

        // 重複を除いたリソースIDセットを作成する
        val idSet: MutableSet<Int> = HashSet(resourceIds.size)
        resourceIds.forEach { idSet.add(it) }

        val lock = ReentrantLock()
        val condition = lock.newCondition()
        val map: HashMap<Int, RawAudio> = HashMap(idSet.size)
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
        val executorService = Executors.newCachedThreadPool()
        idSet.forEach {
            executorService.execute {
                Log.d(
                    TAG,
                    "convertResourcesToRawAudio: decoding $it, thread = ${Thread.currentThread().id}"
                )
                val decoder = AudioDecoder()
                decoder.setSource(context, it)
                decoder.convert(Callback(it))
            }
        }

//        // Android 7.0 以上のみのサポートであれば下記のコードで実装可能
//        Arrays.stream(resourceIds).distinct().parallel().forEach {
//            Log.d(
//                TAG,
//                "convertResourcesToRawAudio: decoding $it, thread = ${Thread.currentThread().id}"
//            )
//            val decoder = AudioDecoder()
//            decoder.setSource(context, it)
//            decoder.convert(Callback(it))
//        }

        Log.d(TAG, "convertResourcesToRawAudio: waiting")
        lock.withLock {
            while (errors.size == 0 && map.size < idSet.size) {
                condition.await()
//                Log.d(TAG, "convertResourcesToRawAudio: errors.size = ${errors.size}, out.size = ${map.size}, in.size = ${idSet.size}")
            }
        }

        executorService.shutdown()

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