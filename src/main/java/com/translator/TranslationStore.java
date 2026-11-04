package com.translator;

import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Holds the loaded dictionaries and resolves {@code english -> translation}.
 *
 * <p>Dictionaries are downloaded lazily (only the ones the user enabled) and stored as
 * immutable maps, so the client thread can read them while a background thread reloads them.
 * A reload never mutates a map that is being read; it swaps the whole reference instead.</p>
 *
 * <p>Corrections are applied on top of the downloaded data in this order:</p>
 * <ol>
 *   <li>dictionaries from {@code github.com/iolave/osrs-translations}</li>
 *   <li>corrections bundled with the plugin ({@code /translations/overrides/<lang>.txt})</li>
 *   <li>the user's own corrections ({@code ~/.runelite/plugin-data/translator-enhanced/overrides/<lang>.txt})</li>
 * </ol>
 */
@Singleton
class TranslationStore
{
	private static final Logger log = LoggerFactory.getLogger(TranslationStore.class);

	private static final String BUNDLED_OVERRIDES = "/translations/overrides/%s.txt";
	private static final String BUNDLED_INTERFACE = "/translations/interface/%s.txt";
	private static final String PATTERN_PREFIX = "re:";

	/**
	 * Start of the requirement annotation in a diary task, eg.
	 * {@code Catch a Mackerel at Catherby.<col=ffffff>(<col=800000>16 Fishing...}.
	 */
	private static final String ANNOTATION_PREFIX = "<col=ffffff>(";

	private static final Pattern TAG = Pattern.compile("<[^>]*>");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private final TranslatorAPI api;
	private final TranslatorConfig config;

	private Filepath directory;

	private final Map<TranslationType, Map<String, String>> dictionaries = new ConcurrentHashMap<>();

	/**
	 * Entries prefixed with {@code re:} in the translation files, applied to texts that have no
	 * exact match. Needed for strings with numbers: {@code <col=800000>Combat Level 10<br>}.
	 */
	private final Map<TranslationType, List<PatternTranslation>> patterns = new ConcurrentHashMap<>();

	/**
	 * Translation to original text, used to recover the english text of a widget that was
	 * already translated by a previous session or plugin version.
	 */
	private final Map<TranslationType, Map<String, String>> reverse = new ConcurrentHashMap<>();

	/**
	 * Keys and values compared without tags or spaces, used when the exact text does not match.
	 * The game moves {@code <br>} around when a line is re-rendered, so the same task can arrive
	 * with or without the break.
	 */
	private final Map<TranslationType, Map<String, String>> signatures = new ConcurrentHashMap<>();

	/** Bare task text mapped to the translation that includes its requirements. */
	private final Map<TranslationType, Map<String, String>> annotated = new ConcurrentHashMap<>();

	/**
	 * Language the loaded dictionaries belong to. Bumped on every language change so a slow
	 * download of the previous language can not overwrite the new one.
	 */
	private volatile TranslatorConfig.Language language;

	/**
	 * Incremented whenever the dictionaries are invalidated, so a download that finishes
	 * after a language change or a reload is discarded instead of published.
	 */
	private final AtomicInteger generation = new AtomicInteger();

	@Inject
	TranslationStore(TranslatorAPI api, TranslatorConfig config)
	{
		this.api = api;
		this.config = config;
	}

	void setLanguage(TranslatorConfig.Language language)
	{
		this.language = language;
		generation.incrementAndGet();
		dictionaries.clear();
		patterns.clear();
		reverse.clear();
		signatures.clear();
		annotated.clear();
	}

	void setDirectory(Filepath directory)
	{
		this.directory = directory;
	}

	boolean isLoaded(TranslationType type)
	{
		return dictionaries.containsKey(type);
	}

	/**
	 * Forgets every dictionary so the next {@link #load(TranslationType)} downloads them again.
	 */
	void clear()
	{
		generation.incrementAndGet();
		dictionaries.clear();
		patterns.clear();
		reverse.clear();
		signatures.clear();
		annotated.clear();
	}

