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

package net.imoya.android.media.audio.raw

import net.imoya.android.media.audio.AudioSequenceItem

/**
 * [RawAudioSequencer] が連続再生する音声データ 1個を定義するクラス
 */
class RawAudioSequenceItem(delayMilliSeconds: Int, @JvmField val rawAudio: RawAudio) : AudioSequenceItem(delayMilliSeconds)
