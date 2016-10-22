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

package org.didelphis.soundchange.command;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.didelphis.exceptions.ParseException;
import org.didelphis.phonetic.Lexicon;
import org.didelphis.phonetic.LexiconMap;
import org.didelphis.phonetic.Segment;
import org.didelphis.phonetic.Sequence;
import org.didelphis.phonetic.SequenceFactory;
import org.didelphis.phonetic.features.FeatureArray;
import org.didelphis.phonetic.features.SparseFeatureArray;
import org.didelphis.phonetic.model.FeatureModel;
import org.didelphis.soundchange.Condition;
import org.didelphis.soundchange.exceptions.RuleFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Samantha Fiona Morrigan McCabe
 * Date: 4/7/13
 * Time: 5:40 PM
 */
public class Rule implements Runnable {

	private static final transient Logger LOGGER = LoggerFactory.getLogger(Rule.class);
	
	private static final Pattern BACKREFERENCE = Pattern.compile("\\$([^\\$]*)(\\d+)");
	private static final Pattern NOT_PATTERN   = Pattern.compile("\\s*not\\s*", Pattern.CASE_INSENSITIVE);
	private static final Pattern OR_PATTERN    = Pattern.compile("\\s*or\\s*", Pattern.CASE_INSENSITIVE);
	
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final Pattern TRANSFORM = Pattern.compile("\\s*>\\s*");

	private final String          ruleText;
	private final List<Condition> conditions;
	private final List<Condition> exceptions;

	private final SequenceFactory         factory;
	private final Map<Sequence, Sequence> transform;
	private final LexiconMap              lexicons;

	public Rule(String rule, LexiconMap lexiconsParam, SequenceFactory factoryParam) {
		ruleText = rule;
		lexicons = lexiconsParam;
		factory = factoryParam;
		transform = new LinkedHashMap<Sequence, Sequence>();
		exceptions = new ArrayList<Condition>();
		conditions = new ArrayList<Condition>();
		parseRule();
	}

	public Rule(String rule, SequenceFactory factoryParam) {
		this(rule, null, factoryParam);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		for (Sequence sequence : transform.keySet()) {
			sb.append(sequence);
			sb.append(' ');
		}
		sb.append("> ");
		for (Sequence sequence : transform.values()) {
			sb.append(sequence);
			sb.append(' ');
		}
		sb.append("/ ");
		for (int i = 0; i < conditions.size(); i++) {
			sb.append(conditions.get(i));
			if (i < conditions.size() - 1) {
				sb.append(" OR ");
			}
		}
		for (Condition exception : exceptions) {
			sb.append(" NOT ");
			sb.append(exception);
		}
		return sb.toString();
	}

	@Override
	public void run() {
		for (Lexicon lexicon : lexicons.values()) {
			for (List<Sequence> row : lexicon) {
				for (int i = 0; i < row.size(); i++) {
					Sequence word = apply(row.get(i));
					row.set(i, word);
				}
			}
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) { return true; }
		if (!(obj instanceof Rule)) { return false; }
		
		Rule rhs = (Rule) obj;
		return new EqualsBuilder()
			.append(ruleText, rhs.ruleText)
			.append(conditions, rhs.conditions)
			.append(exceptions, rhs.exceptions)
			.append(factory, rhs.factory)
			.append(transform, rhs.transform)
			.append(lexicons, rhs.lexicons)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder()
				.append(ruleText)
				.append(conditions)
				.append(exceptions)
				.append(factory)
				.append(transform)
				.append(lexicons)
				.toHashCode();
	}

