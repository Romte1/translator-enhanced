package com.translator;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TranslationLogicTest
{
	private static final String TASK = "<col=000000>Task.";
	private static final String REQUIREMENT = "<col=ffffff>(<col=800000>16 Fishing<col=000080><col=ffffff>)";
	private static final String TASK_ES = "<col=000000>Tarea.";

	@Test
	public void annotatedLookupKeepsTheRequirements()
	{
		Map<String, String> dictionary = new HashMap<>();
		dictionary.put(TASK + REQUIREMENT, TASK_ES + REQUIREMENT);

		Map<String, String> annotated = TranslationStore.buildAnnotated(dictionary);

		assertEquals(TASK_ES + REQUIREMENT, annotated.get(TASK));
	}

	@Test
	public void annotatedLookupIgnoresEntriesWithoutRequirements()
	{
		Map<String, String> dictionary = new HashMap<>();
		dictionary.put(TASK, TASK_ES);

		assertNull(TranslationStore.buildAnnotated(dictionary).get(TASK));
	}

	@Test
	public void reverseLookupFindsTheOriginalTranslation()
	{
		Map<String, String> dictionary = new HashMap<>();
		dictionary.put(TASK, TASK_ES);

		Map<String, String> reverse = TranslationStore.buildReverse(dictionary);

		assertEquals(TASK, reverse.get(TASK_ES));
	}

	@Test
	public void reverseLookupIgnoresInsertedLineBreaks()
	{
		Map<String, String> dictionary = new HashMap<>();
		dictionary.put(TASK, TASK_ES);

		Map<String, String> reverse = TranslationStore.buildReverse(dictionary);

		assertEquals(TASK, reverse.get(TranslationStore.withoutBreaks(TASK_ES + "<br>")));
	}

	@Test
	public void reverseLookupIgnoresColourTags()
	{
		Map<String, String> dictionary = new HashMap<>();
		dictionary.put("<col=000000>Other.", "<col=000000>Otra.");

		Map<String, String> reverse = TranslationStore.buildReverse(dictionary);

		assertEquals("<col=000000>Other.", reverse.get("Otra."));
	}

	@Test
	public void journalTranslationOnlyRunsForJournalScripts()
	{
		assertEquals(true, WidgetTranslator.isJournalScript(net.runelite.api.ScriptID.DIARY_UPDATE_LINECOUNT));
		assertEquals(true, WidgetTranslator.isJournalScript(net.runelite.api.ScriptID.QUEST_UPDATE_LINECOUNT));
		assertEquals(false, WidgetTranslator.isJournalScript(net.runelite.api.ScriptID.CHAT_SEND));
	}

	@Test
	public void detectsQuestHelperClasses()
	{
		assertEquals(true, WidgetTranslator.isQuestHelperClass("com.questhelper.QuestHelperPlugin"));
		assertEquals(false, WidgetTranslator.isQuestHelperClass("com.translator.TranslatorPlugin"));
	}
}
