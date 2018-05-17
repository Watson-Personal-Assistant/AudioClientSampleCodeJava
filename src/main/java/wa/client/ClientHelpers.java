package wa.client;
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

import okio.ByteString;
import wa.audio.AudioConstants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.util.UUID;

public class ClientHelpers {
    // Initialize our logger
    private static final Logger LOG = LogManager.getLogger(ClientHelpers.class);

  private static UUID currentUUID;

  public static JSONObject getClientStartAudioAction(JSONObject sttOptions) {
    ClientHelpers.currentUUID = UUID.randomUUID();

    JSONObject startAction = new JSONObject();
    JSONObject audioOptions = new JSONObject();

    audioOptions.put("stt", sttOptions);

    startAction.put("id", ClientHelpers.currentUUID.toString());
    startAction.put("action", "audio_start");
    if (sttOptions != null) {
      startAction.put("options", audioOptions);
    }
    LOG.info(String.format("Client: %s", startAction));
    
    return startAction;
  }

  public static JSONObject getClientAudioDataAction(ByteString data) {
    JSONObject dataAction = new JSONObject();

    dataAction.put("action", "audio_data");
    dataAction.put("encoding", "base64");
    dataAction.put("id", ClientHelpers.currentUUID.toString());
    dataAction.put("data", data.base64());

    return dataAction;
  }

  public static JSONObject getClientEndAudioAction() {
    JSONObject endAction = new JSONObject();

    endAction.put("action", "audio_end");
    endAction.put("id", ClientHelpers.currentUUID.toString());
    LOG.info(String.format("Client audio end action: %s", endAction));

    return endAction;
  }

  public static JSONObject getClientSTTOptionsAction() {
    JSONObject action = new JSONObject();
    JSONObject speechToTextOptions = new JSONObject();

    speechToTextOptions.put("engine", "watson");
    speechToTextOptions.put("content_type", AudioConstants.RecordFormat
        + "; rate="
        + (int) AudioConstants.RecordSampleRate
        + "; channels=" + AudioConstants.RecordChannels);
    speechToTextOptions.put("inactivity_timeout", -1);
    speechToTextOptions.put("smart_formatting", true);
    //speechToTextOptions.put("language", "zh-CN");

    action.put("id", UUID.randomUUID().toString());
    action.put("action", "stt_options");
    action.put("options", speechToTextOptions);

    return action;
  }

  public static JSONObject getClientTTSOptionsAction(String voice, boolean useResponseAudioUrl) {
    JSONObject action = new JSONObject();
    action.put("id", UUID.randomUUID().toString());
    action.put("action", "tts_options");
    action.put("options", getTTSOptions(voice));
    if (useResponseAudioUrl) {
        action.put("audio", "url");
    }

    return action;
  }

  public static JSONObject getTTSAction(String text) {
    JSONObject action = new JSONObject();

    action.put("id", UUID.randomUUID().toString());
    action.put("action", "tts");
    action.put("options", getTTSOptions(null));
    action.put("text", text);

    return action;
  }
  
  public static JSONObject getTTSOptions(String voice) {
      JSONObject textToSpeechOptions = new JSONObject();

      textToSpeechOptions.put("engine", "watson");
      textToSpeechOptions.put("accept", AudioConstants.PlaybackFormat
          + "; rate="
          + (int) AudioConstants.PlaybackSampleRate
          + "; channels=" + AudioConstants.PlaybackChannels);
      textToSpeechOptions.put("voice", voice == null ? "en-US_LisaVoice" : voice);

      return textToSpeechOptions;
  }
}
