package com.translator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DiaryStrikeTest
{
	@Test
	public void keepsStrikeOnWholeTask()
	{
		assertEquals(
			"<col=000000><str>Abre el sarcófago.</str></col>",
			WidgetTranslator.preserveStrike(
				"<str><col=000000>Open the Sarcophagus.</col></str>",
				"<col=000000>Open the Sarcophagus.</col>",
				"<col=000000>Abre el sarcófago.</col>"));
	}

	@Test
	public void keepsStrikeWhenRequirementsFollow()
	{
		String source = "<str><col=000000>Smith some Blurite Limbs.</col></str>"
			+ "<col=ffffff>(<col=800000><str>10 Mining</str>, <col=800000>13 Smithing</col></col>)";
		String translated = "<col=000000>Forja unos Blurite Limbs.</col>"
			+ "<col=ffffff>(<col=800000><str>10 Mining</str>, <col=800000>13 Smithing</col></col>)";

		String result = WidgetTranslator.preserveStrike(source, source, translated);

		assertTrue(result.startsWith("<col=000000><str>Forja unos Blurite Limbs.</str></col>"));
		assertTrue(result.contains("<str>10 Mining, </str>"));
		assertTrue(result.contains("<str>13 Smithing"));
	}

	@Test
	public void keepsStrikeWhenTheWholeWidgetIsStruck()
	{
		String source = "<str><col=000000>Smith some Blurite Limbs.</col>"
			+ "<col=ffffff>(<col=800000><str>10 Mining</str></col>)</col></str>";
		String translated = "<col=000000>Forja unos Blurite Limbs.</col>"
			+ "<col=ffffff>(<col=800000><str>10 Mining</str></col>)</col>";

		String result = WidgetTranslator.preserveStrike(source, source, translated);

		assertTrue(result.startsWith("<col=000000><str>Forja unos Blurite Limbs.</str></col>"));
		assertTrue(result.contains("<str>10 Mining"));
	}

	@Test
	public void keepsTheStrikeWhenTheClientLeavesItOpen()
	{
		assertEquals(
			"<col=000000><str>Completa una vuelta al curso de agilidad en la azotea de Falador.</str></col>",
			WidgetTranslator.preserveStrike(
				"<str>Complete a lap of the Falador rooftop agility course.",
				"<str>Complete a lap of the Falador rooftop agility course.",
				"<col=000000>Completa una vuelta al curso de agilidad en la azotea de Falador.</col>"));
	}

	@Test
	public void keepsTheOpenStrikeWhenRequirementsAlreadyHaveTheirOwnTags()
	{
		String source = "<str>Smith some Blurite Limbs on Doric's Anvil.";
		String translated = "<col=000000>Forja unos Blurite Limbs en el yunque de Doric.</col>"
			+ "<col=ffffff>(<col=800000><str>10 Mining</str>, <col=800000>13 Smithing</col></col>)";

		String result = WidgetTranslator.preserveStrike(source, source, translated);

		assertTrue(result.startsWith("<col=000000><str>Forja unos Blurite Limbs en el yunque de Doric.</str></col>"));
		assertTrue(result.contains("<str>10 Mining, </str>"));
		assertTrue(result.contains("<str>13 Smithing"));
	}

	@Test
	public void doesNotWrapARequirementOnlyLine()
	{
		String source = "<col=000080><str>10 Mining</str>, <col=800000>13 Smithing</col>";
		String translated = "<col=000080><str>10 Minería</str>, <col=800000>13 Herrería</col>";

		assertEquals(translated, WidgetTranslator.preserveStrike(source, source, translated));
	}

	@Test
	public void leavesUnstruckTextAlone()
	{
		assertEquals(
			"<col=000000>Abre el sarcófago.</col>",
			WidgetTranslator.preserveStrike(
				"<col=000000>Open the Sarcophagus.</col>",
				"<col=000000>Open the Sarcophagus.</col>",
				"<col=000000>Abre el sarcófago.</col>"));
	}

	@Test
	public void doesNotWrapWhenTheTranslationIsAlreadyStruck()
	{
		String translated = "<col=000000><str>Abre el sarcófago.</str></col>";

		assertEquals(translated, WidgetTranslator.preserveStrike(
			"<str><col=000000>Open the Sarcophagus.</col></str>",
			"<col=000000>Open the Sarcophagus.</col>",
			translated));
	}
}
