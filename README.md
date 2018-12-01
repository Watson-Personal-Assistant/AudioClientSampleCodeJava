
---
Customizing  and deploying an audio client
---
In Watson Assistant Solutions, an audio client is responsible for streaming audio to and from the audio gateway using a web socket interface.

A sample audio client is provided with Watson Assistant Solutions.  You can customize the configuration of the sample client, build the client, and deploy the client to your device.

For information about the audio gateway and the audio streaming interface, see [audio](http://ibm.biz/audio_interface) section of the product documentation.

### About this task
A number of options are provided with the audio client.

#### Supported Operating Systems
The client is developed for Raspberry PI.  However, with some minor modifications, you can deploy the client on another type of controller or on a Windows or Mac OS operating system. You  might want to run the client locally during testing.  With a local build, you avoid having to download the client to a remote device and having to debug it remotely each time you make a change to the client.

#### Using the Eclipse IDE
You might want to use a Java IDE to more easily modify and debug the audio client code.  Otherwise, you can use a text editor and maven to build the JAR file.  The procedure for customizing the audio client uses the Eclipse IDE but any Java IDE can be used.

Maven is embedded in the Eclipse `.project` file that is provided with the sample audio client. Maven is configured to include debug information.  You can run the audio client on Raspberry PI in debug mode but still debug the code from the Eclipse IDE.

**Important**: If you are using the Eclipse IDE and you are deploying to a device (Raspberry Pi), you must build the JAR file using maven from the command-line to build a correctly configured JAR file.

#### Error and status messages
The sample audio client provides some FLAC sound files that are used to provide status and error messages.  If you cannot connect to the audio gateway, you are unable to use its text-to-speech service for messages. Instead, you can use FLAC files to play messages through your speaker.  For example, if the `configure.properties` file is not found, a message announcing that the file is not found is played through the speaker.

The FLAC sound files are in the `speech` directory of the sample audio client.  If you want to edit the message that is played,  complete these steps:
1. Set the TTS_USERNAME and TTS_PASSWORD environment variables to the Watson Text-to-speech service credentials.  For example:
On Linux and MAC OS
```
$ export TTS_USERNAME=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
$ export TTS_PASSWORD=xxxxxxxxxxxx
```
2. Go to `AudioClientSampleCodeJava` top-level directory.
3. Open the `synthesize.sh` file in a text editor.
4. Edit the content of message according to the needs of your environment.  For example:
   Change the line
  `synthesize "The configure properties file was not found" error-no-config-file`
   to
  `synthesize "Sorry, could not find the configure properties file" error-no-config-file`
5. To recompile the message, enter: `./synthesize.sh`.
For more information about the FLAC file format, see the [FLAC page](https://xiph.org/flac/) on the xiph.org website.

#### Using a device controller
Typically, you use the local speaker and microphone of the audio device. However, you might have a smart speaker in your environment that you would like to use to perform external processing or you might have additional device controls, for example, for volume or display. Watson Assistant Solutions provides you with the option to use your own smart speaker and microphone with the audio client. The smart speaker acts as a device controller for the audio client. You use a command socket interface to send commands between the device controller and the audio client and an audio socket interface to send audio.


##### Using socket interfaces to the client
The audio client provides socket interfaces to allow control of the audio client from a device controller and sending audio to and from the controller.  There are two socket interfaces:
  - Command socket interface: The device controller sends text-based commands on the command socket interface to the audio client.  The audio client sends status messages and commands to the device controller. The port is set using the  `cmdSocketPort` property in the `configure.properties` file.
  - Audio socket interface: Audio is sent from the device controller to the client and from the client to the controller on an audio socket interface. The port is set using the  `audioSocketPort` property in the `configure.properties` file.

For example, the device controller wants to send audio to the audio client on the audio socket interface, but the client is not ready to process the audio. The device controller sends a RAS (a trigger to the client to read from the audio socket) command on the command socket interface.  The audio client responds with a `micWakeUpNotAllowed` status message.  The device controller waits for a `micWakeUpAllowed` status message and sends data to the audio client on the audio socket interface.

##### Command socket interface
You can test the command socket interface using telnet. Complete these steps:
1. Establish a telnet connection to the audio client on the command port.
2. Wait for the client to respond with OK.
3. Send a command and terminate the command with a carriage return.

Table 1 displays the commands from the device controller to the client.

|Command |Description|
|-----|:-------------------------------|
| `OS`   |Send output to the speaker.|
| `OAS`  |Send output to the audio socket. |
| `RM`   |Read the microphone (trigger).  |
| `RAS`  |Read the audio socket (trigger).|
| `EXIT` |Disconnect.|

Table 2 displays the responses from the audio client to the device controller.

|Command |Description|
|-----|:-------------------------------|
| `OK`   |Command was received and is acknowledged.|
| `?`  | An unknown command was received. |
| `DONE`   |The client was told to disconnect.  |
| `micWakeUpNotAllowed`  |The client will not respond to the wake up command trigger.|
| `micWakeUpAllowed` |The client will respond to the wake up command trigger.|
| `micOn` | The client is expecting audio.  |
| `micOff` |The client is not expecting audio.

Table 3 displays the status messages from the audio client to the device controller to show the status of the connection to the audio gateway.

|Command |Description|
|-----|:-------------------------------|
| `serverConnected`  | The client is connected to the server but is not yet ready to start.  |
| `serverConnecting`  | The client is attempting to connect to the server.  |
| `serverConnectionReady`   | The client is connected and is ready to start. |
| `serverNotConnected `  | The client is not connected to the server. |

##### Audio socket interface
The audio socket interface sends and receives audio data streams in binary format. Currently, the format is fixed as follows:
  - Input: audio/l16 (PCM, SampleRate=16,000, Channels=1, Bits=16)
  - Output: audio/l16 (PCM, SampleRate=16,000, Channels=1, Bits=16)

If the controller sends an OAS command for diverting audio output to the audio socket, the controller must be ready to receive audio data and process it (that is, play it). No command is sent from the client to indicate that audio data will be sent.

When the controller sends a RAS command to trigger the reading of audio data from the audio socket, the client responds with a `micOn` response and starts to read data from the audio socket. The audio data is sent to the Watson server for transcription. Once the transcription has responded with an acceptable confidence level, the client sends a `micClose` response. Any further data that is received on the audio socket is discarded.

#### Sending audio to the audio gateway
For information about the flow of audio streaming from the audio client to the audio gateway in Watson Assistant Solutions, see the _How audio input is processed_ topic in the product documentation.  If you plan to use a device controller, see the _How audio input is processed with a controller_ topic in the product documentation.

For a specification of the communication messages that are sent between the audio client and the audio gateway on the web socket interface, see the _Audio streaming interface specification_ topic in the product documentation.

### Before you begin
1. Install [Java 8](https://developer.ibm.com/javasdk/downloads/sdk8/).
2. Install [Maven](https://maven.apache.org/).  If you install Maven on Raspberry PI, you might encounter an issue.  The install command `apt-get install maven` introduces a JVM that has a conflict with Java 8. To fix the issue, install the Maven binary directly on Raspberry PI.  Following the instructions [here](https://www.xianic.net/post/installing-maven-on-the-raspberry-pi/).
3. Install the [Eclipse IDE](https://www.ibm.com/cloud/eclipse).

### Procedure
Complete these steps to deploy the sample audio client to your device and to test the streaming of audio data:

#### Step 1: Clone the audio client GIT repository.
1.  Copy the  audio client repository to your local system.
    1. Go to [audio client sample Java code repository](https://github.com/Watson-Personal-Assistant/AudioClientSampleCodeJava).
    2. Click Fork to take a copy of the repository.
    3. Click Clone or download. Copy the GIT url.
    4. Open a command-line terminal and enter<br>`git clone git_url`
2. Install the node dependencies for the audio client.
    1. Enter `cd AudioClientSampleCodeJava`.
    2. Enter `npm install`.

#### Step 2: Import the audio client project into the Eclipse IDE.
1. Start Eclipse. Click `eclipse.exe`.
2. Specify a workspace to use.
3. Import the Eclipse project for the sample audio client.
  1. Go to File > Import.
  2. Under General, select `Existing Projects into Workspace` and click Next.
  3. Enable the ` Select root directory` option.
  4. Click Browse to locate the `.project` file in the top-level directory of your audio client.
  4. Click Finish.  Your audio client project is displayed in the Project Explorer.

#### Step 3: Customize your audio client.
1.  Copy the `configure.properties.example` file from the `AudioClientSampleCodeJava` directory and rename it to `configure.properties`.
2. Modify the parameters in Table 1 to suit your environment.

Table 1 - Audio client configuration parameters

| Parameter  |Description | Type |
|-----|:-------------------------|:----------------|
| `host` (mandatory)  | The URL of the audio gateway.  The URL is `wa-audio-gateway.mybluemix.net`. Note: Do not include the protocol prefix, for example, `https://` |Audio gateway connection parameter |
| `IAMAPIKey` (mandatory) | The client IAM API key for the device.  |Audio gateway connection parameter |
| `skillset`  | The skillset to be used by the audio client.  | Audio gateway connection parameter  |
| `engine `  | The speech-to-text (STT) engine that the audio gateway must use to convert speech to text.  Valid values are `watson` or `google`.  The default value is `google`.   | Speech-to-text parameter |
| `urltts`  | If set to `true`, the audio gateway plays back audio from a URL.  If set to `false`, the gateway streams audio using data messages.  The default value is `false`.  | Audio response type parameter |
| `cmdSocketPort`  | The port to use for external commands. The default port is 10010.  |External control parameter |
| `audioSocketPort`  | The port to use for audio streaming.  The default port is 10011.   |External control parameter |
| `statusPingRate`  |The rate at which the audio client sends operational status messages to its controller in milliseconds.  | External control parameter |
| `useDefaultAudio`  | If set to `true`, use the default audio output of the device.  For example, on Raspberry Pi, the default output is an aux jack.  If you are using a USB speaker, set the value to `false`.  **Tip**: If no audio is heard, change the value of this parameter.  | Audio output parameter |
| `voice`  | The text-to-speech service to use.  The default value is en-US-LisaVoice.  |Text-to-speech parameter |
| `nogpio`   | If set to `true`, use the enter key and console for the wake up command. Use the console for status. This allows the client to run on platforms other than a Raspberry Pi (for example, Mac OS and Windows). Note: This option can also be used on a Raspberry Pi to allow it to be controlled through the console rather than by connecting it to a switch and LED.  The default value is `false`. If set to `false`, use the GPIO-connected push-to-talk switch for wake-up and use an LED that is connected to GPIO for status.  **Important**: To enable a user account to have access to GPIO without a sudo, set `WIRINGPI_GPIOMEM=1` on the Raspberrry Pi. | Raspberry Pi configuration parameter |
| `nossl`  |If set to `true`, connect to the gateway without using an SSL protocol.  The value is set to `false` by default.    | SSL parameter |


#### Step 4: Build the audio client JAR file.
To build the package, go to the top-level directory of your audio client.  From the command-line enter: `mvn package`.
A JAR file for the audio client is created in the `target/` directory. The file name includes a version number.  Each build includes a `-SNAPSHOT` suffix.

#### Step 5: Deploy the audio client JAR file to your device.
Copy the audio client JAR file from the `/target` directory to the `/watson` directory on your device.  For example:
`$ scp target/wpa-1.4-SNAPSHOT.jar pi@192.168.1.15:~/watson`

#### Step 6: Start the audio client.
Go to the `start` directory of the audio client on your device.
To start the audio client, enter `./run.sh`. To start the audio client in debug mode, enter `./rundebug.sh`.

The commands to run the client for different operating systems are:
- Raspberry Pi:
    sudo java -jar wpa-1.4-SNAPSHOT.jar 
- Mac OS:
    java -jar wpa-1.4-SNAPSHOT.jar 
- Linux:
    java -jar wpa-1.4-SNAPSHOT.jar 
- Windows:
    java -jar wpa-1.4-SNAPSHOT.jar 

('sudo' is required for Raspberry Pi in order to access the GPIO functionality)

#### Step 7: Send audio data from the audio client.
Send audio data from the audio client to the audio gateway by waking the client up and asking a question. For example, `"Hello Watson, what is the weather like today`".

#### Step 8: Monitor the flow of audio data.
The audio client outputs the following characters to the console to indicate how data is flowing.

| Character  |Progress indicator|
|-----|:-------------------------------|
| %  | Reading a chunk of audio from the local microphone. |
| <   | Reading a chunk of audio from an audio socket.  |
| ~  | No audio is received from either the local microphone or audio socket.  |
| ^   | Sending audio to the audio gateway.  |
| &   | Receiving audio from the audio gateway. |
| @   | Sending audio to a local speaker.  |
| >   | Sending audio to an audio socket.  |
| #   | Reading from the audio socket to drain excess input.   |

The following sample output indicates that audio data is being read from an audio socket and is being sent to the audio gateway:
```
<^<^<^<^<^<^<^<^<^<^<^<^<^<^<^
```
The following sample output indicates that audio data is being received from the audio gateway and is being sent to an audio socket.
```
&>&>&>&>&>&>&>&>&>&>
```

The following sample output indicates that audio data is being read from a local microphone and is being sent to the audio gateway.
```
%^%^%^%^%^%^
```

The following sample output indicates that audio data is being received from the audio gateway and is being sent to a local speaker.
```
&@&@&@&@&@&@&@&@&@&@&@&@&@
```

The following sample output indicates that excess audio data is being read from the audio socket to clear it after a micClose command was sent.



#### Step 9: View log data.
Review the log file, if required.  The audio client uses Log4J.  The logging configuration file is in `config/log4j2.xml`.
For more information about configuring logging, see the [LOG4J website](https://logging.apache.org/log4j/2.x/manual/appenders.html).

The current file and nine previous files (compressed) are saved.  This can be changed by modifying the `config/log4j2.xml` configuration file.

#### Step 10: Recording and Analyzing Performance Data
The client will record performance data in the logs/perf.log file if the log4j2.xml configuration has the `GLOBAL.Performance` logging category level set to `debug`.

The file contains tab separated values to make it easy to import into a spreadsheet program for analysis.  Each client interaction with the Watson Assistant Solutions server is recorded on a separate line.  The numeric values are in milliseconds. The `Trigger` value is an absolute timestamp and the other time values are deltas from that (elapsed time from the trigger).  The `Packets` and `Data` values are absolute.  The `Method` value is `URL|Stream` indicating how the audio was delivered.  The `STT` and `RESP` are the text of the Speech-to-Text transcript and the text of the Text-to-Speech response.

The current file and nine previous files (compressed) are saved.  This can be changed by modifying the `config/log4j2.xml` configuration file.

### Result
The audio client is configured on your device and you have tested the streaming of audio data to the audio gateway.

### What to do next
Learn more about setting up an audio client on Raspberry PI.

Figure 1 shows a schematic of the client status indicator.
![client status indicator](Client-Status-Indicator-Schematic.jpg)

Figure 2 shows an example of how to set up Raspberry PI.
![status panel breadboard](Status-Panel-Breadboard.jpg)

- GPIO 'Pin' numbers: (BCM/WIRINGPI/Physical) GND (Any of (physical) pins: 6,9,14,20,25,30,34,39)
- Wake-Up Switch: GPIO 12/26 (Pin 32) Mute/Diagnostic Switch: GPIO 16/27 (Pin 36)
- Red LED: GPIO 25/6 (Pin 22) Green LED: GPIO 24/5 (Pin 18) Blue LED: GPIO 23/4 (Pin 16)
