package com.translator;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WidgetReflowTest
{
	@Test
	public void visibleLengthIgnoresTags()
	{
		assertEquals(12, WidgetReflow.visibleLength("<col=000000>Kill a duck.</col>"));
	}

	@Test
	public void visibleTextStripsTags()
	{
		assertEquals("Difícil", WidgetReflow.visibleText("<col=ffff00>Difícil</col>"));
	}

	@Test
	public void wrapKeepsTagsWithTheirWord()
	{
		String text = "<col=ffffff>(<col=800000>10 Mining<col=000080>, <col=800000>13 Smithing<col=000080><col=ffffff>)";
		List<String> lines = WidgetReflow.wrap(text, 12);

		assertTrue(lines.size() > 1);

		for (String line : lines)
		{
			assertTrue("line too long: " + line, WidgetReflow.visibleLength(line) <= 12);
		}

		assertEquals(text.replace(" ", ""), String.join("", lines).replace(" ", ""));
	}

	@Test
	public void wrapKeepsShortTextInOneLine()
	{
		List<String> lines = WidgetReflow.wrap("Teleport to Falador.", 60);

		assertEquals(1, lines.size());
		assertEquals("Teleport to Falador.", lines.get(0));
	}
}
