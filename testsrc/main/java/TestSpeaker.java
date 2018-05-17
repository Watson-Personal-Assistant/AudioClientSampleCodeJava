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

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Port;
import javax.sound.sampled.SourceDataLine;

public class TestSpeaker {

	public static void main(String[] args) {

		try {
			Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();

			for (Mixer.Info aMixerInfo : mixerInfo) {
				System.out.println("Mixer.Info Name:'" + aMixerInfo.getName() + "'  Description:'" + aMixerInfo.getDescription() + "'  [" + aMixerInfo.toString() + "]");
				Mixer mixer = AudioSystem.getMixer(aMixerInfo);
				System.out.println("Mixer: " + mixer.toString());
			}

			if (AudioSystem.isLineSupported(Port.Info.SPEAKER)) {
				try {
					Port port = (Port) AudioSystem.getLine(Port.Info.SPEAKER);
					System.out.println("Port: " + port);
					SourceDataLine line = (SourceDataLine) AudioSystem.getLine(port.getLineInfo());
					System.out.println("Line: " + line);
				} catch (LineUnavailableException e) {
					e.printStackTrace();
				}
				finally {
				}
			}
		} 
		finally {
		}
	}
}
