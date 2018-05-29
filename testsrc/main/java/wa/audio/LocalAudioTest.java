package wa.audio;
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

import static javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import javax.sound.sampled.AudioFormat;

import org.junit.Test;

public class LocalAudioTest {

	@Test
	/**
	 * Test that the returned 'output' audio format is appropriate for the passed in 'input' audio format.
	 */
	public void testGetOutputAudioFormat() {
		System.out.println("LocalAudioTest.testGetOutputAudioFormat()");
		// Create in AudioFormat to represent the 'input'
		int ch = 1;
		float rate = 16000f;
		AudioFormat inputFormat = new AudioFormat(PCM_SIGNED, rate, 16, ch, ch * 2, rate, false);
		AudioFormat outputFormat = LocalAudio.getOutputAudioFormat(inputFormat);
		int outputCh = outputFormat.getChannels();
		float outputRate = outputFormat.getSampleRate();
		assertThat(outputCh, is(ch));
		assertThat(outputRate, is(rate));
		
		ch = 2;
		rate = 8000f;
		inputFormat = new AudioFormat(PCM_SIGNED, rate, 16, ch, ch * 2, rate, false);
		outputFormat = LocalAudio.getOutputAudioFormat(inputFormat);
		outputCh = outputFormat.getChannels();
		outputRate = outputFormat.getSampleRate();
		assertThat(outputCh, is(ch));
		assertThat(outputRate, is(rate));
	}

}
