/**
 * Copyright 2018 IBM Corporation. All Rights Reserved.
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

import wa.audio.LocalAudio;

/**
 * Manual test for LocalAudio functionality.
 *
 */
public class TestLocalAudioFilePlay {

    /**
     * Manually test the ability to play local audio.
     * 
     * This will attempt to play the following:
     *   "The IP address is"
     *   "1 dot 2 dot 3"
     *   
     * @param args - NONE
     */
    public static void main(String[] args) {
        try {
            // Play: "The IP address is..."
            LocalAudio.playFlacFile(LocalAudio.ANNOUNCE_IP);
            // Play: "One two three" (using the individual audio digits with a 'dot' between)
            Thread.sleep(250);
            LocalAudio.playFlacFile("1");
            LocalAudio.playFlacFile("dot");
            Thread.sleep(30);
            LocalAudio.playFlacFile("2");
            LocalAudio.playFlacFile("dot");
            Thread.sleep(30);
            LocalAudio.playFlacFile("3");
        } catch (Throwable t) {
            System.err.println("TestLocalAudioFile failed to play due to: " + t);
            t.printStackTrace();
        }
    }
}
