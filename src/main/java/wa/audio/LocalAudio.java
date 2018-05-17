package wa.audio;
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

import javax.sound.sampled.*;
import javax.sound.sampled.DataLine.Info;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;

import static javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;
import static javax.sound.sampled.AudioSystem.getAudioInputStream;

public class LocalAudio {
    // Initialize our logger
    private static final Logger LOG = LogManager.getLogger(LocalAudio.class);

    public static final String ERROR_AUTH = "error-auth";
    public static final String ERROR_CONFIG = "error-config";
    public static final String ERROR_NO_CONFIG_FILE = "error-no-config-file";
    public static final String ERROR_NETWORK = "error-network";
    public static final String ABORTING = "aborting";

    private static final String ANNOUNCE_IP = "announce-ip";

    public static void sayIP(InetAddress ip) {
        play(ANNOUNCE_IP);
        byte[] octets = ip.getAddress();
        for (int i = 0; i < octets.length; i++) {
            int number = ((int) octets[i]) & 0xff;
            char[] digits = String.valueOf(number).toCharArray();
            for (char digit : digits) {
                play(String.valueOf(digit));
            }
            if (i < octets.length - 1)
                play("dot");
        }
    }

    public static void play(String message) {
        String file = message + ".flac";
        try (
                final InputStream resource = new BufferedInputStream(LocalAudio.class.getResourceAsStream(file));

                final AudioInputStream in = getAudioInputStream(resource)) {

            final AudioFormat outFormat = getOutFormat(in.getFormat());
            final Info info = new Info(SourceDataLine.class, outFormat);

            try (final SourceDataLine line =
                    (SourceDataLine) AudioSystem.getLine(info)) {

                if (line != null) {
                    line.open(outFormat);
                    line.start();
                    stream(getAudioInputStream(outFormat, in), line);
                    line.drain();
                    line.stop();
                }
            }

        } catch (UnsupportedAudioFileException
                | LineUnavailableException
                | IOException e) {
            LOG.error("Error trying to play", e);
        }
    }

    private static AudioFormat getOutFormat(AudioFormat inFormat) {
        final int ch = inFormat.getChannels();

        final float rate = inFormat.getSampleRate();
        return new AudioFormat(PCM_SIGNED, rate, 16, ch, ch * 2, rate, false);
    }

    private static void stream(AudioInputStream in, SourceDataLine line)
            throws IOException {
        final byte[] buffer = new byte[4096];
        for (int n = 0; n != -1; n = in.read(buffer, 0, buffer.length)) {
            line.write(buffer, 0, n);
        }
    }
}
