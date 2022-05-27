# ImoyaAndroidMediaLib

[VoiceClock](https://imoya.net/android/voiceclock) より切り出した、音声データ処理ロジック集です。

下記を含んでいます。

* [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec) を使用したオーディオストリームのデコードロジック
* 複数のデコード済みオーディオストリームを結合して、1個のオーディオストリームを生成し再生するロジック

## Installation

### Android application with Android Studio

1. Install [ImoyaAndroidLog](https://github.com/IceImo-P/ImoyaAndroidLog) with [this section](https://github.com/IceImo-P/ImoyaAndroidLog#android-application-with-android-studio).
2. Install [ImoyaAndroidUtil](https://github.com/IceImo-P/ImoyaAndroidUtil) with [this section](https://github.com/IceImo-P/ImoyaAndroidUtil#android-application-with-android-studio).
3. Download `imoya-android-media-release-[version].aar` from [Releases](https://github.com/IceImo-P/ImoyaAndroidMediaLib/releases) page.
4. Place `imoya-android-media-release-[version].aar` in `libs` subdirectory of your app module.
5. Add dependencies to your app module's `build.gradle`:

    ```groovy
    dependencies {
        // (other dependencies)
        implementation files('libs/imoya-android-media-release-[version].aar')
        // (other dependencies)
    }
    ```

6. Sync project with Gradle.

### Android library with Android Studio

1. Install [ImoyaAndroidLog](https://github.com/IceImo-P/ImoyaAndroidLog) with [this section](https://github.com/IceImo-P/ImoyaAndroidLog#android-library-with-android-studio).
2. Install [ImoyaAndroidUtil](https://github.com/IceImo-P/ImoyaAndroidUtil) with [this section](https://github.com/IceImo-P/ImoyaAndroidUtil#android-library-with-android-studio).
3. Download `imoya-android-media-release-[version].aar` from [Releases](https://github.com/IceImo-P/ImoyaAndroidMediaLib/releases) page.
4. Create `imoya-android-media` subdirectory in your project's root directory.
5. Place `imoya-android-media-release-[version].aar` in `imoya-android-media` directory.
6. Create `build.gradle` file in `imoya-android-media` directory and set content as below:

    ```text
    configurations.maybeCreate("default")
    artifacts.add("default", file('imoya-android-media-release-[version].aar'))
    ```

7. Add the following line to the `settings.gradle` file in your project's root directory:

    ```text
    include ':imoya-android-media'
    ```

8. Add dependencies to your library module's `build.gradle`.

    ```groovy
    dependencies {
        // (other dependencies)
        implementation project(':imoya-android-media')
        // (other dependencies)
    }
    ```

9. Sync project with Gradle.

## Logging

By default, ImoyaAndroidMediaLib does not output logs.

If you want to see ImoyaAndroidMediaLib's log, please do the following steps:

1. Make string resource `imoya_android_media_log_level` for setup minimum output log level.

    ```xml
    <resources>
        <!-- (other resources) -->

        <string name="imoya_android_media_log_level" translatable="false">info</string>

        <!-- (other resources) -->
    </resources>
    ```

    * The values and meanings are shown in the following table:
      | value | meanings |
      | --- | --- |
      | `none` | Output nothing |
      | `all` | Output all log |
      | `v` or `verbose` | Output VERBOSE, DEBUG, INFO, WARN, ERROR, ASSERT log |
      | `d` or `debug` | Output DEBUG, INFO, WARN, ERROR, ASSERT log |
      | `i` or `info` | Output INFO, WARN, ERROR, ASSERT log |
      | `w` or `warn` | Output WARN, ERROR, ASSERT log |
      | `e` or `error` | Output ERROR, ASSERT log |
      | `assert` | Output ASSERT log |
2. Call `net.imoya.android.media.MediaLog.init` method at starting your application or Activity.
    * Sample(Kotlin):

        ```kotlin
        import android.app.Application
        import net.imoya.android.media.MediaLog

        class MyApplication : Application() {
            override fun onCreate() {
                super.onCreate()

                MediaLog.init(getApplicationContext())

                // ...
            }

            // ...
        }
        ```

    * Sample(Java):

        ```java
        import android.app.Application;
        import net.imoya.android.media.MediaLog;

        public class MyApplication extends Application {
            @Override
            public void onCreate() {
                super.onCreate();

                MediaLog.init(this.getApplicationContext());

                // ...
            }

            // ...
        }
        ```

## License

Apache license 2.0
