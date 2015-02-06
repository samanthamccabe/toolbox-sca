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

package org.haedus.tables;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Author: Samantha Fiona Morrigan McCabe
 * Created: 12/11/2014
 */
public class SymmetricTableTest {

	private static final transient Logger LOGGER = LoggerFactory.getLogger(SymmetricTableTest.class);

	private static Table<String> indexedList;

	@BeforeClass
	public static void init() {
		indexedList = new SymmetricTable<String>("", 6);
	}

	@Test
	public void testGet01() {
		String prettyTable = indexedList.getPrettyTable();

		LOGGER.info("\n{}", prettyTable);
	}
}