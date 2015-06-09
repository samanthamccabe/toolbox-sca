/******************************************************************************
 * Copyright (c) 2015. Samantha Fiona McCabe                                  *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *     http://www.apache.org/licenses/LICENSE-2.0                             *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

package org.haedus.phonetic;

import org.haedus.enums.FormatterMode;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertTrue;

/**
 * Samantha Fiona Morrigan McCabe
 * Created: 2/5/2015
 */
public class SequenceFactoryTest {


	@Test
	public void testGetSequence01() throws IOException {
		InputStream stream = SequenceFactoryTest.class.getClassLoader().getResourceAsStream("features.model");
		FormatterMode formatterMode = FormatterMode.INTELLIGENT;
		
		FeatureModel model = new FeatureModel(stream, formatterMode);
		
		String word = "avaːm";

		SequenceFactory factory = new SequenceFactory(model, formatterMode);

		Sequence sequence = factory.getSequence(word);
		assertTrue(!sequence.isEmpty());
	}
}