package com.translator;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AnnotatedLookupTest
{
	@Test
	public void findsTheAnnotatedTranslationWithoutTags()
	{
		Map<String, String> dictionary = new HashMap<>();
		String key = "<col=000000>Smith some Blurite Limbs on Doric's Anvil."
			+ "<col=ffffff>(<col=800000><str>10 Mining</str>, <col=800000>13 Smithing</col></col>)";
		String value = "<col=000000>Forja unos Blurite Limbs en el yunque de Doric."
			+ "<col=ffffff>(<col=800000><str>10 Mining</str>, <col=800000>13 Smithing</col></col>)";
		dictionary.put(key, value);

		Map<String, String> index = TranslationStore.buildAnnotatedSignatures(dictionary);
		String completed = "<str><col=000000>Smith some Blurite Limbs on Doric's Anvil.";

		assertEquals(value, index.get(TranslationStore.signature(completed)));
	}

	@Test
	public void translatesBothHalvesOfASplitDiaryTask()
	{
		Map<String, String> dictionary = new HashMap<>();
		String requirements = "<col=ffffff>(<col=800000>78 Runecraft<col=000080><col=ffffff>)";
		dictionary.put(
			"<col=000000>Craft 100 or more earth runes simultaneously from Essence without the use of Extracts."
				+ requirements,
			"<col=000000>Fabrica 100 o más earth runes simultáneamente a partir de Essence sin usar Extracts."
				+ requirements);

		Map<String, String> fragments = TranslationStore.buildDiaryFragments(dictionary);
		String first = fragments.get(TranslationStore.signature(
			"<col=000000>Craft 100 or more earth runes simultaneously from"));
		String second = fragments.get(TranslationStore.signature(
			"<col=000000>Essence without the use of Extracts." + requirements));

		assertEquals(true, first != null && first.contains("Fabrica"));
		assertEquals(true, second != null && second.contains("Extracts"));
	}

	@Test
	public void findsTheBareTaskFromTheAnnotatedEntry()
	{
		Map<String, String> dictionary = new HashMap<>();
		String task = "<col=000000>Cut and burn some magic logs in the Resource Area.";
		String value = "<col=000000>Corta y quema algunos magic logs en el Resource Area."
			+ "<col=ffffff>(<col=800000>75 Woodcutting<col=000080>, "
			+ "<col=800000>75 Firemaking<col=000080><col=ffffff>)";
		dictionary.put(task + "<col=ffffff>(<col=800000>75 Woodcutting<col=000080>, "
			+ "<col=800000>75 Firemaking<col=000080><col=ffffff>)", value);

		Map<String, String> index = TranslationStore.buildAnnotated(dictionary);

		assertEquals(value, index.get(task));
	}

	@Test
	public void keepsTheDifferentTaskVariantsWorking()
	{
		Map<String, String> dictionary = new HashMap<>();
		dictionary.put("<col=000000>Catch a mackerel.<col=ffffff>(<col=800000>16 Fishing</col></col>)",
			"<col=000000>Pesca un mackerel.<col=ffffff>(<col=800000>16 Fishing</col></col>)");

		Map<String, String> index = TranslationStore.buildAnnotatedSignatures(dictionary);

		assertEquals("<col=000000>Pesca un mackerel.<col=ffffff>(<col=800000>16 Fishing</col></col>)",
			index.get(TranslationStore.signature("<str><col=000000>Catch a mackerel.")));
	}
}
