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

package org.didelphis.soundchange;

import org.didelphis.enums.FormatterMode;
import org.didelphis.exceptions.ParseException;
import org.didelphis.io.FileHandler;
import org.didelphis.phonetic.model.FeatureModel;
import org.didelphis.phonetic.model.FeatureModelLoader;
import org.didelphis.phonetic.Lexicon;
import org.didelphis.phonetic.LexiconMap;
import org.didelphis.phonetic.SequenceFactory;
import org.didelphis.phonetic.VariableStore;
import org.didelphis.phonetic.model.StandardFeatureModel;
import org.didelphis.soundchange.command.LexiconCloseCommand;
import org.didelphis.soundchange.command.LexiconOpenCommand;
import org.didelphis.soundchange.command.LexiconWriteCommand;
import org.didelphis.soundchange.command.Rule;
import org.didelphis.soundchange.command.ScriptExecuteCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Samantha Fiona Morrigan McCabe
 * Date: 4/18/13
 * Time: 11:46 PM
 */
public class StandardScript implements SoundChangeScript {

	private static final transient Logger LOGGER = LoggerFactory.getLogger(StandardScript.class);

	private static final String COMMENT_STRING = "%";
	private static final String FILEHANDLE     = "(\\w+)";
	private static final String FILEPATH       = "[\"\']([^\"\']+)[\"\']";

	public static final int INS = Pattern.CASE_INSENSITIVE;

	private static final String MODE    = "MODE";
	private static final String EXECUTE = "EXECUTE";
	private static final String IMPORT  = "IMPORT";
	private static final String OPEN    = "OPEN";
	private static final String WRITE   = "WRITE";
	private static final String CLOSE   = "CLOSE";
	private static final String BREAK   = "BREAK";
	private static final String LOAD    = "LOAD";
	private static final String RESERVE = "RESERVE";

	private static final Pattern COMMENT_PATTERN    = Pattern.compile(COMMENT_STRING + ".*");
	private static final Pattern NEWLINE_PATTERN    = Pattern.compile("\\s*(\\r?\\n|\\r)\\s*");
	private static final Pattern RESERVE_PATTERN    = Pattern.compile(RESERVE + ":? *");
	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

	private static final Pattern CLOSE_PATTERN   = Pattern.compile(CLOSE + "\\s+" + FILEHANDLE + "\\s+(as\\s)?" + FILEPATH, INS);
	private static final Pattern WRITE_PATTERN   = Pattern.compile(WRITE + "\\s+" + FILEHANDLE + "\\s+(as\\s)?" + FILEPATH, INS);
	private static final Pattern OPEN_PATTERN    = Pattern.compile(OPEN + "\\s+" + FILEPATH + "\\s+(as\\s)?" + FILEHANDLE, INS);
	private static final Pattern MODE_PATTERN    = Pattern.compile(MODE + ":? *", INS);
	private static final Pattern EXECUTE_PATTERN = Pattern.compile(EXECUTE + "\\s+", INS);
	private static final Pattern IMPORT_PATTERN  = Pattern.compile(IMPORT + "\\s+", INS);
	private static final Pattern LOAD_PATTERN    = Pattern.compile(LOAD + "\\s+", INS);
	private static final Pattern QUOTES_PATTERN  = Pattern.compile("\"|\'");

	private static final Pattern VAR_PATTERN = Pattern.compile("[^=]+=", INS);
	private static final Pattern RULE_PATTERN = Pattern.compile("(\\[[^\\]]+\\]|[^>]\\s*)*>", INS);

	private final String         scriptId;
	private final FileHandler    fileHandler;
	private final Queue<Runnable> commands;
	private final LexiconMap     lexicons;
	private final VariableStore  variables;
	private final Set<String>    reserved;

	// Need these as fields or IMPORT doesn't work correctly
	private FormatterMode formatterMode;
	private FeatureModel  featureModel;

	public StandardScript(String id, CharSequence script, FileHandler handler) {
		this(id, handler, FormatterMode.NONE, StandardFeatureModel.EMPTY_MODEL);

		Collection<String> lines = new ArrayList<String>();
		Collections.addAll(lines, NEWLINE_PATTERN.split(script));

		boolean fail = parse(id, lines);
		if (fail) {
			throw new ParseException("There were problems compiling the script "
			                         + id + "; please see logs for details");
		}
	}

	private StandardScript(String id, FileHandler handler, FormatterMode mode, FeatureModel model) {
		scriptId    = id;
		fileHandler = handler;

		lexicons  = new LexiconMap();
		commands  = new ArrayDeque<Runnable>();
		variables = new VariableStore();
		reserved  = new LinkedHashSet<String>();

		formatterMode = mode;
		featureModel  = model;
	}

