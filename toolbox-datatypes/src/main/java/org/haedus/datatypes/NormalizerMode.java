/*******************************************************************************
 * Copyright (c) 2015. Samantha Fiona McCabe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.haedus.datatypes;

/**
 * Class NormalizationMode
 * Samantha Fiona Morrigan McCabe
 *
 * @since 07/30/2014
 */
public enum NormalizerMode {
	NFD("NFD"),
	NFC("NFC"),
	NFKD("NFKD"),
	NFKC("NFKC"),
	NONE("NONE");

	private final String value;

	NormalizerMode(String v) {
		value = v;
	}

	@Override
	public String toString() {
		return value;
	}
}