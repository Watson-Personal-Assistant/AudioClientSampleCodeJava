/**
 * Copyright 2016-2017 IBM Corporation. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wa.audio;

public class AudioConstants {

  public static final String RecordFormat = "audio/l16";

  public static final float RecordSampleRate = 16000;

  public static final int RecordChannels = 1;

  public static final int RecordSampleSizeInBits = 16;

  public static final int RecordWindowSizeInSeconds = 10;

  public static final String PlaybackFormat = "audio/l16";

  public static final float PlaybackSampleRate = 16000; // Jabra speaker-phone needs 48000 to play normally.

  public static final int PlaybackChannels = 1;

  public static final int PlaybackSampleSizeInBits = 16;

  public static final boolean isSigned = true;

  public static final boolean isBigEndian = false;
}
