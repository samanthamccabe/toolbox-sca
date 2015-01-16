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

package org.haedus.soundchange;

import org.haedus.datatypes.phonetic.Sequence;
import org.haedus.soundchange.command.Command;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Pattern;

/**
 * Samantha Fiona Morrigan McCabe
 * Created: 1/14/2015
 */
public abstract class AbstractScript implements SoundChangeScript {

	protected static final String COMMENT_STRING = "%";
	protected static final String RESERVE        = "RESERVE";

	protected static final Pattern COMMENT_PATTERN    = Pattern.compile(COMMENT_STRING + ".*");
	protected static final Pattern NEWLINE_PATTERN    = Pattern.compile("\\s*(\\r?\\n|\\r)\\s*");
	protected static final Pattern RESERVE_PATTERN    = Pattern.compile(RESERVE + ":? *");
	protected static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");


	protected final Queue<Command>                    commands;
	protected final Map<String, List<List<Sequence>>> lexicons;


	public AbstractScript() {
		commands = new ArrayDeque<Command>();
		lexicons = new HashMap<String, List<List<Sequence>>>();
	}

	@Override
	public List<List<Sequence>> getLexicon(String handle) {
		return Collections.unmodifiableList(lexicons.get(handle));
	}

	@Override
	public void process() {
		for (Command command : commands) {
			command.execute();
		}
	}
}