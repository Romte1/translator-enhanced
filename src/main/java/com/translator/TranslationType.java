package com.translator;

/**
 * The translation dictionaries the plugin can load.
 *
 * <p>The file name is also the value used by the data collection endpoint, so it must not be
 * renamed.</p>
 */
public enum TranslationType
{
	/** Chatbox dialogues (NPC / player lines). */
	DIALOGUE("dialogue", false, false),
	/** NPC names, eg. {@code Goblin}. */
	NPC("npc", false, false),
	/** Item names, eg. {@code Abyssal whip}. */
	ITEM("item", false, false),
	/** Game object names, eg. {@code Tree}. */
	OBJECT("object", false, false),
	/** Right click menu options, eg. {@code Talk-to}. */
	MENU_OPTION("menu_entry_option", false, true),
	/** Interface texts (quest journals, achievement diaries, combat achievements). */
	INTERFACE("interface", true, true);

	private final String fileName;
	private final boolean bundled;
	private final boolean curated;

	TranslationType(String fileName, boolean bundled, boolean curated)
	{
		this.fileName = fileName;
		this.bundled = bundled;
		this.curated = curated;
	}

	/**
	 * @return the file name used both by the download URL and the collection API.
	 */
	public String fileName()
	{
		return fileName;
	}

	public boolean isBundled()
	{
		return bundled;
	}

	public boolean isCurated()
	{
		return curated;
	}
}
