package com.translator;

/**
 * A parsed right click menu target.
 *
 * <p>OSRS builds menu targets out of optional colour tags plus the entity name, and sometimes
 * a suffix:</p>
 *
 * <pre>
 *   &lt;col=ffff00&gt;Goblin&lt;col=ff0000&gt; (level-2)&lt;/col&gt;   NPC with combat level
 *   &lt;col=00ffff&gt;Tree&lt;/col&gt;                            game object
 *   Shark (2)                                             stacked ground item
 *   Prayer potion(4)                                      item whose name contains parentheses
 * </pre>
 *
 * <p>Splitting it into prefix (colour tags), name and suffix (combat level, amount and colour
 * tags) allows translating only the name while preserving everything else byte for byte.</p>
 */
final class MenuTarget
{
	private final String prefix;
	private final String name;
	private final String suffix;

	private MenuTarget(String prefix, String name, String suffix)
	{
		this.prefix = prefix;
		this.name = name;
		this.suffix = suffix;
	}

	/**
	 * Parses a raw menu target.
	 *
	 * @param raw the raw target as returned by {@link net.runelite.api.MenuEntry#getTarget()}
	 * @return the parsed target, or {@code null} when there is nothing translatable
	 */
	static MenuTarget parse(String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return null;
		}

		// skip leading colour tags, eg. "<col=ffff00>"
		int start = 0;
		while (start < raw.length() && raw.charAt(start) == '<')
		{
			int close = raw.indexOf('>', start);
			if (close < 0)
			{
				start = 0;
				break;
			}
			start = close + 1;
		}

		if (start >= raw.length())
		{
			return null;
		}

		// the name ends at the next tag (combat level / closing colour)
		int end = raw.indexOf('<', start);
		if (end < 0)
		{
			end = raw.length();
		}

		if (end <= start)
		{
			return null;
		}

		String name = raw.substring(start, end);
		if (name.trim().isEmpty())
		{
			return null;
		}

		return new MenuTarget(raw.substring(0, start), name, raw.substring(end));
	}

	/**
	 * @return the untranslated entity name, without colour tags.
	 */
	String getName()
	{
		return name;
	}

	/**
	 * Some targets have a suffix that is not part of the name, eg. {@code "Shark (2)"} or
	 * {@code "Goblin (level-2)"}. Only the part before it has to be translated.
	 *
	 * <p>Note that the suffix is only used as a fallback: many names legitimately contain
	 * parentheses ({@code Clue scroll (easy)}), so the full name is always tried first.</p>
	 *
	 * @return the name without the trailing {@code " (...)"} group, or {@code null} if there
	 * is no such group
	 */
	String getCoreName()
	{
		int suffixStart = coreSuffixStart();
		return suffixStart > 0 ? name.substring(0, suffixStart) : null;
	}

	/**
	 * @return the trailing {@code " (...)"} group, or {@code null} if there is none
	 */
	String getCoreSuffix()
	{
		int suffixStart = coreSuffixStart();
		return suffixStart > 0 ? name.substring(suffixStart) : null;
	}

	private int coreSuffixStart()
	{
		int suffixStart = name.indexOf(" (");
		return suffixStart > 0 ? suffixStart : -1;
	}

	/**
	 * Rebuilds the target with the name replaced.
	 *
	 * @param translated the translated name
	 * @return the full target, keeping colour tags, combat level and amount untouched
	 */
	String withName(String translated)
	{
		return prefix + translated + suffix;
	}
}