	// Visible for testing
	Sequence apply(Sequence input) {
		Sequence output = new Sequence(input);
		// Step through the word to see if the rule might apply, i.e. if the source pattern can be found
		for (int index = 0; index < output.size(); ) {
			int startIndex = index;
			boolean match = false;
			// Check each source pattern
			for (Map.Entry<Sequence, Sequence> entry : transform.entrySet()) {
				Sequence source = entry.getKey();
				Sequence target = entry.getValue();

				if (index < output.size()) {
					// These map from reference index ($) to:
					Map<Integer, Integer>  indexMap     = new HashMap<Integer, Integer>(); // index of the matching variable element
					Map<Integer, String>   variableMap  = new HashMap<Integer, String>(); // the matched variable / segment string
					Map<Integer, Sequence> sequenceMap  = new HashMap<Integer, Sequence>(); // the actual input that was matched

					// Step through the source pattern
					int testIndex = index;
					int referenceIndex = 1;
					for (int i = 0; i < source.size() && testIndex >= 0; i++) {
						Sequence subSequence = output.getSubsequence(testIndex);
						Segment segment = source.get(i);

						// Source symbol is a variable
						if (factory.hasVariable(segment.getSymbol())) {
							List<Sequence> elements = factory.getVariableValues(segment.getSymbol());
							boolean elementMatches = false;
							for (int k = 0; k < elements.size() && !elementMatches; k++) {
								Sequence element = elements.get(k);
								if (subSequence.startsWith(element)) {
									indexMap.put(referenceIndex, k);
									variableMap.put(referenceIndex, segment.getSymbol());
									sequenceMap.put(referenceIndex, element);
									referenceIndex++;
									testIndex += element.size();
									elementMatches = true;
								}
							}
							// If none of the variable elements match, fail 
							if (!elementMatches) {
								testIndex = -1;
							}
						} else if (segment.isUnderspecified()) {
							// theoretically, this excludes fully specified features, but then
							// why would use use bracket notation for that? just use the symbol

							// Otherwise it's the same as a literal
							if (subSequence.startsWith(segment)) {
								indexMap.put(referenceIndex, -1); // use -1 because there are no elements here
								variableMap.put(referenceIndex, segment.getSymbol());
								sequenceMap.put(referenceIndex, subSequence.getSubsequence(0, 1));
								testIndex++;
							} else {
								testIndex = -1;
							}
						} else if (segment.getSymbol().equals("0")) {
							// do nothing; leaving it written this way for clarity
						} else {
							// It's a literal
							testIndex = subSequence.startsWith(segment) ? testIndex + 1 : -1;
						}
					}

					// This is checked second for a good reason: it may not be
					// possible to know in advance what is the length of the
					// matching initial until it's been evaluated, esp. in the
					// case of a variable whose elements are allowed to have a
					// length greater than 1. This is allowed because it is
					// possible, or even likely, that a language might have a
					// set of multi-segment clusters which still pattern
					// together, or which are part of conditioning environments.
					if (testIndex >= 0 && conditionsMatch(output, startIndex, testIndex)) {
						index = testIndex;
						// Now at this point, if everything worked, we can
						Sequence removed;
						if (startIndex < index) {
							removed = output.remove(startIndex, index);
						} else {
							removed = factory.getNewSequence();
						}
						Sequence replacement = getReplacementSequence(removed, target, variableMap, indexMap, sequenceMap);
						match = true;
						if (!replacement.isEmpty()) {
							output.insert(replacement, startIndex);
						}
						index += replacement.size() - removed.size();
						startIndex = index;
					}
				}
			}
			if (!match) {
				index++;
			}
		}
		return output;
	}

	private void parseRule() {
		String transformString;
		// Check-and-parse for conditions
		if (ruleText.contains("/")) {
			String[] array = ruleText.split("/");
			if (array.length <= 1) {
				throw new RuleFormatException("Condition was empty!");
			} else {
				transformString = array[0].trim();

				String conditionString = array[1].trim();

				Matcher notMatcher = NOT_PATTERN.matcher(conditionString);
				if (notMatcher.lookingAt()) {
					// if there is no regular condition
					// Takes the first one off, and splits on the rest
					String first = notMatcher.replaceFirst("");
					String[] split = NOT_PATTERN.split(first);

					for (String clause : split) {
						if (OR_PATTERN.matcher(clause).find()) {
							throw new RuleFormatException("OR is not allowed " +
									"following a NOT");
						}
						exceptions.add(new Condition(clause.trim(), factory));
					}

				} else if (notMatcher.find()) {
					String[] split = NOT_PATTERN.split(conditionString, 2);
					String conditionClauses = split[0];
					String exceptionClauses = split[1];

					for (String con : OR_PATTERN.split(conditionClauses)) {
						conditions.add(new Condition(con, factory));
					}

					for (String exc : NOT_PATTERN.split(exceptionClauses)) {
						exceptions.add(new Condition(exc, factory));
					}
				} else {
					for (String s : OR_PATTERN.split(conditionString)) {
						conditions.add(new Condition(s, factory));
					}
				}
			}
		} else {
			transformString = ruleText;
			conditions.add(new Condition("_", factory));
		}
		parseTransform(transformString);
	}

