package net.imoya.android.media

import android.media.MediaDataSource
import kotlin.Throws
import java.io.IOException
import java.nio.ByteBuffer

/**
 * 任意のメディアファイルイメージを [android.media.MediaPlayer] で再生する場合に使用する、
 * [MediaDataSource] の実装です。
 *
 * @param data メディアファイルのバイナリイメージを読み取り可能な [ByteBuffer]
 */
class OnMemoryDataSource(private val data: ByteBuffer) : MediaDataSource() {
    @Throws(IOException::class)
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        // Log.d(TAG, "readAt: position = " + position + ", bufferLength = " + buffer.length + ", offset = " + offset + ", size = " + size + ", srcLimit = " + this.data.limit());
        return if (position < 0) {
            throw IOException("Illegal position")
        } else if (offset < 0 || offset >= buffer.size) {
            throw IOException("Illegal offset")
        } else if (size <= 0) {
            throw IOException("Illegal size")
        } else if (position >= data.limit()) {
            -1
        } else {
            var srcPos = position.toInt()
            var destPos = offset
            var written = 0
            var i = 0
            while (i < size && srcPos < data.limit() && destPos < buffer.size) {
                buffer[destPos] = data[srcPos]
                srcPos++
                destPos++
                written++
                i++
            }
            // Log.d(TAG, "written = $written")
            written
        }
    }

    override fun getSize(): Long {
        return data.limit().toLong()
    }

    override fun close() {
        // 何もしない
    }

//    companion object {
//        /**
//         * Tag for log
//         */
//        private const val TAG = "ImoMediaLib.OnMemDataSrc"
//    }
}