/******************************************************************************
 * Copyright (c) 2016 Samantha Fiona McCabe                                   *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.      *
 ******************************************************************************/

package org.didelphis.soundchange;

import org.didelphis.enums.FormatterMode;
import org.didelphis.io.ClassPathFileHandler;
import org.didelphis.io.FileHandler;
import org.didelphis.io.MockFileHandler;
import org.didelphis.io.NullFileHandler;
import org.didelphis.phonetic.Lexicon;
import org.didelphis.phonetic.SequenceFactory;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Created with IntelliJ IDEA.
 * User: Samantha Fiona Morrigan McCabe
 * Date: 9/19/13
 * Time: 10:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class StandardScriptMultilineTest {

	private static final transient Logger LOGGER = LoggerFactory.getLogger(StandardScriptMultilineTest.class);

	private static final ClassPathFileHandler CLASSPATH_HANDLER   = ClassPathFileHandler.getDefaultInstance();
	private static final SequenceFactory FACTORY_INTELLIGENT = new SequenceFactory(FormatterMode.INTELLIGENT);


	@Test
	public void testExecute() throws Exception {
		Map<String, String> fileSystem = new HashMap<String, String>();
		FileHandler fileHandler = new MockFileHandler(fileSystem);

		String rules = getStringFromClassPath("testRuleLarge01.txt");
		String words = getStringFromClassPath("testRuleLarge01.lex");
		String outpt = getStringFromClassPath("testRuleLargeOut01.lex");

		// Append output clause
		rules = rules + "\n" +
				"MODE COMPOSITION\n" +
				"CLOSE LEXICON AS \'output.lex\'";

		fileSystem.put("testRuleLarge01.lex", words);
		fileSystem.put("testRuleLarge01.txt", rules);

		String executeRule = "EXECUTE 'testRuleLarge01.txt'";
		SoundChangeScript script = new StandardScript("testExecute", executeRule, fileHandler);
		script.process();

		String received = fileSystem.get("output.lex");

		assertEquals(outpt.replaceAll("\\r\\n|\\n|\\r","\n"), received);
	}

	@Test
	public void testRuleLarge01() throws Exception {
		String[] output = getStringFromClassPath("testRuleLargeOut01.lex").split("\n");

		String script = "IMPORT 'testRuleLarge01.txt'";

		SoundChangeScript sca = new StandardScript(script, CLASSPATH_HANDLER);
		sca.process();

		Lexicon received = sca.getLexicon("LEXICON");
		Lexicon expected = FACTORY_INTELLIGENT.getLexiconFromSingleColumn(output);
		assertEquals(expected, received);
	}


	@Test
	public void testRules01() {
		String lexicon = "" +
				"apat\n" +
				"takan\n" +
				"kepak\n" +
				"pik\n" +
				"ket";

		String script = "" +
				"OPEN 'lexicon'\n" +
				"    as LEXICON\n" +
				"C = p t k\n" +
				"G = b d g\n" +
				"V = a e i o u\n" +
				"C > G" +
				"    / V_V\n" +
				"CLOSE LEXICON as 'newlex'";

		Map<String, String> fileSystem = new HashMap<String, String>();
		fileSystem.put("lexicon", lexicon);

		MockFileHandler handler = new MockFileHandler(fileSystem);
		SoundChangeScript standardScript = new StandardScript(script, handler);
		standardScript.process();

		String received = fileSystem.get("newlex");
		String expected = "" +
				"abat\n" +
				"tagan\n" +
				"kebak\n" +
				"pik\n" +
				"ket";

		assertEquals(expected, received);
	}

	@Test
	public void testRules02() {
		String lexicon = "" +
				"apat\n" +
				"takan\n" +
				"kepak\n" +
				"pik\n" +
				"ket";

		String script = "" +
				"OPEN 'lexicon' as LEXICON\n" +
				"C = p t k\n" +
				"G = b d g\n" +
				"V = a e i o u\n" +
				"C > G\n" +
				"/ V_V\n" +
				"k t  >\n" +
				"x th /\n" +
				"#_\n" +
				"CLOSE\n" +
				"LEXICON as 'newlex'";

		Map<String, String> fileSystem = new HashMap<String, String>();
		fileSystem.put("lexicon", lexicon);

		MockFileHandler handler = new MockFileHandler(fileSystem);
		SoundChangeScript standardScript = new StandardScript(script, handler);
		standardScript.process();

		String received = fileSystem.get("newlex");
		String expected = "" +
				"abat\n" +
				"thagan\n" +
				"xebak\n" +
				"pik\n" +
				"xet";

		assertEquals(expected, received);
	}

	@Test
	public void testImportVariables() throws Exception {
		String script1 = "" +
				"C = p t k\n" +
				"V = a i u\n";

		String script2 = "" +
				"OPEN \"lexicon\" as LEXICON\n" +
				"IMPORT \"script1\"\n" +
				"a i u > 0 / VC_CV\n" +
				"CLOSE LEXICON as \"newlex\"";

		String lexicon = "" +
				"apaka\n" +
				"paku\n" +
				"atuku\n";

		Map<String, String> fileSystem = new HashMap<String, String>();
		fileSystem.put("script1", script1);
		fileSystem.put("lexicon", lexicon);

		new StandardScript(script2, new MockFileHandler(fileSystem)).process();

		String received = fileSystem.get("newlex");
		String expected = "" +
				"apka\n" +
				"paku\n" +
				"atku";
		assertEquals(expected, received);
	}

	@Test
	public void testImportReserve() throws Exception {
		String script1 = "RESERVE ph th kh\n";

		String script2 = "" +
				"IMPORT \"script1\"\n" +
				"OPEN \"lexicon\" as LEXICON\n" +
				"p t k ph th kh >\n" +
				"b d g f  θ  x\n" +
				"CLOSE LEXICON as \"newlex\"";

		String lexicon = "" +
				"apakha\n" +
				"phaku\n" +
				"athuku\n";

		Map<String, String> fileSystem = new HashMap<String, String>();
		fileSystem.put("script1", script1);
		fileSystem.put("lexicon", lexicon);

		new StandardScript(script2, new MockFileHandler(fileSystem)).process();

		String received = fileSystem.get("newlex");
		String expected = "" +
				"abaxa\n" +
				"fagu\n" +
				"aθugu";
		assertEquals(expected, received);
	}

	@Test(timeout = 2000)
	public void testLoop() {
		String commands = "" +
				"P = pw p t k" + '\n' +
				"B = bw b d g" + '\n' +
				"V = a o" + '\n' +
				'\n' +
				"P > B /\nV_V" + '\n' +
				"P = p t k" + '\n' +
				"B = b d g" + '\n' +
				"B > 0 / #_c";

		new StandardScript(commands, NullFileHandler.INSTANCE);
	}

	private static String getStringFromClassPath(String name) throws IOException {
		InputStream rulesStream = StandardScriptTest.class
				.getClassLoader()
				.getResourceAsStream(name);

		Reader streamReader = new InputStreamReader(rulesStream, "UTF-8");
		Reader bufferedReader = new BufferedReader(streamReader);

		StringBuilder sb = new StringBuilder();

		int c = bufferedReader.read();
		while (c >= 0) {
			sb.append((char) c);
			c = bufferedReader.read();
		}
		return sb.toString();
	}
}
