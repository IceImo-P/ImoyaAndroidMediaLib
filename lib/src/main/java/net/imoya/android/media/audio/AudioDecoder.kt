package net.imoya.android.media.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import net.imoya.android.util.Log
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * 任意のオーディオファイルを、 [AudioTrack] へ設定可能な音声データへ変換します。
 *
 * Android の [MediaCodec] を使用して任意のオーディオファイルをデコードし、
 * [AudioTrack] へ設定可能な音声データを出力します。
 *
 * 入力ファイルは実行環境がデコード可能な任意の形式(MP3, AAC, Ogg Vorbis 等)を利用可能ですが、
 * Ogg Vorbis のみ動作確認済みです。
 */
class AudioDecoder {
    /**
     * コールバック
     */
    interface AudioDecoderCallback {
        /**
         * 変換完了時コールバック
         *
         * @param decoder 呼び出し元の [AudioDecoder]
         * @param data 変換の出力(16-bit linear PCM)
         * @param format [data] のオーディオ形式
         */
        fun onEnd(decoder: AudioDecoder, data: ByteArray, format: MediaFormat)

        /**
         * エラー発生時コールバック
         *
         * @param decoder 呼び出し元の [AudioDecoder]
         * @param e エラー内容
         */
        fun onError(decoder: AudioDecoder, e: Exception)
    }

    /**
     * 変換コンテキスト
     */
    private class ConversionContext(
        val decoder: AudioDecoder,
        val callback: AudioDecoderCallback
    )

    /**
     * 入力ファイルの読み取りに使用する [MediaExtractor]
     */
    private var extractor: MediaExtractor = MediaExtractor()

    /**
     * 入力ファイルのトラック番号
     */
    private var track = -1

    /**
     * [MediaCodec] の出力フォーマット
     */
    private lateinit var outputFormat: MediaFormat

    /**
     * 出力の PCM エンコーディング
     */
    private var pcmEncoding: Int = AudioFormat.ENCODING_PCM_16BIT

    /**
     * リソースIDを使用して入力ファイルを設定する
     *
     * @param context [Context]
     * @param resourceId リソースID
     */
    @Throws(IOException::class)
    fun setSource(context: Context, resourceId: Int) {
        val fd = context.resources.openRawResourceFd(resourceId)
        extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        this.checkFormat()
    }

    /**
     * [FileDescriptor] を使用して入力ファイルを設定する
     *
     * @param source [FileDescriptor]
     */
    @Suppress("unused")
    @Throws(IOException::class)
    fun setSource(source: FileDescriptor) {
        extractor.setDataSource(source)
        this.checkFormat()
    }

    /**
     * 入力ファイルの形式をチェックする
     *
     * @throws IllegalArgumentException 入力ファイルは Android がサポートするオーディオ形式ではありません。
     */
    private fun checkFormat() {
        track = -1
        run loop@{
            (0 until extractor.trackCount).forEach {
                val format = extractor.getTrackFormat(it)
                val mimeType = format.getString(MediaFormat.KEY_MIME)
                Log.v(TAG) { "checkFormat: MIME type for track $it is: $mimeType" }
                if (mimeType != null && mimeType.startsWith("audio/")) {
                    track = it
                    Log.v(TAG) { "checkFormat: Audio track found: $it" }
                    return@loop
                }
            }
        }
        Log.v(TAG) { "checkFormat: Audio track is: $track" }
        require(track >= 0) { "Illegal media type: No audio track at source" }
        extractor.selectTrack(track)
    }

    /**
     * 変換を実行します。
     */
    fun convert(callback: AudioDecoderCallback) {
        try {
            Log.v(TAG, "convert: start")
            val format = extractor.getTrackFormat(track)
            val mimeType = format.getString(MediaFormat.KEY_MIME)

            val decoder = MediaCodec.createDecoderByType(mimeType!!)
            decoder.setCallback(CodecCallback(ConversionContext(this, callback)))
            decoder.configure(format, null, null, 0)
            outputFormat = decoder.outputFormat
            pcmEncoding = AudioUtility.getAudioEncoding(outputFormat)
            Log.v(TAG) { "convert: Output = $outputFormat" }
            decoder.start()
            Log.v(TAG, "convert: end")
        } catch (e: Exception) {
            callback.onError(this, e)
        }
    }