	// Visible for testing
	StandardScript(CharSequence script, FileHandler fileHandlerParam) {
		this("DEFAULT", script, fileHandlerParam);
	}

	@Override
	public boolean hasLexicon(String handle) {
		return lexicons.hasHandle(handle);
	}

	@Override
	public Lexicon getLexicon(String handle) {
		return lexicons.get(handle);
	}

	@Override
	public Queue<Runnable> getCommands() {
		return commands;
	}

	@Override
	public void process() {
		for (Runnable command : commands) {
			command.run();
		}
	}

	public Collection<String> getReservedSymbols() {
		return reserved;
	}

	private boolean parse(String id, Iterable<String> strings) {

		// For error reporting
		int lineNumber = 1;
		boolean fail = false;

		StringBuilder buffer = new StringBuilder();
		Type commandType = null;
		String currentLine;
		for (String string : strings) {
			currentLine = COMMENT_PATTERN.matcher(string).replaceAll("").trim();
			// TODO: this section contains a lot of repeated code
			if (currentLine.isEmpty()) {
				// end previous command
				buildCommand(commandType, buffer.toString());
				buffer = new StringBuilder();
				commandType = null;
			} else if (currentLine.startsWith(LOAD)) {
				buildCommand(commandType, buffer.toString());
				buffer = new StringBuilder(currentLine);
				commandType = Type.LOAD;
			} else if (currentLine.startsWith(EXECUTE)) {
				buildCommand(commandType, buffer.toString());
				buffer = new StringBuilder(currentLine);
				commandType = Type.EXECUTE;
			} else if (currentLine.startsWith(IMPORT)) {
				buildCommand(commandType, buffer.toString());
				buffer = new StringBuilder(currentLine);
				commandType = Type.IMPORT;
			} else if (currentLine.startsWith(OPEN)) {
				buildCommand(commandType, buffer.toString());
				buffer = new StringBuilder(currentLine);
				commandType = Type.OPEN;
			} else if (currentLine.startsWith(WRITE)) {
				buildCommand(commandType, buffer.toString());
				buffer = new StringBuilder(currentLine);
				commandType = Type.WRITE;
			} else if (currentLine.startsWith(CLOSE)) {
				buildCommand(commandType, buffer.toString());
				buffer = new StringBuilder(currentLine);
				commandType = Type.CLOSE;
			} else if (currentLine.startsWith(MODE)) {
				buildCommand(commandType, buffer.toString());
				buffer = new StringBuilder(currentLine);
				commandType = Type.MODE;
			} else if (currentLine.startsWith(RESERVE)) {
				buildCommand(commandType, buffer.toString());
				buffer = new StringBuilder(currentLine);
				commandType = Type.RESERVE;
			} else if (currentLine.startsWith(BREAK)) {
				buildCommand(commandType, buffer.toString());
				break; // terminate processing
			} else if (VAR_PATTERN.matcher(currentLine).find()) {
				buildCommand(commandType, buffer.toString());
				buffer = new StringBuilder(currentLine);
				commandType = Type.VARIABLE;
			} else if (RULE_PATTERN.matcher(currentLine).find()) {
				if (commandType == null) {
					commandType = Type.RULE;
				} else {
					buildCommand(commandType, buffer.toString());
					buffer = new StringBuilder(currentLine);
					commandType = Type.RULE;
				}
			} else {
				buffer.append(currentLine);
			}
			// Always add a standard newline
			buffer.append('\n');
		}

		// Compile remaining contents of buffer
		buildCommand(commandType, buffer.toString());

		return fail;
	}

	private void buildCommand(Type commandType, String string) {

		string = string.trim();

		if (string.isEmpty() || commandType == null) {
			return;
		}

		SequenceFactory factory = new SequenceFactory(
				featureModel,
				new VariableStore(variables),
				new HashSet<String>(reserved),
				formatterMode
		);

		switch (commandType) {
			case MODE:
				formatterMode = setNormalizer(string);
				break;
			case EXECUTE:
				executeScript(string);
				break;
			case IMPORT:
				importScript(string);
				break;
			case OPEN:
				openLexicon(string, factory);
				break;
			case WRITE:
				writeLexicon(string, formatterMode);
				break;
			case CLOSE:
				closeLexicon(string, formatterMode);
				break;
			case LOAD:
				featureModel = loadModel(string, fileHandler, formatterMode);
				break;
			case RESERVE:
				String reserve = RESERVE_PATTERN.matcher(string).replaceAll("");
				Collections.addAll(reserved, WHITESPACE_PATTERN.split(reserve));
				break;
			case VARIABLE:
				variables.add(string);
				break;
			case RULE:
				commands.add(new Rule(string, lexicons, factory));
				break;
			default:
				//
				LOGGER.debug("");
		}
	}

