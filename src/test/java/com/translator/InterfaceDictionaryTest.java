package com.translator;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Validates the reviewed interface dictionary. These are the checks that have to pass before
 * any contribution (or automated PR) is merged.
 */
public class InterfaceDictionaryTest
{
	private static final String FILE = "/translations/interface/spanish.txt";

	private static final Pattern TAG = Pattern.compile("<[^>]*>");
	private static final Pattern NUMBER = Pattern.compile("\\d+");
	private static final Pattern PLACEHOLDER = Pattern.compile("\\$(\\d+)");

	@Test
	public void keysAreUnique() throws Exception
	{
		Set<String> keys = new HashSet<>();
		for (String[] entry : readRawEntries())
		{
			assertTrue("duplicate key: " + entry[0], keys.add(entry[0]));
		}
	}

	@Test
	public void tagsArePreserved() throws Exception
	{
		for (Map.Entry<String, String> entry : dictionary().entrySet())
		{
			if (entry.getKey().startsWith("re:"))
			{
				continue;
			}

			assertEquals("tags differ for: " + entry.getKey(),
				tags(entry.getKey()), tags(entry.getValue()));
		}
	}

	@Test
	public void numbersArePreserved() throws Exception
	{
		for (Map.Entry<String, String> entry : dictionary().entrySet())
		{
			if (entry.getKey().startsWith("re:"))
			{
				continue;
			}

			assertEquals("numbers differ for: " + entry.getKey(),
				numbers(entry.getKey()), numbers(entry.getValue()));
		}
	}

	@Test
	public void patternsUseExistingGroups() throws Exception
	{
		for (Map.Entry<String, String> entry : dictionary().entrySet())
		{
			if (!entry.getKey().startsWith("re:"))
			{
				continue;
			}

			Pattern pattern = Pattern.compile(entry.getKey().substring("re:".length()));
			int groups = pattern.matcher("").groupCount();

			Matcher placeholder = PLACEHOLDER.matcher(entry.getValue());
			while (placeholder.find())
			{
				int group = Integer.parseInt(placeholder.group(1));
				assertTrue("pattern " + entry.getKey() + " uses $" + group + " but only has " + groups + " groups",
					group >= 1 && group <= groups);
			}
		}
	}

	@Test
	public void questDescriptionsFitTheOriginalLine() throws Exception
	{
		// the quest overview panel is sized with the english text: a longer translation
		// overflows the panel (and gets clipped on both sides)
		for (Map.Entry<String, String> entry : dictionary().entrySet())
		{
			String key = entry.getKey();
			if (!key.startsWith("I can start this quest") && !key.startsWith("I started this quest"))
			{
				continue;
			}

			String source = TAG.matcher(key).replaceAll("");
			String translation = TAG.matcher(entry.getValue()).replaceAll("");

			assertTrue("translation longer than the original (" + translation.length()
					+ " > " + source.length() + "): " + key,
				translation.length() <= source.length());
		}
	}

	@Test
	public void valuesAreNotEmpty() throws Exception
	{
		for (Map.Entry<String, String> entry : dictionary().entrySet())
		{
			assertTrue("empty value for: " + entry.getKey(), !entry.getValue().trim().isEmpty());
		}
	}

	private static Map<String, String> dictionary() throws Exception
	{
		InputStream in = InterfaceDictionaryTest.class.getResourceAsStream(FILE);
		assertNotNull("missing " + FILE, in);

		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			return TranslationStore.parse(reader);
		}
	}

	/**
	 * Reads the file without parsing, to detect duplicated keys (the parser keeps the last one).
	 */
	private static List<String[]> readRawEntries() throws Exception
	{
		List<String[]> entries = new ArrayList<>();

		InputStream in = InterfaceDictionaryTest.class.getResourceAsStream(FILE);
		assertNotNull("missing " + FILE, in);

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.charAt(0) == '#')
				{
					continue;
				}

				int separator = line.indexOf(';');
				if (separator <= 0)
				{
					continue;
				}

				entries.add(new String[]{line.substring(0, separator), line.substring(separator + 1)});
			}
		}

		return entries;
	}

	private static List<String> tags(String text)
	{
		List<String> tags = new ArrayList<>();
		Matcher matcher = TAG.matcher(text);
		while (matcher.find())
		{
			tags.add(matcher.group());
		}
		return tags;
	}

	private static List<String> numbers(String text)
	{
		List<String> numbers = new ArrayList<>();
		Matcher matcher = NUMBER.matcher(TAG.matcher(text).replaceAll(""));
		while (matcher.find())
		{
			numbers.add(matcher.group());
		}
		return numbers;
	}
}
