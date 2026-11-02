package com.translator;

import java.util.ArrayList;
import java.util.List;

final class WidgetReflow
{
	private WidgetReflow()
	{
	}

	/**
	 * Splits a text into lines of at most {@code budget} visible characters, keeping tags
	 * attached to their word.
	 */
	static List<String> wrap(String text, int budget)
	{
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		int visible = 0;

		for (String word : text.split(" "))
		{
			int wordLength = visibleLength(word);
			boolean mustBreak = line.length() > 0 && visible + 1 + wordLength > budget;

			if (mustBreak)
			{
				lines.add(line.toString());
				line.setLength(0);
				visible = 0;
			}

			if (line.length() > 0)
			{
				line.append(' ');
				visible++;
			}

			line.append(word);
			visible += wordLength;
		}

		if (line.length() > 0)
		{
			lines.add(line.toString());
		}

		return lines;
	}

	/**
	 * @return the characters that are actually drawn, ignoring colour tags
	 */
	static int visibleLength(String text)
	{
		int length = 0;
		boolean inTag = false;

		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);

			if (c == '<')
			{
				inTag = true;
			}
			else if (c == '>')
			{
				inTag = false;
			}
			else if (!inTag)
			{
				length++;
			}
		}

		return length;
	}
}