	/**
	 * Generates an appropriate sequence by filling in backreferences based on
	 * the provided maps.
	 *
	 * @param source
	 * @param target the "target" pattern; provides a template of indexed
	 *      variables and backreferences to be filled in
	 * @param variableMap Tracks the order of variables in the "source"
	 *      pattern; i.e. the 2nd variable in the source pattern is referenced
	 *      via {@code $2}. Unlike standard regular expressions, all variables
	 *      are tracked, rather than tracking explicit groups
	 * @param indexMap tracks which variable values are matched by the "source"
	 *      pattern; an entry (2 -> 4) would indicate that the source matched
	 *      the 4th value of the 2nd variable. This permits proper mapping
	 *      between source and target symbols when using backreferences and
	 *      indexed variables
	 * @param sequenceMap
	 * @return a Sequence object with variables and references filled in
	 * according to the provided maps
	 */
	private Sequence getReplacementSequence(
			Sequence source,
			Sequence target,
			Map<Integer, String> variableMap,
			Map<Integer, Integer> indexMap,
			Map<Integer, Sequence> sequenceMap) {
		int variableIndex = 1;
		Sequence replacement = factory.getNewSequence();
		// Step through the target pattern
		for (int i = 0; i < target.size(); i++) {
			Segment segment = target.get(i);

			String symbol = segment.getSymbol();
			Matcher matcher = BACKREFERENCE.matcher(symbol);
			if (matcher.matches()) {
				Sequence sequence = getReferencedReplacement(target,
						variableMap, indexMap, sequenceMap, matcher);
				replacement.add(sequence);
			} else if (factory.hasVariable(symbol)) {
				// Allows C > G transformations, where C and G have the same
				// number of elements
				List<Sequence> elements = factory.getVariableValues(symbol);
				Integer anIndex = indexMap.get(variableIndex);
				Sequence sequence = elements.get(anIndex);
				replacement.add(sequence);
				variableIndex++;
			} else if (segment.isUnderspecified()) {
				// Underspecified - overwrite the feature
				Segment alter = source.get(i).alter(segment);
				FeatureArray<Double> features = alter.getFeatures();
				FeatureModel model = factory.getFeatureModel();
				String bestSymbol = model.getBestSymbol(features);
				Segment newSegment = new Segment(
						bestSymbol,
						features,
						model.getSpecification());
				replacement.add(newSegment);
			} else if (!symbol.equals("0")) {
				// Normal segment and not 0
				replacement.add(segment);
			}
			// Else: it's zero, do nothing
		}
		return replacement;
	}

	private Sequence getReferencedReplacement(
			Sequence target,
			Map<Integer, String> variableMap,
			Map<Integer, Integer> indexMap,
			Map<Integer, Sequence> sequenceMap,
			MatchResult matcher
	) {
		String symbol = matcher.group(1);
		String digits = matcher.group(2);

		int reference = Integer.valueOf(digits);
		int integer = indexMap.get(reference);

		Sequence sequence;
		if (integer == -1) {
			// -1 means it was an underspecified feature
			// but we need to know what was matched
			if (symbol.isEmpty()) {
				sequence = factory.getNewSequence();
				// add the captured segment
				Segment captured = sequenceMap.get(reference).get(0);
				sequence.add(captured);
			} else {
				throw new RuntimeException("The use of feature substitution " +
						"in this manner is not supported! " + target);
			}
		} else {
			String variable;
			if (symbol.isEmpty()) {
				variable = variableMap.get(reference);
			} else {
				variable = symbol;
			}
			sequence = factory.getVariableValues(variable).get(integer);
		}
		return sequence;
	}

