package net.imoya.android.media.audio.raw;

import android.media.AudioAttributes;
import android.media.MediaPlayer;

import androidx.annotation.NonNull;

import net.imoya.android.media.OnMemoryDataSource;
import net.imoya.android.media.audio.wav.RawToWavConverter;
import net.imoya.android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * {@link MediaPlayer} を使用して、1個の {@link RawAudio} を再生します。
 */
public class RawAudioMediaPlayer {
    /**
     * Tag for log
     */
    private static final String TAG = "ImoMediaLib.RawAudioMPlyr";

    /**
     * 音声の用途
     */
    private int audioUsage = AudioAttributes.USAGE_MEDIA;

    /**
     * 音声の種別
     */
    private int contentType = AudioAttributes.CONTENT_TYPE_UNKNOWN;

    /**
     * 結合済み音声データを再生する {@link MediaPlayer}
     */
    private MediaPlayer mediaPlayer;

    /**
     * 音声の用途を返します。
     *
     * @return 音声の用途を表す {@link AudioAttributes}.USAGE_* 値
     */
    @SuppressWarnings("unused")
    public int getAudioUsage() {
        return this.audioUsage;
    }

    /**
     * 音声の用途を設定します。
     *
     * @param audioUsage 音声の用途を表す {@link AudioAttributes}.USAGE_* 値
     */
    public void setAudioUsage(int audioUsage) {
        this.audioUsage = audioUsage;
    }

    /**
     * 音声の種別を返します。
     *
     * @return 音声の種別を表す {@link AudioAttributes}.CONTENT_TYPE_* 値
     */
    @SuppressWarnings("unused")
    public int getContentType() {
        return this.contentType;
    }

    /**
     * 音声の種別を設定します。
     *
     * @param contentType 音声の種別を表す {@link AudioAttributes}.CONTENT_TYPE_* 値
     */
    public void setContentType(int contentType) {
        this.contentType = contentType;
    }

    /**
     * 音声の再生に使用しているリソースを解放します。
     */
    public synchronized void release() {
        this.cleanupMediaPlayer();
    }

    /**
     * 1個の {@link RawAudio} を再生します。
     * <p>
     * 再生を完了するまで、メソッドを呼び出したスレッドはブロックされます。
     *
     * @param audio 再生する音声
     */
    public void play(@NonNull RawAudio audio) {
        try {
            final ByteBuffer wav = RawToWavConverter.convert(audio);
            final OnMemoryDataSource dataSource = new OnMemoryDataSource(wav);

            this.mediaPlayer = new MediaPlayer();
            this.mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(this.audioUsage)
                    .setContentType(this.contentType)
                    .build()
            );
            this.mediaPlayer.setDataSource(dataSource);
            this.mediaPlayer.prepare();
            this.mediaPlayer.setOnCompletionListener(listener -> {
                synchronized (RawAudioMediaPlayer.this) {
                    RawAudioMediaPlayer.this.notifyAll();
                }
            });
            this.mediaPlayer.start();
            synchronized (this) {
                while (this.mediaPlayer != null && this.mediaPlayer.isPlaying()) {
                    try {
                        this.wait(1000);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "playWithMediaPlayer: interrupted", e);
                    }
                }
            }
        } catch (IOException e) {
            Log.v(TAG, "play: Exception at play", e);
        } finally {
            this.cleanupMediaPlayer();
        }
    }

    private synchronized void cleanupMediaPlayer() {
        // Log.d(TAG, "cleanupMediaPlayer: start");

        try {
            if (this.mediaPlayer != null) {
                if (this.mediaPlayer.isPlaying()) {
                    try {
                        this.mediaPlayer.stop();
                    } catch (IllegalStateException ex) {
                        Log.i(TAG, "cleanupMediaPlayer: ERROR on stop MediaPlayer", ex);
                    }
                }
                this.mediaPlayer.release();
                this.mediaPlayer = null;
                this.notifyAll();
            }
        } catch (Throwable tr) {
            Log.w(TAG, "cleanupMediaPlayer: ERROR", tr);
        }

        // Log.d(TAG, "cleanupMediaPlayer: end");
    }
}
