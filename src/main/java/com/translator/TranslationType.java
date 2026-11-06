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
	DIALOGUE("dialogue", false),
	/** NPC names, eg. {@code Goblin}. */
	NPC("npc", false),
	/** Item names, eg. {@code Abyssal whip}. */
	ITEM("item", false),
	/** Game object names, eg. {@code Tree}. */
	OBJECT("object", false),
	/** Right click menu options, eg. {@code Talk-to}. */
	MENU_OPTION("menu_entry_option", true),
	/** Interface texts (quest journals, achievement diaries, combat achievements). */
	INTERFACE("interface", true);

	private final String fileName;
	private final boolean bundled;

	TranslationType(String fileName, boolean bundled)
	{
		this.fileName = fileName;
		this.bundled = bundled;
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
}