	private boolean conditionsMatch(Sequence word, int start, int end) {
		Iterator<Condition> cI = conditions.iterator();
		Iterator<Condition> eI = exceptions.iterator();

		boolean conditionMatch = false;
		boolean exceptionMatch = false;

		if (cI.hasNext()) {
			while (cI.hasNext() && !conditionMatch) {
				Condition condition = cI.next();
				conditionMatch = condition.isMatch(word, start, end);
			}
		} else {
			conditionMatch = true;
		}

		if (eI.hasNext()) {
			while (eI.hasNext() && !exceptionMatch) {
				Condition exception = eI.next();
				exceptionMatch = exception.isMatch(word, start, end);
			}
		}
		return conditionMatch && !exceptionMatch;
	}

	private void parseTransform(String transformation) {
		if (transformation.contains(">")) {
			String[] array = TRANSFORM.split(transformation);

			if (array.length <= 1) {
				throw new RuleFormatException("Malformed transformation! " +
						transformation);
			} else if (transformation.contains("$[")) {
				throw new RuleFormatException("Malformed transformation! use " +
						"of indexing with $[] is not permitted! " +
						transformation);
			} else {
				String sourceString = WHITESPACE.matcher(array[0]).replaceAll(" ");
				String targetString = WHITESPACE.matcher(array[1]).replaceAll(" ");

				// Split strings, but not within brackets []
				List<String> sourceList = parseToList(sourceString);
				List<String> targetList = parseToList(targetString);

				// fill in target for cases like "a b c > d"
				if (sourceList.contains("0") && (sourceList.size() != 1 || targetList.size() != 1)) {
					throw new ParseException("A rule may only use \"0\" in the \"source\" if that ");
				}
				
				balanceTransform(sourceList, targetList);

				for (int i = 0; i < sourceList.size(); i++) {
					// Also, we need to correctly tokenize $1, $2 etc or $C1,$N2
					Sequence source = factory.getSequence(sourceList.get(i));
					Sequence target = factory.getSequence(targetList.get(i));
					validateTransform(source, target);
					transform.put(source, target);
				}
			}
		} else {
			throw new RuleFormatException("Missing \">\" sign! in rule " + ruleText);
		}
	}

	/**
	 * Once converted to features, ensure that the rule's transform is well-
	 * formed and has an appropriate structure
	 */
	private void validateTransform(Sequence source, Sequence target) {
		int j = 0;
		for (Segment segment : target) {
			FeatureArray<Double> features = segment.getFeatures();
			boolean underspecified = features.contains(null) || features instanceof SparseFeatureArray;
			if (underspecified && source.size() <= j) {
				throw new RuleFormatException(
						"Unmatched underspecified segment " + segment +
								" in target of rule " + ruleText);
			}
			j++;
		}
	}

	private static List<String> parseToList(String source) {
		List<String> list = new ArrayList<String>();
		int start = 0;
		int end   = 0;

		while (end < source.length()) {
			char c = source.charAt(end);
			if (c == ' ') {
				list.add(source.substring(start, end));
				end++;
				start=end;
			} else if (c == '[') {
				end = source.indexOf(']', end);
			} else {
				end++;
			}
		}
		list.add(source.substring(start, end));
		return list;
	}

	private static void balanceTransform(List<String> source, List<String> target) {
		if (target.size() > source.size()) {
			throw new RuleFormatException("Target size cannot be greater than" +
					" source size! " + source + " < " + target);
		} else if (target.size() < source.size()) {
			if (target.size() == 1) {
				String first = target.get(0);
				while (target.size() < source.size()) {
					target.add(first);
				}
			} else {
				throw new RuleFormatException("Target and source sizes may" +
						" only be uneven if target size is exactly 1! " +
						source + " > " + target);
			}
		}
	}
}