	/**
	 * Downloads one dictionary, applies the corrections and publishes it.
	 *
	 * @throws IOException when the download fails
	 */
	void load(TranslationType type) throws IOException
	{
		TranslatorConfig.Language language = this.language;
		int generation = this.generation.get();

		Map<String, String> merged;
		int downloaded;
		if (type.isDownloadable())
		{
			merged = new HashMap<>(api.download(type, language));
			downloaded = merged.size();
		}
		else
		{
			merged = new HashMap<>();
			readOverrides(
				merged,
				TranslationStore.class.getResourceAsStream(String.format(BUNDLED_INTERFACE, language)),
				"bundled interface " + language);
			downloaded = merged.size();

			if (config.useOnlineInterfaceTranslations())
			{
				applyOnlineInterface(merged, language);
			}
		}

		int overrides = applyOverrides(merged, language);
		addTrimmedAliases(merged);
		addAnnotationAliases(merged);
		addColourCloseAliases(merged);
		List<PatternTranslation> compiled = extractPatterns(merged, type);

		if (generation != this.generation.get())
		{
			log.debug("discarding {} translations for {}: the language changed while loading",
				downloaded, type.fileName());
			return;
		}

		dictionaries.put(type, Collections.unmodifiableMap(merged));
		patterns.put(type, compiled);
		reverse.put(type, buildReverse(merged));
		signatures.put(type, buildSignatures(merged));
		annotated.put(type, buildAnnotated(merged));
		log.info("loaded {} translations for {}/{} ({} corrections applied, {} patterns)",
			downloaded, language, type.fileName(), overrides, compiled.size());
	}

	/**
	 * Resolves a text.
	 *
	 * @param type dictionary to use
	 * @param text english text, as it appears in game
	 * @return the translation, or {@code null} when the dictionary has no entry for it
	 */
	String lookup(TranslationType type, String text)
	{
		if (text == null || text.isEmpty())
		{
			return null;
		}

		Map<String, String> dictionary = dictionaries.get(type);
		if (dictionary == null)
		{
			return null;
		}

		String translated = dictionary.get(text);
		if (translated == null)
		{
			// some downloads contain names with leading/trailing spaces
			String trimmed = text.trim();
			if (!trimmed.equals(text))
			{
				translated = dictionary.get(trimmed);
			}
		}

		if (translated != null)
		{
			return translated;
		}

		Map<String, String> index = signatures.get(type);
		translated = index == null ? null : index.get(signature(text));
		if (translated != null)
		{
			return translated;
		}

		return applyPatterns(patterns.get(type), text);
	}

	/**
	 * Applies the {@code re:} entries, in file order. A text can match several patterns, eg. a
	 * single widget with several level requirements.
	 *
	 * @return the translated text, or {@code null} when nothing matched
	 */
	static String applyPatterns(List<PatternTranslation> patterns, String text)
	{
		if (patterns == null || patterns.isEmpty() || text == null)
		{
			return null;
		}

		String result = text;
		boolean changed = false;

		for (PatternTranslation pattern : patterns)
		{
			String applied = pattern.apply(result);
			if (applied != null)
			{
				result = applied;
				changed = true;
			}
		}

		return changed && !result.equals(text) ? result : null;
	}

	/**
	 * Removes the {@code re:} entries from a dictionary and compiles them.
	 */
	private static List<PatternTranslation> extractPatterns(Map<String, String> dictionary, TranslationType type)
	{
		List<PatternTranslation> compiled = new ArrayList<>();

		Iterator<Map.Entry<String, String>> iterator = dictionary.entrySet().iterator();
		while (iterator.hasNext())
		{
			Map.Entry<String, String> entry = iterator.next();
			if (!entry.getKey().startsWith(PATTERN_PREFIX))
			{
				continue;
			}

			iterator.remove();

			try
			{
				compiled.add(new PatternTranslation(
					entry.getKey().substring(PATTERN_PREFIX.length()),
					entry.getValue()));
			}
			catch (PatternSyntaxException e)
			{
				log.warn("invalid {} pattern in {}: {}", PATTERN_PREFIX, type.fileName(), entry.getKey(), e);
			}
		}

		return compiled;
	}

	/**
	 * A regex translation. The replacement can use {@code $1}, {@code $2}... to keep the
	 * dynamic parts (numbers, levels) of the original text.
	 */
	static final class PatternTranslation
	{
		private final Pattern pattern;
		private final String replacement;

		PatternTranslation(String regex, String replacement)
		{
			this.pattern = Pattern.compile(regex);
			this.replacement = replacement;
		}

		String apply(String text)
		{
			Matcher matcher = pattern.matcher(text);
			return matcher.find() ? matcher.replaceAll(replacement) : null;
		}
	}