    /**
     * [MediaCodec.Callback] の実装
     */
    private inner class CodecCallback(val context: ConversionContext) : MediaCodec.Callback() {
        /**
         * デコード済みオーディオデータの一時保存バッファ
         */
        val destination = ByteArrayOutputStream()

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            try {
                // 入力ファイルよりオーディオデータを読み取り入力バッファーへ設定する
                Log.v(TAG) { "onInputBufferAvailable: start. index = $index" }
                val buffer = codec.getInputBuffer(index)
                if (buffer != null) {
                    val read = extractor.readSampleData(buffer, 0)
                    if (read < 0) {
                        // ファイル終端へ達した場合は終端フラグをセットする
                        Log.v(TAG, "Input EOF")
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        Log.v(TAG) { "Input length = $read" }
                        val sampleTime = extractor.sampleTime
                        if (extractor.advance()) {
                            codec.queueInputBuffer(index, 0, read, sampleTime, 0)
                        } else {
                            Log.v(TAG, "Input EOF (with data)")
                            codec.queueInputBuffer(
                                index,
                                0,
                                read,
                                sampleTime,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                        }
                    }
                } else {
                    Log.w(TAG, "onInputBufferAvailable: No buffer available(MediaCodec returned null)")
                }
            } catch (e: Exception) {
                errorProcess(codec, e)
            }
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            Log.v(TAG) { "onOutputBufferAvailable: start. index = $index" }
            try {
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null) {
                    when (pcmEncoding) {
                        AudioFormat.ENCODING_PCM_8BIT -> output8bit(buffer)
                        AudioFormat.ENCODING_PCM_16BIT -> output16bit(buffer)
                        AudioFormat.ENCODING_PCM_FLOAT -> outputFloat(buffer)
                        else -> throw IllegalArgumentException("Unexpected PCM encoding: $pcmEncoding")
                    }
                    codec.releaseOutputBuffer(index, false)
                } else {
                    Log.w(
                        TAG,
                        "onOutputBufferAvailable: No buffer available(MediaCodec returned null)"
                    )
                }

                // 終端か?
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    // 終端である場合
                    Log.v(TAG, "Output EOF")

                    destination.close()
                    codec.stop()
                    codec.release()
                    extractor.release()

                    // 変換完了時コールバックをコール
                    context.callback.onEnd(context.decoder, destination.toByteArray(), outputFormat)
                }
            } catch (e: Exception) {
                errorProcess(codec, e)
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            errorProcess(codec, e)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
//            Log.w(TAG, "Changing media format function is not supported.")
        }

        /**
         * 8-bit PCM を出力バッファへコピーします。
         */
        private fun output8bit(buffer: ByteBuffer) {
            destination.write(buffer.array())
        }

        /**
         * 16-bit PCM を出力バッファへコピーします。
         */
        private fun output16bit(buffer: ByteBuffer) {
            val samples: ShortBuffer = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
            val writableBuffer = ByteBuffer.allocate(samples.remaining() * 2)
            val convertedSamples: ShortBuffer =
                writableBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
            while (samples.remaining() > 0) {
                convertedSamples.put(samples.get())
            }
            destination.write(writableBuffer.array())
        }

        /**
         * Float PCM を出力バッファへコピーします。
         */
        private fun outputFloat(buffer: ByteBuffer) {
            val samples: FloatBuffer = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
            val writableBuffer = ByteBuffer.allocate(samples.remaining() * 2)
            val convertedSamples: FloatBuffer =
                writableBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
            while (samples.remaining() > 0) {
                convertedSamples.put(samples.get())
            }
            destination.write(writableBuffer.array())
        }

        /**
         * エラー発生時の処理
         */
        private fun errorProcess(codec: MediaCodec, e: Exception) {
            Log.i(TAG) { "Error: $e" }

            try {
                destination.close()
                codec.stop()
                codec.release()
                extractor.release()
            } catch (e: Exception) {
            }

            // エラー発生時コールバックをコール
            context.callback.onError(context.decoder, e)
        }
    }

    companion object {
        /**
         * Tag for log
         */
        private const val TAG = "ImoMediaLib.AudioDecoder"
    }
}