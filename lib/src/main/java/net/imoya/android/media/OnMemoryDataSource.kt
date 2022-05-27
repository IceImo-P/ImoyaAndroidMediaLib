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
//        MediaLog.v(TAG) {
//            "readAt: position = $position, bufferLength = ${buffer.size}, offset = $offset, size = $size, srcLimit = ${this.data.limit()}"
//        }
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
//            MediaLog.v(TAG) { "written = $written" }
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