	private void applyOnlineInterface(Map<String, String> dictionary, TranslatorConfig.Language language)
	{
		Filepath cache = directory == null ? null : directory.join("interface").join(language + ".txt");

		try
		{
			Map<String, String> online = api.download(TranslationType.INTERFACE, language);

			if (online.size() < dictionary.size())
			{
				log.debug("ignoring {} online interface translations: fewer entries than the bundled ones",
					online.size());
				return;
			}

			dictionary.putAll(online);
			writeCache(cache, online);
			log.debug("applied {} online interface translations", online.size());
		}
		catch (IOException e)
		{
			log.debug("failed to download interface translations, using the cached copy", e);

			if (cache != null && cache.isFile())
			{
				try (InputStream in = cache.openInputStream())
				{
					readOverrides(dictionary, in, cache.toString());
				}
				catch (IOException ignored)
				{
				}
			}
		}
	}

	private static void writeCache(Filepath file, Map<String, String> entries)
	{
		if (file == null)
		{
			return;
		}

		StringBuilder builder = new StringBuilder();
		entries.forEach((key, value) -> builder.append(key).append(';').append(value).append('\n'));

		try
		{
			file.getParent().createDirectories();
			file.write(builder.toString());
		}
		catch (IOException e)
		{
			log.debug("failed to cache interface translations", e);
		}
	}

	private int applyOverrides(Map<String, String> dictionary, TranslatorConfig.Language language)
	{
		if (!config.useLocalCorrections())
		{
			return 0;
		}

		int applied = readOverrides(
			dictionary,
			TranslationStore.class.getResourceAsStream(String.format(BUNDLED_OVERRIDES, language)),
			"bundled " + language);

		Filepath userFile = directory == null ? null : directory.join("overrides").join(language + ".txt");
		if (userFile != null && userFile.isFile())
		{
			try (InputStream in = userFile.openInputStream())
			{
				applied += readOverrides(dictionary, in, userFile.toString());
			}
			catch (IOException e)
			{
				log.warn("failed to read user corrections from {}", userFile, e);
			}
		}

		return applied;
	}

