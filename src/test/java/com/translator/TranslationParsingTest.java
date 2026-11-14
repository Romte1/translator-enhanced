package com.translator;

import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TranslationParsingTest
{
	@Test
	public void keepsSemicolonsInsideTheTranslation() throws Exception
	{
		Map<String, String> parsed = TranslationStore.parse(new StringReader(
			"I no longer need these bones - take them, please.;Ya no necesito estos huesos; tómalos, por favor."
		));

		assertEquals(
			"Ya no necesito estos huesos; tómalos, por favor.",
			parsed.get("I no longer need these bones - take them, please."));
	}

	@Test
	public void ignoresCommentsBlankLinesAndBrokenLines() throws Exception
	{
		Map<String, String> parsed = TranslationStore.parse(new StringReader(
			"# comentario;ignorado\n"
				+ "\n"
				+ "sin separador\n"
				+ "sin valor;\n"
				+ ";sin clave\n"
				+ "Walk here;Caminar aquí\n"
		));

		assertEquals(1, parsed.size());
		assertEquals("Caminar aquí", parsed.get("Walk here"));
	}

	@Test
	public void preservesKeysWithLeadingSpaces() throws Exception
	{
		Map<String, String> parsed = TranslationStore.parse(new StringReader(" Sneaky undead fowl;Ave furtiva\n"));

		assertTrue(parsed.containsKey(" Sneaky undead fowl"));
		assertFalse(parsed.containsKey("Sneaky undead fowl"));
	}

	@Test
	public void addsTrimmedAliases() throws Exception
	{
		Map<String, String> parsed = TranslationStore.parse(new StringReader(" Sneaky undead fowl;Ave furtiva\n"));

		TranslationStore.addTrimmedAliases(parsed);

		assertEquals("Ave furtiva", parsed.get("Sneaky undead fowl"));
		assertEquals("Ave furtiva", parsed.get(" Sneaky undead fowl"));
	}

	@Test
	public void addsAnnotationAliases() throws Exception
	{
		Map<String, String> parsed = TranslationStore.parse(new StringReader(
			"<col=000000>Task.<col=ffffff>(<col=800000>16 Fishing<col=000080><col=ffffff>);"
				+ "<col=000000>Tarea.<col=ffffff>(<col=800000>16 Fishing<col=000080><col=ffffff>)\n"
		));

		TranslationStore.addAnnotationAliases(parsed);

		assertEquals("<col=000000>Tarea.", parsed.get("<col=000000>Task."));
	}

	@Test
	public void addsColourCloseAliases() throws Exception
	{
		Map<String, String> parsed = TranslationStore.parse(new StringReader(
			"<col=000000>Task.;<col=000000>Tarea.\n"
		));

		TranslationStore.addColourCloseAliases(parsed);

		assertEquals("<col=000000>Tarea.</col>", parsed.get("<col=000000>Task.</col>"));
	}

	@Test
	public void signatureIgnoresTagsAndLineBreaks() throws Exception
	{
		String plain = "<col=000000>Find out what your family crest is from Sir Renitee."
			+ "<col=ffffff>(<col=800000>16 Construction<col=000080><col=ffffff>)";
		String broken = "<col=000000>Find out what your family crest is from Sir Renitee."
			+ "<br><col=ffffff>(<col=800000>16 Construction<col=000080><col=ffffff>)";

		assertEquals(TranslationStore.signature(plain), TranslationStore.signature(broken));
	}

	@Test
	public void unescapesLegacyDialogueTags() throws Exception
	{
		String open = "\\" + "u003c";
		String close = "\\" + "u003e";
		String source = open + "col=000080" + close + "-- Slave;"
			+ open + "col=000080" + close + "-- Esclavo";

		Map<String, String> parsed = TranslationStore.parse(new StringReader(source));

		assertEquals("<col=000080>-- Esclavo", parsed.get("<col=000080>-- Slave"));
	}

	@Test
	public void leavesUnknownEscapedAngleBracketsAlone() throws Exception
	{
		String open = "\\" + "u003c";
		String close = "\\" + "u003e";
		String source = open + "missing" + close + ";texto";

		Map<String, String> parsed = TranslationStore.parse(new StringReader(source));

		assertTrue(parsed.containsKey(open + "missing" + close));
	}

	@Test
	public void bundledCorrectionsAreAvailable() throws Exception
	{
		InputStream in = getClass().getResourceAsStream("/translations/overrides/spanish.txt");
		assertNotNull(in);

		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			Map<String, String> corrections = TranslationStore.parse(reader);
			assertEquals("Equipar", corrections.get("Wield"));
			assertEquals("Vender 1", corrections.get("Sell 1"));
		}
	}

	@Test
	public void bundledInterfaceTranslationsAreAvailable() throws Exception
	{
		InputStream in = getClass().getResourceAsStream("/translations/interface/spanish.txt");
		assertNotNull(in);

		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			Map<String, String> interfaceTexts = TranslationStore.parse(reader);
			assertEquals("Dificultad:", interfaceTexts.get("Difficulty:"));
			assertEquals("Mostrar en el mapa", interfaceTexts.get("Show on Map"));
		}
	}
}
