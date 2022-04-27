package net.imoya.android.media.audio.wav

import android.media.AudioFormat
import net.imoya.android.media.audio.raw.RawAudio
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RawToWavConverter {
    companion object {
        // /**
        //  * Tag for log
        //  */
        // const val TAG = "ImoMediaLib.RawToWavConv"

        @JvmStatic
        fun convert(rawAudio: RawAudio): ByteBuffer {
            val format = rawAudio.format
            val dataLength = rawAudio.lengthInBytes
            val dest = ByteBuffer
                .allocate(dataLength + 44)
                .order(ByteOrder.LITTLE_ENDIAN)

            // Log.d(TAG, "convert: dataLength = $dataLength, format = $format")

            // "RIFF"
            dest.put(0x52.toByte())
            dest.put(0x49.toByte())
            dest.put(0x46.toByte())
            dest.put(0x46.toByte())

            // RIFF size
            dest.putInt(dataLength + 36)

            // "WAVE"
            dest.put(0x57.toByte())
            dest.put(0x41.toByte())
            dest.put(0x56.toByte())
            dest.put(0x45.toByte())

            // "fmt "
            dest.put(0x66.toByte())
            dest.put(0x6d.toByte())
            dest.put(0x74.toByte())
            dest.put(0x20.toByte())

            // fmt chunk size (16 bytes)
            dest.putInt(16)

            // wFormatTag (1: Linear PCM)
            dest.putShort(1.toShort())

            // nChannels
            dest.putShort(format.channels.toShort())

            // nSamplesPerSecond
            dest.putInt(format.samplesPerSecond)

            // nAvgBytesPerSec
            dest.putInt(format.bytesPerSample * format.samplesPerSecond)

            // nBlockAlign
            dest.putShort(format.bytesPerSample.toShort())

            // wBitsPerSample
            dest.putShort((format.bytesPerSampleAtChannel * 8).toShort())

            // "data"
            dest.put(0x64.toByte())
            dest.put(0x61.toByte())
            dest.put(0x74.toByte())
            dest.put(0x61.toByte())

            // data chunk size
            dest.putInt(dataLength)

            // PCM data
            copyData(rawAudio, dest)

            dest.flip()
            return dest
        }

        @JvmStatic
        private fun copyData(rawAudio: RawAudio, dest: ByteBuffer) {
            when (rawAudio.format.audioFormat.encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> copyData8(rawAudio, dest)
                AudioFormat.ENCODING_PCM_16BIT -> copyData16(rawAudio, dest)
                AudioFormat.ENCODING_PCM_FLOAT -> copyDataFloat(rawAudio, dest)
                else -> throw IllegalArgumentException("Unexpected audio encoding: " + rawAudio.format.audioFormat.encoding)
            }
        }

        @JvmStatic
        private fun copyData8(rawAudio: RawAudio, dest: ByteBuffer) {
            dest.put(rawAudio.data)
        }

        @JvmStatic
        private fun copyData16(rawAudio: RawAudio, dest: ByteBuffer) {
            val src = ByteBuffer.wrap(rawAudio.data).order(ByteOrder.nativeOrder()).asShortBuffer()
            while (src.hasRemaining()) {
                dest.putShort(src.get())
            }
        }

        @JvmStatic
        private fun copyDataFloat(rawAudio: RawAudio, dest: ByteBuffer) {
            val src = ByteBuffer.wrap(rawAudio.data).order(ByteOrder.nativeOrder()).asFloatBuffer()
            while (src.hasRemaining()) {
                dest.putFloat(src.get())
            }
        }

    }
}