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