	private static FeatureModel loadModel(CharSequence command, FileHandler handler, FormatterMode mode) {
		String input = LOAD_PATTERN.matcher(command).replaceAll("");
		String path  = QUOTES_PATTERN.matcher(input).replaceAll("");

		FeatureModelLoader loader = new FeatureModelLoader(path, handler.readLines(path), mode);
		return new StandardFeatureModel(loader);
	}

	/**
	 * OPEN "some_lexicon.txt" (as) FILEHANDLE to load the contents of that file
	 * into a lexicon stored against the file-handle;
	 *
	 * @param command the whole command staring from OPEN, specifying the path and file-handle
	 */
	private void openLexicon(String command, SequenceFactory factory) {
		Matcher matcher = OPEN_PATTERN.matcher(command);
		if (matcher.lookingAt()) {
			String path   = matcher.group(1);
			String handle = matcher.group(3);
			commands.add(new LexiconOpenCommand(lexicons, path, handle, fileHandler, factory));
		} else {
			throw new ParseException("Command seems to be ill-formatted: " + command);
		}
	}

	/**
	 * CLOSE FILEHANDLE (as) "some_output2.txt" to close the file-handle and
	 * save the lexicon to the specified file.
	 *
	 * @param command the whole command starting from CLOSE, specifying the file-handle and path
	 * @throws  ParseException
	 */
	private void closeLexicon(String command, FormatterMode mode) {
		Matcher matcher = CLOSE_PATTERN.matcher(command);
		if (matcher.lookingAt()) {
			String handle = matcher.group(1);
			String path   = matcher.group(3);
			commands.add(new LexiconCloseCommand(lexicons, path, handle, fileHandler, mode));
		} else {
			throw new ParseException("Command seems to be ill-formatted: " + command);
		}
	}

	/**
	 * WRITE FILEHANDLE (as) "some_output1.txt" to save the current state of the
	 * lexicon to the specified file,but leave the handle open
	 *
	 * @param command the whole command starting from WRITE, specifying the file-handle and path
	 * @throws ParseException
	 */
	private void writeLexicon(String command, FormatterMode mode) {
		Matcher matcher = WRITE_PATTERN.matcher(command);
		if (matcher.lookingAt()) {
			String handle = matcher.group(1);
			String path   = matcher.group(3);
			commands.add(new LexiconWriteCommand(lexicons, path, handle, fileHandler, mode));
		} else {
			throw new ParseException("Command seems to be ill-formatted: " + command);
		}
	}

	/**
	 * IMPORT other rule files, which basically inserts those commands into your
	 * current rule file; Unlike other commands, this runs immediately and
	 * inserts the new commands into the current sound change applier
	 *
	 * @param command the whole command starting with 'IMPORT'
	 */
	private void importScript(CharSequence command) {
		String input = IMPORT_PATTERN.matcher(command).replaceAll("");
		String path  = QUOTES_PATTERN.matcher(input).replaceAll("");
		String data = fileHandler.read(path);
		Collection<String> lines = new ArrayList<String>();
		Collections.addAll(lines, NEWLINE_PATTERN.split(data));
		parse(path, lines);
	}

	/**
	 * EXECUTE other rule files, which just does what that rule file does in a
	 * separate process;
	 *
	 * @param command the whole command starting with 'EXECUTE'
	 */
	private void executeScript(CharSequence command) {
		String input = EXECUTE_PATTERN.matcher(command).replaceAll("");
		String path  = QUOTES_PATTERN.matcher(input).replaceAll("");
		commands.add(new ScriptExecuteCommand(path, fileHandler));
	}

	private static FormatterMode setNormalizer(CharSequence command) {
		String mode = MODE_PATTERN.matcher(command).replaceAll("");
		try {
			return FormatterMode.valueOf(mode.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new ParseException("Unsupported mode: "+mode, e);
		}
	}

	@Override
	public String toString() {
		return "StandardScript{" + scriptId + '}';
	}

	private enum Type {
		MODE,
		EXECUTE,
		IMPORT,
		OPEN,
		WRITE,
		CLOSE,
//		BREAK,
		LOAD,
		RESERVE,
		VARIABLE,
		RULE
		}
}
