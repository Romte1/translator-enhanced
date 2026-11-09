package com.translator;

import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MenuDictionaryTest
{
	private static final Path DIRECTORY = Paths.get("src/main/resources/translations/menu_entry_option");

	private static final Pattern TAG = Pattern.compile("<[^>]*>");
	private static final Pattern NUMBER = Pattern.compile("\\d+");
	private static final Pattern PLACEHOLDER = Pattern.compile("\\$(\\d+)");
	private static final Pattern SKILL_GUIDE =
		Pattern.compile("^View (<col=[^>]+>)([A-Za-z]+)(</col>) guide$");

	@Test
	public void keysAreUnique() throws Exception
	{
		for (String language : languages())
		{
			Set<String> keys = new HashSet<>();
			for (String line : Files.readAllLines(DIRECTORY.resolve(language + ".txt"), StandardCharsets.UTF_8))
			{
				if (!line.contains(";"))
				{
					continue;
				}

				String key = line.substring(0, line.indexOf(';'));
				assertTrue(language + ": duplicate key: " + key, keys.add(key));
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
				if (entry.getKey().startsWith("re:")
					|| entry.getKey().startsWith("Add friend ")
					|| entry.getKey().startsWith("Add ignore "))
				{
					continue;
				}

				assertEquals(language + ": numbers differ for: " + entry.getKey(),
					numbers(entry.getKey()), numbers(entry.getValue()));
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
	public void dynamicMenuOptionsUsePatterns() throws Exception
	{
		Map<String, String> spanish = dictionary("spanish");

		assertEquals("Retirar-454", applyPatterns(spanish, "Withdraw-454"));
		assertEquals("Depositar-454", applyPatterns(spanish, "Deposit-454"));
		assertEquals("Comprar: 1 x Death rune", applyPatterns(spanish, "Buy: 1 x Death rune"));
		assertEquals("Comprar: 1,000 x Oak logs", applyPatterns(spanish, "Buy: 1,000 x Oak logs"));
		assertEquals("Vender: 100 x Death rune", applyPatterns(spanish, "Sell: 100 x Death rune"));
		assertEquals("Cantidad predeterminada: 10,000", applyPatterns(spanish, "Default quantity: 10,000"));
		assertEquals("Agregar amigo Player 123", applyPatterns(spanish, "Add friend Player 123"));
		assertEquals("Agregar ignorar Player 123", applyPatterns(spanish, "Add ignore Player 123"));

		assertTrue(!spanish.containsKey("Withdraw-454"));
		assertTrue(!spanish.containsKey("Buy: 1 x Death rune"));
		assertTrue(!spanish.containsKey("Add friend Player 123"));

		for (String language : languages())
		{
			Map<String, String> dictionary = dictionary(language);
			assertTrue(language + ": Withdraw pattern missing",
				!applyPatterns(dictionary, "Withdraw-454").equals("Withdraw-454"));
			assertTrue(language + ": Buy pattern missing",
				!applyPatterns(dictionary, "Buy: 1 x Death rune").equals("Buy: 1 x Death rune"));
			assertTrue(language + ": Add friend pattern missing",
				!applyPatterns(dictionary, "Add friend Player 123").equals("Add friend Player 123"));
		}
	}

	@Test
	public void spanishUsesActionsForEmptyAndNet() throws Exception
	{
		Map<String, String> spanish = dictionary("spanish");

		assertEquals("Vaciar", spanish.get("Empty"));
		assertEquals("Vaciar", spanish.get("Empty Dish"));
		assertEquals("Vaciar cesta", spanish.get("Empty basket"));
		assertEquals("Pescar con red", spanish.get("Net"));
		assertEquals("Abrir banco", spanish.get("Bank"));
		assertEquals("Salir", spanish.get("Exit"));
		assertEquals("Ver", spanish.get("View"));
	}

	@Test
	public void spanishKeepsSkillNamesInGuides() throws Exception
	{
		Map<String, String> spanish = dictionary("spanish");

		for (Map.Entry<String, String> entry : spanish.entrySet())
		{
			Matcher matcher = SKILL_GUIDE.matcher(entry.getKey());
			if (!matcher.matches())
			{
				continue;
			}

			assertTrue("skill name translated in: " + entry.getKey(),
				entry.getValue().contains(matcher.group(1) + matcher.group(2) + matcher.group(3)));
		}
	}

	@Test
	public void spanishKeepsItemNamesInBuyAndSell() throws Exception
	{
		Map<String, String> spanish = dictionary("spanish");

		for (Map.Entry<String, String> entry : spanish.entrySet())
		{
			String key = entry.getKey();
			if (key.startsWith("Buy: "))
			{
				assertEquals("Comprar: " + key.substring("Buy: ".length()), entry.getValue());
			}
			else if (key.startsWith("Sell: "))
			{
				assertEquals("Vender: " + key.substring("Sell: ".length()), entry.getValue());
			}
		}
	}

	@Test
	public void spanishKeepsFriendAndIgnoreNames() throws Exception
	{
		Map<String, String> spanish = dictionary("spanish");

		for (Map.Entry<String, String> entry : spanish.entrySet())
		{
			if (entry.getKey().startsWith("Add friend "))
			{
				assertTrue(entry.getValue().endsWith(entry.getKey().substring("Add friend ".length())));
			}
			else if (entry.getKey().startsWith("Add ignore "))
			{
				assertTrue(entry.getValue().endsWith(entry.getKey().substring("Add ignore ".length())));
			}
		}
	}

	@Test
	public void spanishXpLinesUsePuntosAndEnglishSkill() throws Exception
	{
		Map<String, String> spanish = dictionary("spanish");

		for (Map.Entry<String, String> entry : spanish.entrySet())
		{
			String key = entry.getKey();
			if (!key.matches("^.+ XP: \\d+ points?$"))
			{
				continue;
			}

			assertTrue("points not translated in: " + key, entry.getValue().contains("punto"));
			assertTrue("skill changed in: " + key,
				entry.getValue().startsWith(key.substring(0, key.indexOf(" XP: ")) + " XP: "));
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
		String file = "/translations/menu_entry_option/" + language + ".txt";
		InputStream in = MenuDictionaryTest.class.getResourceAsStream(file);
		assertNotNull("missing " + file, in);

		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			return TranslationStore.parse(reader);
		}
	}

	private static String applyPatterns(Map<String, String> dictionary, String text)
	{
		String result = text;

		for (Map.Entry<String, String> entry : dictionary.entrySet())
		{
			if (!entry.getKey().startsWith("re:"))
			{
				continue;
			}

			Pattern pattern = Pattern.compile(entry.getKey().substring("re:".length()));
			Matcher matcher = pattern.matcher(result);
			if (matcher.find())
			{
				result = matcher.replaceAll(entry.getValue());
			}
		}

		return result;
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
		String plain = TAG.matcher(text).replaceAll("")
			.replaceAll("(?<=\\d)[\\s\\p{Zs}.,'’](?=\\d)", "");
		Matcher matcher = NUMBER.matcher(plain);
		while (matcher.find())
		{
			numbers.add(matcher.group());
		}
		return numbers;
	}
}
