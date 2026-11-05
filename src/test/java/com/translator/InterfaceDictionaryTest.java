package com.translator;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Validates the reviewed interface dictionaries. These are the checks that have to pass before
 * any contribution (or automated PR) is merged.
 */
public class InterfaceDictionaryTest
{
	private static final Path DIRECTORY = Paths.get("src/main/resources/translations/interface");

	private static final Pattern TAG = Pattern.compile("<[^>]*>");
	private static final Pattern NUMBER = Pattern.compile("\\d+");
	private static final Pattern PLACEHOLDER = Pattern.compile("\\$(\\d+)");

	@Test
	public void keysAreUnique() throws Exception
	{
		for (String language : languages())
		{
			Set<String> keys = new HashSet<>();
			for (String[] entry : readRawEntries(language))
			{
				assertTrue(language + ": duplicate key: " + entry[0], keys.add(entry[0]));
			}
		}
	}

	@Test
	public void tagsArePreserved() throws Exception
	{
		for (String language : languages())
		{
			for (Map.Entry<String, String> entry : dictionary(language).entrySet())
			{
				if (entry.getKey().startsWith("re:"))
				{
					continue;
				}

				assertEquals(language + ": tags differ for: " + entry.getKey(),
					tags(entry.getKey()), tags(entry.getValue()));
			}
		}
	}

	@Test
	public void numbersArePreserved() throws Exception
	{
		for (String language : languages())
		{
			for (Map.Entry<String, String> entry : dictionary(language).entrySet())
			{
				if (entry.getKey().startsWith("re:"))
				{
					continue;
				}

				assertEquals(language + ": numbers differ for: " + entry.getKey(),
					numbers(entry.getKey()), numbers(entry.getValue()));
			}
		}
	}

	@Test
	public void patternsUseExistingGroups() throws Exception
	{
		for (String language : languages())
		{
			for (Map.Entry<String, String> entry : dictionary(language).entrySet())
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
					assertTrue(language + ": pattern " + entry.getKey() + " uses $" + group
							+ " but only has " + groups + " groups",
						group >= 1 && group <= groups);
				}
			}
		}
	}

	@Test
	public void questDescriptionsFitTheOriginalLine() throws Exception
	{
		// the quest overview panel is sized with the english text: a longer translation
		// overflows the panel (and gets clipped on both sides)
		for (Map.Entry<String, String> entry : dictionary("spanish").entrySet())
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
	public void keysAreNotTranslations() throws Exception
	{
		for (String language : languages())
		{
			Map<String, String> dictionary = dictionary(language);
			Set<String> values = new HashSet<>();

			for (String value : dictionary.values())
			{
				values.add(TranslationStore.signature(value));
			}

			for (Map.Entry<String, String> entry : dictionary.entrySet())
			{
				String key = entry.getKey();
				if (key.startsWith("re:"))
				{
					continue;
				}

				String signature = TranslationStore.signature(key);
				boolean identity = signature.equals(TranslationStore.signature(entry.getValue()));

				assertFalse(language + ": key is also a translation: " + key,
					!identity && signature.length() > 20 && values.contains(signature));
			}
		}
	}

	@Test
	public void valuesAreNotEmpty() throws Exception
	{
		for (String language : languages())
		{
			for (Map.Entry<String, String> entry : dictionary(language).entrySet())
			{
				assertTrue(language + ": empty value for: " + entry.getKey(),
					!entry.getValue().trim().isEmpty());
			}
		}
	}

	@Test
	public void properNounsStayEnglish() throws Exception
	{
		Set<String> names = new HashSet<>(Arrays.asList(
			"Attack", "Strength", "Defence", "Ranged", "Prayer", "Magic", "Runecraft",
			"Construction", "Hitpoints", "Agility", "Herblore", "Thieving", "Crafting",
			"Fletching", "Slayer", "Hunter", "Mining", "Smithing", "Fishing", "Cooking",
			"Firemaking", "Woodcutting", "Farming", "Sailing",
			"Grand Exchange", "Theatre of Blood", "Chambers of Xeric", "Tombs of Amascut",
			"Barrows", "Varrock", "Falador", "Lumbridge", "Draynor", "Karamja", "Brimhaven",
			"Kharazi Jungle", "Waterbirth Island", "God Wars Dungeon", "Dagannoth Kings",
			"Neitiznot", "Rellekka", "Wilderness", "Ardougne", "Canifis", "Morytania",
			"Tirannwn", "Kandarin", "Asgarnia", "Misthalin", "Prifddinas", "Great Kourend",
			"Gielinor", "Blue Moon Inn", "Varrock Museum", "Shilo Village", "Tai Bwo Wannai",
			"Lunar Diplomacy", "Dragon Slayer", "Monkey Madness", "The Grand Tree",
			"Jungle Potion", "Nylocas", "Olm", "Warden", "Verzik Vitur", "Zulrah",
			"Vorkath", "Cerberus", "Skotizo", "Bryophyta", "Scurrius", "Nex", "Bandos",
			"Armadyl", "Saradomin", "Zamorak", "Zaros", "Guthix", "Seren", "Tumeken",
			"Elidinis", "Amascut", "Icthlarin", "Twisted bow", "Scythe of vitur",
			"Tumeken's shadow", "Dragon claws", "Elysian", "Inferno", "Fight Caves",
			"Wintertodt", "Tempoross", "Zalcano", "Hespori", "Gauntlet", "Nightmare"));

		for (String language : languages())
		{
			for (Map.Entry<String, String> entry : dictionary(language).entrySet())
			{
				String key = entry.getKey();
				if (key.startsWith("re:"))
				{
					continue;
				}

				String plainKey = TAG.matcher(key).replaceAll("").trim();
				String plainValue = TAG.matcher(entry.getValue()).replaceAll("").trim();
				if (!plainKey.matches(".*[.!?]$") && !names.contains(plainKey))
				{
					continue;
				}

				for (String name : names)
				{
					Pattern pattern = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
					if (!pattern.matcher(plainKey).find())
					{
						continue;
					}

					if (plainKey.equals(name))
					{
						assertEquals(language + ": proper noun translated: " + key, name, plainValue);
					}
					else
					{
						assertTrue(language + ": proper noun missing from translation: " + key,
							plainValue.contains(name));
					}
				}
			}
		}
	}

	private static List<String> languages() throws Exception
	{
		try (Stream<Path> files = Files.list(DIRECTORY))
		{
			return files
				.map(path -> path.getFileName().toString())
				.filter(name -> name.endsWith(".txt"))
				.map(name -> name.substring(0, name.length() - ".txt".length()))
				.sorted()
				.collect(Collectors.toList());
		}
	}

	private static Map<String, String> dictionary(String language) throws Exception
	{
		String file = "/translations/interface/" + language + ".txt";
		InputStream in = InterfaceDictionaryTest.class.getResourceAsStream(file);
		assertNotNull("missing " + file, in);

		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			return TranslationStore.parse(reader);
		}
	}

	/**
	 * Reads the file without parsing, to detect duplicated keys (the parser keeps the last one).
	 */
	private static List<String[]> readRawEntries(String language) throws Exception
	{
		List<String[]> entries = new ArrayList<>();

		String file = "/translations/interface/" + language + ".txt";
		InputStream in = InterfaceDictionaryTest.class.getResourceAsStream(file);
		assertNotNull("missing " + file, in);

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
