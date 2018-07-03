package wa.client;
/**
 * Copyright 2016-2018 IBM Corporation. All Rights Reserved.
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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import wa.audio.AudioInput;
import wa.audio.AudioOutput;
import wa.audio.AudioPlayer;
import wa.audio.AudioSocket;
import wa.audio.LocalAudio;
import wa.exceptions.AuthenticationError;
import wa.exceptions.ConnectionError;
import wa.network.LocalNetworkInterface;
import wa.status.StatusConsole;
import wa.status.StatusIndicator;
import wa.status.StatusLED;
import wa.status.StatusPing;
import wa.util.CallStack;
import wa.util.Utils;

public class Client extends WebSocketListener implements ThreadManager, Runnable {
    // Initialize our loggerLOG_SERVER_COMM_RECEIVE
    private static final Logger LOG = LogManager.getLogger(Client.class);
    private static final Logger LOG_SERVER_COMM_RECEIVE = LogManager.getLogger("GLOBAL.Server.Communication.Receive");
    private static final Logger LOG_SERVER_COMM_SEND = LogManager.getLogger("GLOBAL.Server.Communication.Send");

    private static final int MIN_RETRY_DELAY = 8;
    private static final int MAX_RETRY_DELAY = 15;

    private boolean WE_SHOULD_KEEP_RUNNING = true;

    private List<Thread> threads = new ArrayList<>();

    private AudioSocket audioSocket;
    private SocketCommandProcessor socketCommandProcessor;
    private final Object socketsLock = new Object();

    private AudioInput audioInput;

    private AudioOutput audioOutput = AudioOutput.getInstance();

    private WebSocket webSocket;

    private String iamAccessToken = null;

    private String skillset = null;

    private String tenantID = null;

    private String language = null;

    private String engine = null;

    private String watsonHost = null;

    private String watsonPort = null;

    private Boolean watsonSsl = true;

    private String userID = null;

    private String watsonVoice = null;

    private int commandSocketPort;

    private int audioSocketPort;

    private long statusPingRate;

    private Boolean enableResponseUrlProcessing;

    private Boolean debug;

    private Boolean logAdditionalAudioInfo;

    private Constructor wakeupClassCtor;

    private String currentAudioId;
    private String previousAudioId;

    // Client status
    private StatusIndicator indicator;
    private StatusPing statusPingSender;
    private RuntimeException error;

    public enum ServerConnectionStatus {
        NOTCONNECTED, CONNECTING, CONNECTED, READY, CLOSING
    };

    private ServerConnectionStatus serverConnectionStatus = ServerConnectionStatus.NOTCONNECTED;
    private long serverConnectionStatusLastSentTS = 0;
    final private Object serverConnectionStatusLock = new Object();

    private boolean hasFailed = false;

    private Boolean wakeupTriggerAllowed = false;
    private long wakeupTriggerAllowedStatusLastSentTS = 0;
    final private Object wakeupTriggerAllowedLock = new Object();

    // audio url playing
    private String voiceUrl = null;
    private boolean urlMode = false;
    private boolean muteThisClient = false;

    // Performance data
    private long openMicTime = 0;
    private long transcriptReceivedTime = 0;
    private long responseReceivedTime = 0;
    private long textReceivedTime = 0;
    private long audioStartReceivedTime = 0;
    private long audioEndReceivedTime = 0;
    private long audioPacketCount = 0;
    private long audioDataSize = 0;

    private class AuthException extends IOException {
        private static final long serialVersionUID = 1L;

        AuthException(String message) {
            super(message);
        }
    }

    private class CleanUp implements Runnable {
        private AudioInput audioInput;
        private AudioOutput speaker;
        private StatusIndicator statusIndicator;

        public CleanUp(AudioInput audioInput, AudioOutput speaker, StatusIndicator statusLED) {
            this.audioInput = audioInput;
            this.speaker = speaker;
            this.statusIndicator = statusLED;
        }

        @Override
        public void run() {
            if (currentAudioId != null) {
                LOG.debug("Running CleanUp task");
                currentAudioId = null;
                this.audioInput.micClose();
                this.statusIndicator.off();
            }
        }
    }

    private class EndOfAudioOutputTask implements Runnable {

        private String id = null;
        private boolean shouldPrompt = false;
        private JSONObject promptSttOptions = null;

        EndOfAudioOutputTask(String id, boolean shouldPrompt, JSONObject promptSttOptions) {
            this.id = id;
            this.shouldPrompt = shouldPrompt;
            this.promptSttOptions = promptSttOptions;
            LOG.debug(String.format("EndOfAudioOutputTask created with ID:%s  Should-Prompt:%b PromptSttOptions:%s", id, shouldPrompt, promptSttOptions));
        }

        public void complexPromptSttOptions(JSONObject completPromptSttOptions) {
            this.promptSttOptions = completPromptSttOptions;
        }

        synchronized void shouldPrompt(boolean shouldPrompt) {
            this.shouldPrompt = shouldPrompt;
            this.promptSttOptions = null;
            LOG.debug(String.format(" shouldPrompt being set to:%b PromptSttOptions:%s", shouldPrompt, promptSttOptions));
        }

        synchronized void shouldPrompt(boolean shouldPrompt, JSONObject complexPromptSttOptions) {
            this.shouldPrompt = shouldPrompt;
            this.promptSttOptions = complexPromptSttOptions;
            LOG.debug(String.format(" shouldPrompt being set to:%b PromptSttOptions:%s", shouldPrompt, complexPromptSttOptions));
        }

        synchronized boolean shouldPrompt() {
            return shouldPrompt;
        }

        @Override
        public void run() {
            if (shouldPrompt) {
                LOG.info(String.format(" EndOfAudioOutputTask (ID=%s) - Prompting...", id));
                Client.this.continueConversation(id, promptSttOptions);
            }
            // Make sure the wakeup trigger is enabled.
            Client.this.wakeupTriggerIsAllowed();
            // Wipe ourself out!
            Client.this.endOfAudioOutputTask = null;
        }
    }

    private EndOfAudioOutputTask endOfAudioOutputTask = null;

    // Scheduler for executing a fail-safe for the Client Wake Trigger enable
    private final ScheduledExecutorService wakeTriggerEnableScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> wakeTriggerEnablerFuture;

    private class WakeTriggerEnableFailSafe implements Runnable {
        Client client = null;

        public WakeTriggerEnableFailSafe(Client client) {
            this.client = client;
        }

        @Override
        public void run() {
            LOG.debug("WakeTriggerEnableFailSafe running...");
            client.wakeupTriggerIsAllowed();
        }
    }

    /**
     * Create a Client object with the property values in Properties. The only
     * reason this method will fail is if there is a required property value that is
     * missing or cannot be used.
     * 
     * @param props
     *            - a Properties object that provides the required and optional
     *            properties for the Client to initialize.
     */
    public Client(Properties props) {
        super();

        initialize(props);
    }

    /**
     * Run the client.
     * 
     * This will try to connect. If it cannot connect, it will retry (as much as
     * needed). Once connected, it will respond to input audio, send it to the
     * server, and respond with audio. If it disconnects, it will go back to trying
     * to reconnect.
     * 
     */
    @Override
    public void run() {
        // Try to connect
        String threadName = Thread.currentThread().getName();

        long reconnectDelay = MIN_RETRY_DELAY;
        long readyStateConnectAttemps = 0;
        do {
            try {
                // connect, and stay connected... - try to reconnect if needed
                // If the server connection isn't in the READY state we need to be doing one of
                // the following:
                // (if)
                // 1) NOT_CONNECTED: Try to connect
                // 2) CONNECTING: Wait for timeout for connection to occur
                // 3) CONNECTED: Wait for timeout for 'READY' state to occur
                while (ServerConnectionStatus.READY != getServerConnectionStatus()) {
                    // Any status but READY - we should close the microphone
                    readyStateConnectAttemps++;
                    try {
                        if (this.audioInput.micIsOpen()) {
                            LOG.info("Server connection is not ready and microphone is open. Close the microphone.");
                            this.audioInput.micClose();
                        }
                    } catch (RuntimeException e) {
                        // For now - print this so we can see why we can't connect...
                        LOG.error("Server connection is not ready and microphone is open - Problem closing the microphone.", e);
                    }
                    if (ServerConnectionStatus.NOTCONNECTED == getServerConnectionStatus()) {
                        // IAMAccessToken should be retrieved from the IAM service by providing it with
                        // your cloud API Key (based on your IBM ID)
                        iamAccessToken = getIAMAccessToken(iamAPiKey);

                        // We need to connect...
                        connect();
                        Thread.sleep(1500);
                        continue;
                    }
                    // Keep track of how long we've been trying to connect. If it takes too long -
                    // start over...
                    if (ServerConnectionStatus.CONNECTING == getServerConnectionStatus()) {
                        if (reconnectDelay < MAX_RETRY_DELAY) {
                            reconnectDelay++;
                        } else {
                            setServerConnectionStatus(ServerConnectionStatus.NOTCONNECTED);
                        }
                        LOG.info(String.format("Could not connect.  Re-trying attempt %d in %d seconds...", (readyStateConnectAttemps + 2), reconnectDelay));
                        try {
                            Thread.sleep(reconnectDelay * 1000);
                        } catch (InterruptedException e) {
                            // This is the main Client thread, so catch this.
                            // For now - print this so we can see why we get it...
                            LOG.error("Client main thread interrupt", e);
                        }
                    }
                }
                // We are currently connected...
                readyStateConnectAttemps = 0;
                reconnectDelay = MIN_RETRY_DELAY;
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                // Cannot THROW out of thread Run. Log it!
                LOG.error(String.format("Client main thread '%s' was Interrupted!", threadName), e);
            } catch (RuntimeException re) {
                // Cannot THROW out of thread Run. Log it!
                LOG.error(String.format("Client main thread '%s' received RuntimeException!", threadName), re);
                // Now STOP
                break;
            } catch (Error err) {
                // Cannot THROW out of thread Run. Log it!
                LOG.error(String.format("Client main thread '%s' received Error!", threadName), err);
            }
        } while (WE_SHOULD_KEEP_RUNNING);
    }

    /**
     * Gets the current connection status between the client and the server.
     * 
     * @return
     */
    public ServerConnectionStatus getServerConnectionStatus() {
        synchronized (serverConnectionStatusLock) {
            return this.serverConnectionStatus;
        }
    }

    /**
     * Sets the current connection status. If the status is different from the
     * previous status, send a status update. If the status is the same as the
     * previous status, check the last time a status update was sent, and send
     * status if enough time has passed (avoids sending a barrage of status
     * messages.
     * 
     * TODO: Also update the 'local' status indicator.
     * 
     * @param connectionStatus
     *            - the current connection status
     */
    private void setServerConnectionStatus(ServerConnectionStatus connectionStatus) {
        synchronized (serverConnectionStatusLock) {
            ServerConnectionStatus currentStatus = this.serverConnectionStatus;
            if (connectionStatus != currentStatus || (System.currentTimeMillis() > (this.serverConnectionStatusLastSentTS + 3000))) {
                this.serverConnectionStatus = connectionStatus;
                sendConnectionStatus();
                // TODO: Adjust local status indicator.
                String serverPortInfo = "";
                if (null != this.watsonPort) {
                    serverPortInfo = ":" + this.watsonPort;
                }
                switch (this.serverConnectionStatus) {
                case CLOSING:
                    // Rapid blink at 0.3 second rate
                    // TODO: Set to RED
                    LOG.info(" Server-Connection: Closing...");
                    this.indicator.blink(300);
                    break;
                case CONNECTED:
                    // Blink the indicator at a 1.75 second rate
                    // TODO: Set to GREEN
                    LOG.info(String.format(" Server-Connection: Connected to %s%s...", this.watsonHost, serverPortInfo));
                    this.indicator.blink(1750);
                    break;
                case CONNECTING:
                    // Blink the indicator at a 1.5 second rate
                    // TODO: Set to BLUE
                    LOG.info(String.format(" Server-Connection: Connecting to %s%s...", this.watsonHost, serverPortInfo));
                    this.indicator.blink(1500);
                    break;
                case NOTCONNECTED:
                    // Blink the indicator at 0.75 second rate
                    // TODO: Set to RED
                    LOG.info(" Server-Connection: NotConnected...");
                    this.indicator.blink(750);
                    break;
                case READY:
                    // Turn off the (probably blinking) indicator
                    // TODO: Nice connect melody?
                    LOG.info(" Server-Connection: READY...");
                    this.indicator.off();
                    break;
                default:
                    LOG.warn(" Server-Connection: ???");
                    break;
                }
            }
        }
    }

    /**
     * Sends the connection status to the Socket Command Processor to be sent to a
     * controller.
     */
    public void sendConnectionStatus() {
        synchronized (serverConnectionStatusLock) {
            SocketCommandProcessor scp = getSocketCommandProcessor();
            if (null != scp) {
                switch (this.serverConnectionStatus) {
                case CONNECTED:
                    getSocketCommandProcessor().sendServerConnected();
                    break;
                case CONNECTING:
                    getSocketCommandProcessor().sendServerConnecting();
                    break;
                case NOTCONNECTED:
                    getSocketCommandProcessor().sendServerNotConnected();
                    break;
                case CLOSING:
                    getSocketCommandProcessor().sendServerNotConnected();
                    break;
                case READY:
                    getSocketCommandProcessor().sendServerConnectionReady();
                default:
                    break;
                }
                this.serverConnectionStatusLastSentTS = System.currentTimeMillis();
            }
        }
    }

    /**
     * Sends the wake trigger allowed status to the Socket Command Processor to be
     * sent to a controller.
     */
    public void sendWakeupTriggerAllowedStatus() {
        SocketCommandProcessor scp = getSocketCommandProcessor();
        if (null != scp) {
            synchronized (wakeupTriggerAllowedLock) {
                scp.sendWakeupTriggerIsAllowed(this.isWakeupTriggerAllowed());
                this.wakeupTriggerAllowedStatusLastSentTS = System.currentTimeMillis();
            }
        }
    }

    public StatusIndicator getIndicator() {
        return this.indicator;
    }

    public void onMicClose() {
        getSocketCommandProcessor().sendMicrophoneClose();
    }

    public void onMicOpen() throws InterruptedException {
        getSocketCommandProcessor().sendMicrophoneOpen();
    }

    public boolean micIsOpen() {
        return audioInput.micIsOpen();
    }

    public boolean finishedPlayingOutput() {
        LOG.debug("Client.finishedPlayingOutput()");
        currentAudioId = null;
        this.indicator.off();
        runEndOfAudioOutputTask();
        return true;
    }

    public void runEndOfAudioOutputTask() {
        // Finish up the audio output, prompt if asked to, etc.
        if (null != endOfAudioOutputTask) {
            endOfAudioOutputTask.run();
        }
    }

    public boolean setOutputToAudioSocket() {
        audioOutput.setOutputToAudioSocket(true);
        return true;
    }

    public boolean setOutputToSpeaker() {
        audioOutput.setOutputToAudioSocket(false);
        return true;
    }

    public void closeAndAcceptAudioConnection() {
        this.audioSocket.closeCurrentSocket();
    }

    public boolean isServerConnectionReady() {
        synchronized (serverConnectionStatusLock) {
            return (ServerConnectionStatus.READY == serverConnectionStatus);
        }
    }

    /**
     * Enables a wake up trigger to start the system listening.
     */
    public void wakeupTriggerIsAllowed() {
        synchronized (wakeupTriggerAllowedLock) {
            // Attempt to cancel a pending enabler
            cancelWakeupTriggerEnabler();

            // Close the mic if it is open
            if (this.audioInput.micIsOpen()) {
                this.audioInput.micClose();
            }

            wakeupTriggerAllowed = true;
            LOG.debug("Wakeup trigger IS NOW allowed.");
            getSocketCommandProcessor().sendWakeupTriggerIsAllowed(true);
        }
    }

    /**
     * Disables the wake up trigger. A call to wakeupTriggerIsAllawed re-enables it.
     */
    private void wakeupTriggerNotAllowed() {
        synchronized (wakeupTriggerAllowedLock) {
            LOG.debug("Wakeup trigger IS NOW NOT allowed.");
            cancelWakeupTriggerEnabler();
            wakeupTriggerAllowed = false;
            getSocketCommandProcessor().sendWakeupTriggerIsAllowed(false);
        }
    }

    public boolean isWakeupTriggerAllowed() {
        synchronized (wakeupTriggerAllowedLock) {
            synchronized (serverConnectionStatusLock) {
                return (this.wakeupTriggerAllowed && (ServerConnectionStatus.READY == this.serverConnectionStatus));
            }
        }
    }

    /**
     * The error (RuntimeException) that caused the client to close (fail)
     * 
     * @return error
     */
    public RuntimeException getError() {
        return error;
    }

    public long getStatusPingRate() {
        return statusPingRate;
    }

    public void scheduleWakeupTriggerEnable() {
        synchronized (serverConnectionStatusLock) {
            // Cancel an existing one
            cancelWakeupTriggerEnabler();
            WakeTriggerEnableFailSafe wakeTriggerEnableFailSafe = new WakeTriggerEnableFailSafe(this);
            wakeTriggerEnablerFuture = wakeTriggerEnableScheduler.schedule(wakeTriggerEnableFailSafe, 45000, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelWakeupTriggerEnabler() {
        synchronized (serverConnectionStatusLock) {
            if (null != wakeTriggerEnablerFuture) {
                wakeTriggerEnablerFuture.cancel(false);
                wakeTriggerEnablerFuture = null;
            }
        }
    }

    @Override
    public void onOpen(final WebSocket webSocket, Response response) {
        LOG.debug("onOpen");
        this.webSocket = webSocket;
        setServerConnectionStatus(ServerConnectionStatus.CONNECTED);

        sendAudioOptions(webSocket);

        // Indicate we have connected
        setServerConnectionStatus(ServerConnectionStatus.READY);
        // TODO: Play connect tone

        wakeupTriggerIsAllowed();
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        LOG_SERVER_COMM_RECEIVE.debug("onMessage...");
        LOG_SERVER_COMM_RECEIVE.trace(" >" + text);
        try {
            JSONObject response = new JSONObject(text);
            handleAction(response);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (JSONException e) {
            LOG.error("Error parsing message. " + e.toString());
            LOG.error(text);
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        LOG_SERVER_COMM_RECEIVE.info("*** onMessage - Received binary data ***");
        LOG.error("onMessage - Received binary data");
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        setServerConnectionStatus(ServerConnectionStatus.CLOSING);
        // TODO: Anything we need to do here?
        LOG.info("\nonClosing from websocket: code=" + code + " reason=" + reason + " ServerConnectionStatus=", this.serverConnectionStatus);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        setServerConnectionStatus(ServerConnectionStatus.NOTCONNECTED);
        LOG.info("\nonClosed from websocket: code=" + code + " reason=" + reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        LOG.error(String.format("\nonFailure from websocket: %s", t.toString()), t);
        setServerConnectionStatus(ServerConnectionStatus.NOTCONNECTED);
        this.hasFailed = true;
    }

    public synchronized void notifyOfThreadStart(final Thread thread) {
        String name = thread.getName();
        LOG.info(String.format("Client thread '%s' started", name));
    }

    public synchronized void notifyOfThreadStop(final Thread thread) {
        String name = Thread.currentThread().getName();
        LOG.info(String.format("Thread '%s' stopped.", name));
    }

    public SocketCommandProcessor getSocketCommandProcessor() {
        synchronized (socketsLock) {
            return this.socketCommandProcessor;
        }
    }

    volatile JSONObject promptSttOptionsInResponse = null;
    volatile boolean simplePromptInResponse = false;
    volatile boolean shouldPrompt = false;

    private synchronized void handleAction(JSONObject response) throws InterruptedException {
        String action = response.optString("action");
        String id;
        JSONObject data = null;
        if (action.isEmpty()) {
            LOG.error("No action key in response.");
            return;
        } else {
            if (!"audio_data".equals(action)) {
                LOG.debug(String.format("\n******** data action type:\"%s\"", action));
            }
        }

        JSONObject jdata = response.optJSONObject("data");

        switch (action) {
        case "error":
            String errMsg = null;
            JSONObject errData = response.optJSONObject("error");
            if (null != errData) {
                errMsg = errData.optString("message");
            } else { // Try to read it as a string rather than an object
                errMsg = response.optString("error");
            }
            if (null == errMsg || errMsg.isEmpty()) {
                errMsg = "<< no error message available >>";
            }
            LOG.warn(String.format("******** data error message: %s", errMsg));
            return;

        case "stt_transcript":
            transcriptReceivedTime = System.currentTimeMillis();
            LOG.info(String.format("STT transcript -> '%s' confidence -> %.2f%% transaction id -> '%s'", response.optString("transcript"), response.optDouble("confidence") * 100,
                    response.optString("transactionId")));
            if (this.audioInput.micIsOpen()) {
                this.audioInput.micClose();
            }
            break;

        case "text":
            textReceivedTime = System.currentTimeMillis();
            LOG.info(String.format("Text: \"%s\"", response.optString("speech")));

            voiceUrl = response.optString("voice");

            // Check for 'prompt' (if true, open mic after response playback is complete)
            promptSttOptionsInResponse = response.optJSONObject("prompt");
            simplePromptInResponse = response.optBoolean("prompt", false);
            shouldPrompt = (simplePromptInResponse || (promptSttOptionsInResponse != null));

            LOG.debug(String.format("prompt:%b SttOptions:%s", shouldPrompt, promptSttOptionsInResponse));
            if (null != endOfAudioOutputTask) {
                endOfAudioOutputTask.shouldPrompt(shouldPrompt, promptSttOptionsInResponse);
            }
            if (!muteThisClient) {
                if (enableResponseUrlProcessing && voiceUrl != null && !voiceUrl.isEmpty()) {
                    LOG.debug(String.format("client will play audio response from URL: %s", voiceUrl));
                    urlMode = true;
                    AudioPlayer player = new AudioPlayer(voiceUrl);
                    player.addListener(this);
                    player.shouldPrompt = shouldPrompt;
                    player.start();
                } else {
                    urlMode = false;
                }
            } else {
                LOG.debug("client muted");
            }
            break;

        case "response":
            responseReceivedTime = System.currentTimeMillis();
            data = response.optJSONObject("data");
            if (data == null) {
                LOG.error("No data key in response object.");
                break;
            }
            if (debug)
                Utils.printJSON(data);

            // Process CARD if there is one.
            // CARD (if present) can have client control information ('stop', 'volume-up',
            // 'volume-down')
            boolean allowWakeUpTriggerAfterProcessing = true;
            JSONObject card = data.optJSONObject("card");
            if (null != card) {
                // TODO: Create method/class to handle this
                String type = card.optString("type");
                if ("command".equalsIgnoreCase(type)) { // local command
                    JSONObject content = card.optJSONObject("content");
                    if (null != content) {
                        String commandAction = content.optString("action", "UNKNOWN").toUpperCase();
                        String commandFeature = content.optString("feature", "UNKNOWN").toUpperCase();
                        LOG.debug(String.format("CARD response - FEATURE: '%s'  ACTION: '%s'", commandFeature, commandAction));
                        switch (commandFeature) {
                        // TODO: Create class to handle this (could be different on each case - but not
                        // adding comment to each case)
                        case "PLAYBACK":
                            if ("RESUME".equals(commandAction)) {
                                LOG.debug(" Server requested to resume playback");
                                getSocketCommandProcessor().sendPlaybackResume();
                            }
                            if ("STOP".equals(commandAction) || "MUTE".equals(commandAction)) {
                                LOG.debug(" Server requested to stop (mute) playback");
                                currentAudioId = null;
                                this.audioOutput.stop();
                                getSocketCommandProcessor().sendPlaybackStop();
                                // Don't enable the wake up trigger. End of playback will do it.
                                allowWakeUpTriggerAfterProcessing = false;
                                // TODO - Create a stop_playback command on the server and send it from here.
                            }
                            break;
                        case "VOLUME":
                            if ("DECREASE".equals(commandAction)) {
                                LOG.debug(" Server requested to decrease volume");
                                getSocketCommandProcessor().sendVolumeDecrease();
                            } else if ("INCREASE".equals(commandAction)) {
                                LOG.debug(" Server requested to increase volume");
                                getSocketCommandProcessor().sendVolumeIncrease();
                            } else if ("MUTE".equals(commandAction)) {
                                LOG.debug(" Server requested to mute volume");
                                getSocketCommandProcessor().sendVolumeMute();
                            } else if ("UNMUTE".equals(commandAction)) {
                                LOG.debug(" Server requested to unmute volume");
                                getSocketCommandProcessor().sendVolumeUnmute();
                            }
                            break;
                        default:
                            // Unknown client feature (at this time)
                            LOG.warn(String.format("CARD response... Unknown local command FEATURE: %s, COMMAND: %s", commandFeature, commandAction));
                            break;
                        }
                    }
                }
            } else if (null != currentAudioId && null != previousAudioId && !currentAudioId.equals(previousAudioId)) {
                // Treat any other response that doesn't have a CARD as a STOP so it can start
                // playing the new response.
                LOG.debug(String.format(" Server sent a different response without a CARD - treat as STOP and play new response. Current-ID: %s  Previous-ID: %s", currentAudioId,
                        previousAudioId));
                // Clear current audioId
                previousAudioId = null;

                // commented the next line as it was causing a bug - after 2-3 utterances the
                // voice playbacks stops
                // this.audioOutput.stop();

                getSocketCommandProcessor().sendPlaybackStop();
                // Don't enable the wake up trigger. End of playback will do it.
                allowWakeUpTriggerAfterProcessing = false;
                // TODO - Create a stop_playback command on the server and send it from here.
            }

            // data.speech.text for 0.5 server
            // data.say for 0.2 server
            JSONObject speech = data.optJSONObject("speech");
            String responseText = (speech != null) ? speech.optString("text") : data.optString("say");
            LOG.debug(String.format("Response \"%s\"", responseText));

            if (allowWakeUpTriggerAfterProcessing) {
                wakeupTriggerIsAllowed();
            }
            break;

        case "audio_start":
            audioStartReceivedTime = System.currentTimeMillis();
            id = response.getString("id");
            // Keep track of the previous audio to handle barge in with a new question
            previousAudioId = currentAudioId;
            currentAudioId = id;
            // If the microphone is open, it should stop
            // capture and end. We will use AudioInput.capture() if we
            // are asked to prompt or receive another trigger.
            if (this.audioInput.micIsOpen()) {
                this.audioInput.micClose();
            }

            // Start the speaker thread, data will be written
            // to its internal buffer on subsequent audio_data
            // events.
            //
            // Indicate that a wake word trigger event is allowed
            // and blink the status LED
            this.endOfAudioOutputTask = new EndOfAudioOutputTask(id, shouldPrompt, promptSttOptionsInResponse);
            audioOutput.startAudioOutput(this.endOfAudioOutputTask);
            this.indicator.blink(500);
            break;

        case "audio_data":
            if (urlMode) {
                LOG.debug(" will play from url...");
                break;
            }

            // Indicate we received data (only goes to console)
            System.out.print('&');

            id = response.getString("id");
            if (!id.equals(currentAudioId)) {
                LOG.warn("out of sequence audio_data dropped");
                return;
            }

            // ZZZ debouncer.call(id);
            data = response.optJSONObject("data");

            if (data == null) {
                LOG.error("No data key in response object.");
                break;
            }

            JSONArray bufferData = data.optJSONArray("data");

            if (bufferData == null) {
                LOG.error("No buffer data array in response object.");
                break;
            }

            Iterator bufferIterator = bufferData.iterator();
            int dataLength = bufferData.length();
            byte[] speakerData = new byte[dataLength];
            if (logAdditionalAudioInfo) {
                LOG.debug(String.format("AD: \"%d\"", dataLength));
            }

            audioPacketCount++;
            audioDataSize += dataLength;

            int i = 0;

            while (bufferIterator.hasNext()) {
                Byte element = ((Integer) bufferIterator.next()).byteValue();
                speakerData[i] = element;
                i++;
            }
            audioOutput.writeNativeBytes(speakerData);
            break;

        case "audio_end":
            LOG.debug(String.format("handleAction:audio_end - Prompt:%b\n %s", shouldPrompt, response));

            voiceUrl = null;
            audioEndReceivedTime = System.currentTimeMillis();

            id = response.getString("id");
            if (!id.equals(currentAudioId)) {
                LOG.warn("out of sequence audio_end ignored");
                return;
            }

            if (null != endOfAudioOutputTask) {
                endOfAudioOutputTask.complexPromptSttOptions(promptSttOptionsInResponse);
            }

            // Output performance data
            if (logAdditionalAudioInfo) {
                long sttResponseTime = transcriptReceivedTime - openMicTime;
                long responseTime = responseReceivedTime - transcriptReceivedTime;
                long audioStartTime = audioStartReceivedTime - transcriptReceivedTime;
                long audioDataTime = audioEndReceivedTime - audioStartReceivedTime;
                LOG.info(String.format("PERF: \tSTT-Returned=%d \tResponse=%d \tAudio-Start=%d \tAudio-Data-Time=%d \tAudio-Packets=%d \tAudio-Data-Size=%d", sttResponseTime,
                        responseTime, audioStartTime, audioDataTime, audioPacketCount, audioDataSize));
            }

            audioOutput.finish();
            break;

        default:
            break;
        }
    }

    private void continueConversation(String id, JSONObject complexPromptSttOptions) {
        LOG.debug("Continue conversation...");
        // Call capture now rather than waiting for another trigger.
        wakeupTriggerNotAllowed(); // Don't allow trigger since we are prompting
        this.audioInput.setSttOptions(complexPromptSttOptions);
        this.audioInput.capture();
    }

    private int writeToServerCount;
    private int writeToServerBytes;
    private String iamAPiKey;
  
    public synchronized void clearServerWriteLogging() {
        writeToServerCount = 0;
        writeToServerBytes = 0;
    }

    public synchronized void logFinalServerWriteStatus() {
        int bytes = writeToServerBytes;
        int count = writeToServerCount;
        int average = (count > 0 ? bytes / count : 0);
        LOG.debug(String.format("\nWrote to server. Times: %d Total Bytes: %d Avg: %d", count, bytes, average));
    }

    public synchronized void writeToServer(String s) {
        ServerConnectionStatus scstatus = getServerConnectionStatus();
        if ((ServerConnectionStatus.CONNECTED == scstatus || ServerConnectionStatus.READY == scstatus) && !this.hasFailed()) {
            LOG_SERVER_COMM_SEND.debug("writeToServer...");
            LOG_SERVER_COMM_SEND.trace(" >" + s);
            this.webSocket.send(s);
            writeToServerCount++;
            writeToServerBytes += s.length();
            System.out.print('^');
        }
    }

    private synchronized boolean hasFailed() {
        return this.hasFailed;
    }

    private synchronized void setHasFailed(boolean failed) {
        this.hasFailed = failed;
    }

    /**
     * Called by the GPIO, keyboard listen, and socket listen threads. This will
     * trigger the audio listening if it is currently allowed.
     * 
     * @param inputSource
     * @return true if trigger is allowed, false is not currently allowed
     * @throws InterruptedException
     */
    public boolean onWakeupTriggerReceived(AudioInput.InputSource inputSource) throws InterruptedException {
        // This message is a bit 'expensive' so checking for being enabled
        if (LOG.isDebugEnabled()) {
            CallStack from = new CallStack();
            LOG.debug("Wakeup trigger received...", from);
        }
        if (!isWakeupTriggerAllowed()) {
            LOG.debug("Wakeup trigger not allowed - trigger ignored!");
            return false;
        }

        // Cancel output
        audioOutput.cancel(); // TODO: really need to pause in case it is raise/lower volume - but for now
                              // we'll do this.

        // Enable output
        audioOutput.enable();

        this.audioInput.setInputSource(inputSource);
        // Clear our performance values
        if (logAdditionalAudioInfo) {
            openMicTime = System.currentTimeMillis();
            transcriptReceivedTime = 0;
            responseReceivedTime = 0;
            textReceivedTime = 0;
            audioStartReceivedTime = 0;
            audioEndReceivedTime = 0;
            audioPacketCount = 0;
            audioDataSize = 0;
        }
        wakeupTriggerNotAllowed();
        audioInput.capture();

        return true;
    }

    /**
     * Exit client due to an error.
     */
    private void joinThreads(Exception e) {
        // Cleanup microphone.
        LOG.error("Client is joining threads to exit due to: " + e, e);
        try {
            if (this.audioInput != null) {
                if (this.audioInput.micIsOpen()) {
                    this.audioInput.micClose();
                }
            }
        } finally {
            // Cleanup other threads by interrupting them so that their
            // exception handlers can run. Wait for each thread to finish
            // running using join()
            try {
                for (Thread t : this.threads) {
                    t.interrupt();
                    t.join();
                }
                if (this.indicator != null) {
                    this.indicator.off();
                }
                // Cancel audio output
                audioOutput.cancel();
            } catch (InterruptedException cancelling) {
                // Exiting due to cancel request
            } finally {
                // Set flags so that Driver class knows to create a new connection
                setServerConnectionStatus(ServerConnectionStatus.NOTCONNECTED);
                LOG.debug("Finished thread cleanup");
            }
        }
    }

    private void initialize(Properties props) {
        // Required parameters
        watsonHost = props.getProperty("host");
        iamAPiKey = props.getProperty("IAMAPIKey");
        skillset = props.getProperty("skillset");
        tenantID = props.getProperty("tenantID");

        // Optional parameters
        watsonPort = props.getProperty("port");

        language = props.getProperty("language");
        engine = props.getProperty("engine");

        String noSsl = props.getProperty("nossl", "false");
        watsonSsl = noSsl.equalsIgnoreCase("false");
        // the userId
        userID = props.getProperty("userID");
        watsonVoice = props.getProperty("voice");
        commandSocketPort = Integer.parseInt(props.getProperty("cmdSocketPort", "10010"));
        audioSocketPort = Integer.parseInt(props.getProperty("audioSocketPort", "10011"));
        statusPingRate = Long.parseLong(props.getProperty("statusPingRate", "7000"));
        debug = props.getProperty("debug", "false").equalsIgnoreCase("true");
        enableResponseUrlProcessing = props.getProperty("urltts", "true").equalsIgnoreCase("true");
        muteThisClient = props.getProperty("mute", "false").equalsIgnoreCase("true");
        logAdditionalAudioInfo = props.getProperty("logAdditionalAudioInfo", "false").equalsIgnoreCase("true");

        if (LOG.isDebugEnabled()) {
            java.util.logging.Logger.getLogger(OkHttpClient.class.getName()).setLevel(Level.FINE);
        }

        LOG.info("WATSON HOST: " + watsonHost);
        LOG.info("WATSON PORT (Optional): " + watsonPort);
        LOG.info("SKILLSET: " + skillset);
        LOG.info("WATSON IAM API Key: " + (null == iamAPiKey ? "null" : "*****"));
        LOG.info("USER ID (Optional): " + userID);
        LOG.info("Language (Optional): " + language);
        LOG.info("Engine (Optional): " + engine);
        LOG.info("WATSON HOST: " + watsonHost);
        LOG.info("WATSON PORT: " + watsonPort);
        LOG.info("SKILLSET: " + skillset);        
        LOG.info("TENANT ID (Optional): " + tenantID);        
        LOG.info("WATSON IAM API Key: *****");        

        if (StringUtils.isBlank(watsonHost) || StringUtils.isBlank(skillset) || StringUtils.isBlank(iamAPiKey)) {
            LocalAudio.playFlacFile(LocalAudio.ERROR_INVALID_CONFIG);
            throw new Error("Missing required host, authentication or configuration information.  Check the configure.properties file.  Aborting...");
        }

        String defaultAudioPropertyValue = props.getProperty("useDefaultAudio", "true");
        audioOutput.setUseDefaultAudio(defaultAudioPropertyValue.equalsIgnoreCase("true"));

        try {
            boolean nogpio = props.getProperty("nogpio", "false").equalsIgnoreCase("true");
            String wakeupClassname = (nogpio ? "wa.trigger.ListenKey" : "wa.trigger.ListenGpio");
            Class<?> clazz = Class.forName(wakeupClassname);
            wakeupClassCtor = clazz.getConstructor(Client.class);

            if (nogpio) {
                this.indicator = new StatusConsole();
            } else {
                this.indicator = new StatusLED();
            }

            // Create a thread to capture GPIO or Enter-Key wake up triggers
            Thread wakeupTriggerThread = (Thread) wakeupClassCtor.newInstance(this);
            wakeupTriggerThread.start();
            threads.add(wakeupTriggerThread);
            this.socketCommandProcessor = new SocketCommandProcessor(this, commandSocketPort);
            socketCommandProcessor.start();
            threads.add(socketCommandProcessor);
            this.audioSocket = new AudioSocket(this.audioSocketPort);
            this.audioSocket.start();
            threads.add(audioSocket);

            // Setup the audio input
            this.audioInput = new AudioInput(this, audioSocket);

            // Setup the speaker
            this.audioOutput.setAudioSocket(audioSocket);

            this.statusPingSender = new StatusPing(this);

            // Done with initialization
            LOG.info("Done setting up client.");
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            joinThreads(e);
            LocalAudio.playFlacFile(LocalAudio.ERROR_INVALID_CONFIG);
            throw new Error(e);
        } catch (Exception e) {
            joinThreads(e);
            throw new Error(e);
        }

    }

    private String getIAMAccessToken(String apikey) {
        String IAMAccessToken = "";

        String urlParameters = "apikey=" + apikey + "&grant_type=urn%3Aibm%3Aparams%3Aoauth%3Agrant-type%3Aapikey";
        byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
        int postDataLength = postData.length;
        String request = "https://iam.bluemix.net/oidc/token";
        URL url = null;
        HttpURLConnection conn = null;
        try {
            url = new URL(request);

            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("cache-control", "no-cache");
            conn.setRequestProperty("accept", "application/json");
            // conn.setRequestProperty( "charset", "utf-8");
            // conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength
            // ));
            conn.setUseCaches(false);
            conn.getOutputStream().write(postData);

            String response = org.apache.commons.io.IOUtils.toString(conn.getInputStream(), StandardCharsets.UTF_8);
            JSONObject jsonObj = new JSONObject(response);
            IAMAccessToken = jsonObj.getString("access_token");

        } catch (IOException e) {
            LOG.error(String.format("Error - Could not connect to IAM endpoint or could not process token: %s", e.getMessage()), e);
            // try to get more detailed information about the error.
            if (null != conn) {
                try {
                    int responseCode = conn.getResponseCode();
                    // TODO: Make these exceptions specific to the error
                    switch (responseCode) {
                    case 400:
                        error = new AuthenticationError(responseCode, e);
                        break;
                    case 404:
                        error = new AuthenticationError(responseCode, e);
                        break;
                    default:
                        error = new RuntimeException(e);
                        break;
                    }
                } catch (IOException e2) {
                    error = new ConnectionError(e2.getMessage());
                }
            } else {
                error = new ConnectionError(e.getMessage());
            }
            setHasFailed(true);
            throw error;
        }
        return IAMAccessToken;
    }

    /**
     * Connect the client to the Watson (Websocket) Server. If the client is
     * currently connected, it will first disconnect and then attempt a new
     * connection.
     * 
     * @throws InterruptedException
     * @throws IOException
     */
    private synchronized void connect() throws InterruptedException {

        ServerConnectionStatus scstatus = getServerConnectionStatus();
        boolean isConnected = (ServerConnectionStatus.CONNECTED == scstatus || ServerConnectionStatus.READY == scstatus);
        if (isConnected) {
            disconnect(1000, "Establishing new connection");
        }
        setServerConnectionStatus(ServerConnectionStatus.CONNECTING);

        setHasFailed(false);

        // Build HTTP client
        OkHttpClient httpClient = new OkHttpClient.Builder().pingInterval(3000, TimeUnit.MILLISECONDS).readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(3000, TimeUnit.MILLISECONDS).retryOnConnectionFailure(true).build();

        try {
            // Build request
            String webSocketUrl = (watsonSsl ? "wss" : "ws")
                    + "://" + watsonHost
                    + (watsonPort == null ? "" : ":" + watsonPort) + "?skillset=" + skillset+"&userID=" + userID + "&language=" + language + "&engine=" + engine;
            Request request = new Request.Builder()
                    .url(webSocketUrl)
                    .addHeader("tenantId", tenantID)
                    .addHeader("Authorization", "Bearer " + IAMAccessToken)
                    .build();


            // initialize the watch dog
            Runnable cleanup = new CleanUp(this.audioInput, this.audioOutput, this.indicator);
            // ZZZ - why is this needed?
            // debouncer = new Debouncer<String>(cleanup, watchDogTimeout);

            // Create websocket connection
            webSocket = httpClient.newWebSocket(request, this);

        } catch (Exception e) {
            setServerConnectionStatus(ServerConnectionStatus.NOTCONNECTED);
            throw new RuntimeException(e);
        } finally {
            // ZZZ - Look into this!!!
            // Trigger shutdown of the dispatcher's executor so this process can exit
            // cleanly.
            // httpClient.dispatcher().executorService().shutdown();
        }
    }

    /**
     * Disconnect from the Watson (Websocket) Server if connected.
     * 
     * @param webSocketCode
     *            - websocket exit code as defined in
     *            https://tools.ietf.org/html/rfc6455#section-7.4
     * @param reason
     *            - Reason for closing the connection or null
     * @throws InterruptedException
     */
    public void disconnect(int webSocketCode, String reason) throws InterruptedException {
        if (null == webSocket) {
            return;
        }

        // Tell the audio input and audio output to stop
        audioInput.micClose();
        audioOutput.cancel();

        webSocket.close(webSocketCode, reason);
        // It seems 'webSocket.close' should take care of this, but just in case
        Thread.sleep(800);
        setServerConnectionStatus(ServerConnectionStatus.NOTCONNECTED);
    }

    private void sendAudioOptions(WebSocket webSocket) {
        String sttOptions = ClientHelpers.getClientSTTOptionsAction().toString();
        LOG_SERVER_COMM_SEND.info("sendAudioOptions...");
        LOG_SERVER_COMM_SEND.trace(" >" + sttOptions);
        webSocket.send(sttOptions);
        String ttsOptions = ClientHelpers.getClientTTSOptionsAction(watsonVoice, enableResponseUrlProcessing).toString();
        LOG_SERVER_COMM_SEND.trace(" >" + ttsOptions);
        webSocket.send(ttsOptions);
    }

    private void sayLocalIP(WebSocket webSocket) {
        try {
            List<InetAddress> addresses = LocalNetworkInterface.getAddresses();

            InetAddress match = null;

            for (InetAddress address : addresses) {
                boolean isSSHReachable = LocalNetworkInterface.isReachableViaSSH(address, 100);
                LOG.info(String.format("Address: %s\nHostname: %s\n\tis SSH reachable %b", address.getHostAddress(), address.getCanonicalHostName(), isSSHReachable));
                if (isSSHReachable) {
                    match = address;
                    break;
                }
            }

            if (match == null) {
                String msg = "Unfortunately, this device is not reachable via SSH";
                LOG.info(msg);
            } else {
                String addressStr = match.getHostAddress();
                String readableAddress = String.format("<say-as interpret-as=\"digits\">%s</say-as>", addressStr.replaceAll("\\.", "<break time=\"100ms\"/>."));
                String fmtmsg = "This device is reachable via SSH at %s";
                LOG.info(String.format(fmtmsg, addressStr));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void say(String text, WebSocket webSocket) {
        String sayAction = ClientHelpers.getTTSAction(text).toString();
        LOG_SERVER_COMM_SEND.debug("Say - " + sayAction);
        webSocket.send(sayAction);
    }
}
