# Reference implementation in Java for the Cognitive Spaces Websocket client

This application is responsible for audio streaming on the client to and from the server using the WebSocket protocol.  See the Client Interface Specification for details on the messages used to authenticate and converse with the server.

The client is developed for a Raspberry Pi, but there are configuration options to also run it from a PC or Mac.  Other platforms can easily be adapted. These options can make it easier to develop and debug (not requiring a download to the device and remote debugging for each change).


## Requirements
* Java 8
* Maven

Note: The build can be executed on any platform, and the resulting JAR deployed to the device. (e.g. Raspberry Pi)  To build directly on the Raspberry Pi, you may run into some trouble installing maven.  `apt-get install maven` brings in another JVM which conflicts with Java 8. One workaround is to install the Maven binary directly, as described here: https://www.xianic.net/post/installing-maven-on-the-raspberry-pi/


## Synthesize Local Status Sound Files (if needed)
The client uses some local [FLAC](https://xiph.org/flac/) sound files to provide status and error messages without needing to access the text to speech service. For example, it can announce that it cannot connect to the server or that the configure.properties file wasn't found.

The files used to build the JAR are in the `speech` directory. The `synthesize.sh` command can be used to regenerate them if needed. The text used for the different messages is contained in the `synthesize.sh` command.

To run the command `TTS_USERNAME` and `TTS_PASSWORD` environment variables must be set with Watson Text-to-Speech service credentials.

```
$ TTS_USERNAME=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxx
$ TTS_PASSWORD=xxxxxxxxxxxx
$ ./synthesize.sh
```


## Build the JAR
The maven `package` phase will generate a build. The result is a jar file in the `target/` directory with the version number embedded in the filename.  By convention, builds between releases will carry the `-SNAPSHOT` suffix.

```
$ mvn package
```


## Development and Debugging (ability to use Eclipse IDE)
**For deployment to a device (e.g. Raspberry Pi) the Maven build must be run from the command line (to create a correctly configured JAR)**

# Instructions for Eclipse
These instructions are for Eclipse, but those of you that use other IDE's can probably adapt to these instuctions.

For development and debugging - an `Eclipse project` has been created.  Find it as `.project` in the main folder.

Using a dedicated Java IDE like Eclipse can provide significant benefits during development (and especially debugging).  The Eclipse project is configured to function as a Maven Project/Build.  This means you can edit, build, and debug directly from Eclipse. Remote debugging is also possible.  The Maven build is configured with debug information, so if you run the client on the Raspberry Pi in debug mode you can connect to it from Eclipse and debug interactively.

Create a new workspace and import the project (as an existing Eclipse project).

You can run and debug the client within Eclipse - just set up a 'Run/Debug' configuration with the main class of 'Driver'.

To debug remotely on the Raspberry Pi:
	An example command to run the client in remote debug mode is:
	`sudo java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000 -jar wpa-1.4-SNAPSHOT.jar`
	In Eclipse, simply configure a 'Debug Configuration' that specifies the IP address and port of the target and the main class: `Driver`

The `start` directory contains startup scripts for both debug and non-debug modes. These scripts can be copied to the `watson` directory on the device.

To Run/Debug directly from Eclipse (on the local machine) you will need to set up a local Run/Debug configuration.  The main requirement for this is that you will need to indicate that the working directory is the `target/` directory and make sure that the config directory includes a properly configured `configure.properties` file (the Eclipse build does not put one there, so if a 'clean' build is performed there will not be a `configure.properties` file in the directory.  If the `configure.properties` file doesn't exist in the `config` directory the client should announce that to you (a good test really...).

*(as we find and resolve problems with the Eclipse project, we will add to this section - for example, a 'clean' build will delete 'target', so we probably want a build step to copy a `configure.properties` in at the end of the build.)*


## Deploy
Our standard deployment to a device is into the ~/watson directory.

Copy the jar (created using the `maven package` command) from the `target/` directory on your local system to the device `~/watson` directory, e.g.

```
$ scp target/wpa-1.4-SNAPSHOT.jar  pi@192.168.1.101:~/watson
```

## Run
Copy `configure.properties.example` to `configure.properties` into the the `~/watson/config` directory of the device.  Provide configuration and credential information and options by editing the following properties:

The `start` directory contains startup scripts for both debug and non-debug modes. These scripts can be copied to the `watson` directory on the device.

### Required Connection Information
* `host` The URL of the Audio Gateway server (should NOT include the protocol prefix such as "https://")
* `IAMAPIKey` The client IAM API Key associated with the device in the IBM cloud IAM management system
* `skillset` The skillset to be used by this client

### Optional Settings

#### Speech to text engine
The server supports two STT engines out of the box - Watson STT and Google STT 
* `engine` - if you use the value `watson`, the server will use the Watson STT for converting audio to text, if you use `google` the server will use the Google STT. (default=google)

#### Audio Response via URL or Streaming
The server supports returning the response via an audio URL or by streaming the audio to the client.
* `urltts` (if `true` the server will respond with a URL, if `false` the server will stream the audio (using `audio_data` messages) - default=false)

#### Socket Connection
The client provides a socket connection for an external controller for commands and audio (see following section).  
These properties allow configuring the ports used by those connections and a client status ping.
* `cmdSocketPort` (default=10010)
* `audioSocketPort` (default=10011)
* `statusPingRate` The rate at which the client will send operational status messages to a controller

#### Audio Output
* `useDefaultAudio` Use the system's default audio output for speaker (aux jack on Raspberry Pi).  Typically should be set to `false` if using a USB speaker.  However, if no audio is heard, try switching this property. (default=true)

#### Voice
* `voice` Set to a Watson TTS voice, uses en-US_LisaVoice by default.

#### Raspberry Pi GPIO Use
* `nogpio` Will use the enter key for wake up instead of a push-to-talk switch connected to GPIO and use the console for status rather than an LED wired to GPIO. This allows the client to run on platforms other than a Raspberry Pi (like a PC/Mac). *Note that this option can also be used on a Raspberry Pi to allow it to be controlled through the console rather than wiring up a switch and LED*  (default=false)

Note: Setting `WIRINGPI_GPIOMEM=1` on a Raspberry Pi enables a user account to access GPIO without `sudo`

```
$ export WIRINGPI_GPIOMEM=1
$ java -jar target/wpa-1.4-SNAPSHOT.jar
```

#### SSL Use
* `nossl` To connect to server without SSL protocols (http/ws instead of https/wss)  (default=false)

#### Logging
* `debug` generates additional log information. Defaults to false.
* `logAdditionalAudioInfo` Log information about the size of the audio packets and the time to receive them.

Note: the primary logging control is configured with the [Log4J Configuration](src/main/java/log4j2.xml). These controls will be moved there as specific loggers in the future.


## Socket Interface
The client provides a socket command and audio interface to allow remote control of the client (rather than through the GPIO, local microphone, and speaker).

### Command Socket Interface
The command socket:port provides a simple text based interface for commands to be sent to the client and for the client to send status and commands back to the controller. This interface can be easily tested with a tool like `telnet`.  Using `telnet` you can connect to the command port (as configured with the `cmdSocketPort` in configure.properties). The client will respond with `OK` and the communication can begin.  Each command is terminated with a carriage return.

The commands from the controller to the client are:
* `OS` - Output to speaker
* `OAS` - Output to Audio Socket
* `RM` - Read microphone (trigger)
* `RAS` - Read Audio Socket (trigger)
* `EXIT` - Disconnect

Responses and commands from the client are:
* `OK` - Command received and acknowledged
* `?` - Unknown command
* `DONE` - Client has been told to disconnect
* `micWakeUpNotAllowed` - Client will not respond to a wake-up trigger request
* `micWakeUpAllowed` - Client will accept a wake-up trigger to start accepting audio
* `micOn` - Client is expecting audio
* `micOff` - Client is not expecting audio
Commands sent from the client for control of the response play back.
It is suggested that a controller support these, but it's not required.
* `playbackResume` - The user is requesting that the response be resumed if possible
* `playbackStop` - The user is requesting that the response be stopped
* `volumeDown` - The user is requesting that the volume be turned down
* `volumeUp` - The user is requesting that the volume be turned up
* `volumeMute` - The user is requesting that the output be muted (this should not stop the response)
* `volumeUnmute` - The user is requesting that the output be un-muted

Status messages        
* `serverConnected` - Client is connected to the server (but not yet ready to operate)
* `serverConnecting` - Client is attempting to connect to the server
* `serverConnectionReady` - Client is connected and ready
* `serverNotConnected` - Client is not connected to a server

### Audio Socket Interface
The audio socket:port is used in conjunction with the `OAS` and `RAS` commands. The `OAS` command directs the audio data to be sent to the audio socket interface rather than directly to the local speaker.  The `RAS` command instructs the client to read from the audio socket interface rather than directly from the local microphone.

The audio socket interface sends/receives a binary audio data stream.

Currently the format is fixed at:
* Input: `audio/l16` (PCM, SampleRate=16,000, Channels=1, Bits=16)
* Output: `audio/l16` (PCM, SampleRate=16,000, Channels=1, Bits=16)

*(future releases are expected to add commands to configure the audio data format)*

If the `OAS` command is sent the controller should continuously be ready to receive audio data and process it (play it).  No command is sent from the client to indicate that audio data is forthcoming.

When the `RAS` command is sent on the command socket the client will respond with a `micOpen` response and start reading data from the audio socket.  The audio data is sent to the Watson server for transcription.  Once the transcription has responded with an acceptable confidence level the client will send a `micClose` response on the command socket. Any further data received on the audio socket will be discarded.


## Audio Data Flow Indication in Console Logging
To help with debugging, the client outputs some characters to the console that indicate how audio data is flowing. For performance reasons these are written directly to the console and not to the log file.

Audio data flow is indicated by:
* `%` - Read chunk from local microphone
* `<` - Read chunk from audio socket
* `~` - No audio data received when reading from current source (microphone or socket)
* `^` - Write to Watson server
* `&` - Receive audio from Watson server
* `@` - Output to local speaker
* `>` - Output to audio socket
* `#` - Read from audio socket to drain excess input

For example:
* `<^<^<^<^<^<^<^<^<^<^<^<^<^<^<^` indicates that data is being read from the audio socket and sent to the Watson server
* `&>&>&>&>&>&>&>&>&>&>` indicates that audio data is being received from the Watson server and sent to the audio socket
* `%^%^%^%^%^%^` indicates that data is being read from the local microphone and sent to the Watson server
* `&@&@&@&@&@&@&@&@&@&@&@&@&@` indicates that audio data is being received from the Watson server and sent to the local speaker
* `######` indicates that excess audio data is being read from the audio socket to clear it after a `micClose` command was sent

## Logging Configuration
The client uses Log4J for logging.

The logging configuration is config/log4j2.xml
There is a default configuration file there.  If a file isn't found the logging defaults to 'INFO+".
Refer to the log4J2.xml for logging classes an to the Log4J site logging.apache.org/log4j/ for information about configuring the logging.

Note that the Log4J library supports a number of 'Appenders' that can write to logs, send emails, text-message, and more and can be configured in the log4j2.xml file.  Refer to https://logging.apache.org/log4j/2.x/manual/appenders.html

## Design
The [main application loop](src/main/java/Driver.java) is responsible for initiating the connection to the server and retrying on network failure.  The application will exit if there is a configuration or other error which does not appear to be recoverable (e.g. authentication failure)

Once the client authenticates with the server by requesting a token over HTTP, a long-running WebSocket is created with that token. A typical conversation consists of the following repeating steps:

1. An interrupt begins the conversation (such as one triggered on the device by a hotword)
1. The [Audio Input thread](src/main/java/wa/audio/AudioInput.java) sends an `audio_start` message to the server via the WebSocket
1. The audio input thread captures audio and writes it to the WebSocket using `audio_data` messages until either the server responds or a timeout period has elapsed.  An `audio_end` message will follow, signaling the end of the microphone data.
1. If the server does respond to the captured audio, the incoming `audio_start` message on the [client thread](src/main/java/wa/client/Client.java) pauses the microphone, then writes the audio received from the server `audio_data` messages to a buffer.
1. That buffer is consumed by another thread controlling the [Audio Out](src/main/java/wa/audio/AudioOutput.java).  
1. Identifiers on each server response are used to prevent overlapping responses and invalid client states.  In such cases, the last server response wins.
1. An incoming `audio_end` signals the end of incoming audio to output.  If an `audio_end` is not received after a timeout period, the output is closed.  This is done to prevent waiting forever should there be an incomplete transmission from the server.
1. The client waits for a new interrupt and a new spoken phrase, unless there is a prompt in the conversation, in which case Audio Input capture is started again.

### Client Operational Control
The client processes *Command Cards* received from the server. The content of the card can perform operations like stopping the response play back, increasing or decreasing the volume, and more.

Currently, the client processes the command card and issues a corresponding command to a controller connected to the socket interface (the client does not yet respond to the commands when running stand-alone).

For example, the response JSON can contain a `card` key with a value in the form: `{"type":"command","content":{"feature":"volume","action":"increase"}}`
The client checks the response for a `card` and if found checks for a `type` of `command`.
The following *features - Actions* are supported:
* Playback - Stop
* Playback - Resume
* Volume - Decrease
* Volume - Increase
* Volume - Mute
* Volume - UnMute


## Raspberry PI Example Setup

![Schematic](assets/Client-Status-Indicator Schematic.pdf)

<img src="assets/Status-Panel-Breadboard.jpg" alt="Breadboard" width="600" />


GPIO 'Pin' numbers: (BCM/WIRINGPI/Physical)
GND (Any of (physical) pins: 6,9,14,20,25,30,34,39)

Wake-Up Switch: GPIO 12/26 (Pin 32)
Mute/Diagnostic Switch: GPIO 16/27 (Pin 36)

Red LED: GPIO 25/6 (Pin 22)
Green LED: GPIO 24/5 (Pin 18)
Blue LED: GPIO 23/4 (Pin 16)
