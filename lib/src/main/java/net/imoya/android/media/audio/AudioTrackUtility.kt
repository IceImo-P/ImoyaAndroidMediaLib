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

import android.media.AudioFormat

/**
 * [android.media.AudioTrack] 利用時に使用するメソッドを提供します。
 *
 * @author IceImo-P
 */
object AudioTrackUtility {
    /**
     * [AudioFormat].ENCODING_* 値を返します。
     *
     * @param bytesPerSample 1サンプル当たりのバイト数
     * @return [AudioFormat].ENCODING_* 値
     */
    fun getAudioEncodingForBytesPerSample(bytesPerSample: Int): Int {
        when (bytesPerSample) {
            1 -> return AudioFormat.ENCODING_PCM_8BIT
            2 -> return AudioFormat.ENCODING_PCM_16BIT
            4 -> return AudioFormat.ENCODING_PCM_FLOAT
        }
        return AudioFormat.ENCODING_INVALID
    }

    /**
     * [android.media.AudioTrack] の初期化時に使用するチャンネル設定値を返します。
     *
     * @param channels チャンネル数
     * @return チャンネル設定値
     */
    fun getChannelConfig(channels: Int): Int {
//        return if (Build.VERSION.SDK_INT < 5) {
//            getChannelConfigLegacy(channels);
//        } else {
//            getChannelConfigEclair(channels)
//        }
        return getChannelConfigEclair(channels)
    }

//    @Suppress("deprecation")
//    fun getChannelConfigLegacy(channels: Int): Int {
//        when (channels) {
//            1 -> return AudioFormat.CHANNEL_CONFIGURATION_MONO
//            2 -> return AudioFormat.CHANNEL_CONFIGURATION_STEREO
//        }
//        return AudioFormat.CHANNEL_CONFIGURATION_INVALID
//    }

//    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private fun getChannelConfigEclair(channels: Int): Int {
        when (channels) {
            1 -> return AudioFormat.CHANNEL_OUT_MONO
            2 -> return AudioFormat.CHANNEL_OUT_STEREO
        }
        return AudioFormat.CHANNEL_INVALID
    }
}
