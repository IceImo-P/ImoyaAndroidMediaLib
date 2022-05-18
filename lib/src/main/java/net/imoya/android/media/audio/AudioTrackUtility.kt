package net.imoya.android.media.audio;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.os.Build;

/**
 * {@link android.media.AudioTrack} 利用時に使用するメソッドを提供します。
 *
 * @author IceImo-P
 */
public class AudioTrackUtility {
    /**
     * {@link AudioFormat}.ENCODING_* 値を返します。
     *
     * @param bytesPerSample 1サンプル当たりのバイト数
     * @return {@link AudioFormat}.ENCODING_* 値
     */
    public static int getAudioEncodingForBytesPerSample(int bytesPerSample) {
        switch (bytesPerSample) {
            case 1:
                return AudioFormat.ENCODING_PCM_8BIT;
            case 2:
                return AudioFormat.ENCODING_PCM_16BIT;
            case 4:
                return AudioFormat.ENCODING_PCM_FLOAT;
        }
        return AudioFormat.ENCODING_INVALID;
    }

    /**
     * {@link android.media.AudioTrack} の初期化時に使用するチャンネル設定値を返します。
     *
     * @param channels チャンネル数
     * @return チャンネル設定値
     */
    public static int getChannelConfig(int channels) {
//        final int sdkVersion = Build.VERSION.SDK_INT;
//        if (sdkVersion < 5) {
//            return getChannelConfigLegacy(channels);
//        } else {
        return getChannelConfigEclair(channels);
//        }
    }

//    @SuppressWarnings("deprecation")
//    private static int getChannelConfigLegacy(int channels) {
//        switch (channels) {
//            case 1:
//                return AudioFormat.CHANNEL_CONFIGURATION_MONO;
//            case 2:
//                return AudioFormat.CHANNEL_CONFIGURATION_STEREO;
//        }
//        return AudioFormat.CHANNEL_CONFIGURATION_INVALID;
//    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private static int getChannelConfigEclair(int channels) {
        switch (channels) {
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;
            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;
        }
        return AudioFormat.CHANNEL_INVALID;
    }
}