	private static int readOverrides(Map<String, String> dictionary, InputStream in, String source)
	{
		if (in == null)
		{
			return 0;
		}

		try (InputStream stream = in;
			 Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
		{
			Map<String, String> corrections = parse(reader);
			dictionary.putAll(corrections);
			log.debug("applied {} corrections from {}", corrections.size(), source);
			return corrections.size();
		}
		catch (IOException e)
		{
			log.warn("failed to read corrections from {}", source, e);
			return 0;
		}
	}

	/**
	 * Adds aliases so a name can be found with or without surrounding spaces: a few upstream
	 * entries are stored with a leading space ({@code " Sneaky undead fowl"}).
	 */
	static void addTrimmedAliases(Map<String, String> dictionary)
	{
		Map<String, String> aliases = new HashMap<>();
		for (Map.Entry<String, String> entry : dictionary.entrySet())
		{
			String key = entry.getKey();
			String trimmed = key.trim();
			if (!trimmed.equals(key) && !dictionary.containsKey(trimmed))
			{
				aliases.put(trimmed, entry.getValue());
			}
		}
		dictionary.putAll(aliases);
	}

	/**
	 * Adds the variant without the requirement annotation.
	 *
	 * <p>A diary task appears twice in game: with its requirements in the detail view
	 * ({@code Catch a Mackerel...<col=ffffff>(<col=800000>16 Fishing...}) and without them in the
	 * list. Both must resolve to the same translation.</p>
	 */
	static void addAnnotationAliases(Map<String, String> dictionary)
	{
		Map<String, String> aliases = new HashMap<>();

		for (Map.Entry<String, String> entry : dictionary.entrySet())
		{
			String key = entry.getKey();
			int annotation = key.indexOf(ANNOTATION_PREFIX);
			if (annotation <= 0)
			{
				continue;
			}

			String bareKey = key.substring(0, annotation);
			if (bareKey.isEmpty())
			{
				continue;
			}

			String value = entry.getValue();
			int valueAnnotation = value.indexOf(ANNOTATION_PREFIX);
			String bareValue = valueAnnotation >= 0 ? value.substring(0, valueAnnotation) : value;

			if (!dictionary.containsKey(bareKey) && !bareValue.trim().isEmpty())
			{
				aliases.put(bareKey, bareValue);
			}
		}

		dictionary.putAll(aliases);
	}

	/**
	 * Finds the english text a translation came from.
	 *
	 * <p>Needed for widgets that already contain a translation, eg. because the plugin was
	 * updated while the interface was open: the stored text is not a key, so a normal lookup
	 * would miss it and the panel would keep an outdated translation.</p>
	 *
	 * @return the original text, or {@code null} when the text is not a known translation
	 */
	String lookupOriginal(TranslationType type, String text)
	{
		if (text == null || text.isEmpty())
		{
			return null;
		}

		Map<String, String> map = reverse.get(type);
		if (map == null)
		{
			return null;
		}

		String original = map.get(text);
		if (original == null)
		{
			original = map.get(withoutBreaks(text));
		}
		if (original == null)
		{
			original = map.get(signature(text));
		}
		return original;
	}

	/** @return the translation of a bare task that includes its requirements, or {@code null} */
	String lookupAnnotated(TranslationType type, String text)
	{
		if (text == null || text.isEmpty())
		{
			return null;
		}

		Map<String, String> map = annotated.get(type);
		return map == null ? null : map.get(text);
	}

	/**
	 * @return the text without line breaks or tags, used as a last resort when comparing
	 */
	static String signature(String text)
	{
		return WHITESPACE.matcher(TAG.matcher(text).replaceAll("")).replaceAll("");
	}

	/**
	 * @return the text with the line breaks this plugin inserts replaced by spaces
	 */
	static String withoutBreaks(String text)
	{
		return text.replace("<br>", " ").replaceAll("\\s{2,}", " ").trim();
	}

	static Map<String, String> buildReverse(Map<String, String> dictionary)
	{
		Map<String, String> reversed = new HashMap<>();

		for (Map.Entry<String, String> entry : dictionary.entrySet())
		{
			String key = entry.getKey();
			if (key.startsWith(PATTERN_PREFIX))
			{
				continue;
			}

			String value = entry.getValue();
			if (value != null && !value.isEmpty() && !value.equals(key))
			{
				reversed.putIfAbsent(value, key);
			}
		}

		for (Map.Entry<String, String> entry : dictionary.entrySet())
		{
			String key = entry.getKey();
			String value = entry.getValue();
			if (!key.startsWith(PATTERN_PREFIX) && value != null && !value.isEmpty())
			{
				reversed.putIfAbsent(signature(value), key);
			}
		}

		return reversed;
	}

	private static Map<String, String> buildSignatures(Map<String, String> dictionary)
	{
		Map<String, String> index = new HashMap<>();

		for (Map.Entry<String, String> entry : dictionary.entrySet())
		{
			if (!entry.getKey().startsWith(PATTERN_PREFIX) && entry.getValue() != null)
			{
				index.putIfAbsent(signature(entry.getKey()), entry.getValue());
			}
		}

		return index;
	}

	static Map<String, String> buildAnnotated(Map<String, String> dictionary)
	{
		Map<String, String> index = new HashMap<>();

		for (Map.Entry<String, String> entry : dictionary.entrySet())
		{
			String key = entry.getKey();
			int annotation = key.indexOf(ANNOTATION_PREFIX);

			if (annotation > 0 && entry.getValue() != null)
			{
				index.putIfAbsent(key.substring(0, annotation), entry.getValue());
			}
		}

		return index;
	}

	/**
	 * Adds the variant with a trailing {@code </col>}.
	 *
	 * <p>Some interface texts end with {@code </col>} and others do not, depending on the
	 * widget. The same translation has to answer for both.</p>
	 */
	static void addColourCloseAliases(Map<String, String> dictionary)
	{
		Map<String, String> aliases = new HashMap<>();

		for (Map.Entry<String, String> entry : dictionary.entrySet())
		{
			String key = entry.getKey();
			String value = entry.getValue();

			if (key.startsWith(PATTERN_PREFIX)
				|| key.contains("</col>") || value == null || !value.contains("<col="))
			{
				continue;
			}

			aliases.put(key + "</col>", value + "</col>");
		}

		dictionary.putAll(aliases);
	}

	/**
	 * Parses a translation file.
	 *
	 * <p>The format is {@code original;translation}, one per line. Everything after the first
	 * {@code ;} is the translation, because translations themselves may contain semicolons
	 * (614 dialogue lines in the Spanish file do). Lines starting with {@code #} and empty lines
	 * are ignored. Keys are not trimmed on purpose: a few names in the upstream files have a
	 * meaningful leading space.</p>
	 *
	 * @param reader the file contents
	 * @return a mutable map of {@code english -> translation}
	 */
	static Map<String, String> parse(Reader reader) throws IOException
	{
		Map<String, String> dictionary = new HashMap<>();
		BufferedReader bufferedReader = new BufferedReader(reader);
		String line;
		while ((line = bufferedReader.readLine()) != null)
		{
			if (line.isEmpty() || line.charAt(0) == '#')
			{
				continue;
			}

			int separator = line.indexOf(';');
			if (separator <= 0 || separator >= line.length() - 1)
			{
				continue;
			}

			dictionary.put(line.substring(0, separator), line.substring(separator + 1));
		}

		return dictionary;
	}
}
