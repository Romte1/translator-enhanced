package com.translator;

/**
 * The translation dictionaries the plugin can load.
 *
 * <p>Most values map to a file inside the
 * <a href="https://github.com/iolave/osrs-translations">osrs-translations</a> repository, and
 * the file name is also the value used by the data collection endpoint, so it must not be
 * renamed.</p>
 */
public enum TranslationType
{
	/** Chatbox dialogues (NPC / player lines). */
	DIALOGUE("dialogue", true),
	/** NPC names, eg. {@code Goblin}. */
	NPC("npc", true),
	/** Item names, eg. {@code Abyssal whip}. */
	ITEM("item", true),
	/** Game object names, eg. {@code Tree}. */
	OBJECT("object", true),
	/** Right click menu options, eg. {@code Talk-to}. */
	MENU_OPTION("menu_entry_option", true),
	/**
	 * Interface texts (quest journals, achievement diaries, combat achievements...).
	 *
	 * <p>Not downloadable: these texts are curated by the project, because machine
	 * translations of them are unreliable and they contain proper nouns (NPCs, places,
	 * monsters) that must stay in english.</p>
	 */
	INTERFACE("interface", false);

	private final String fileName;
	private final boolean downloadable;

	TranslationType(String fileName, boolean downloadable)
	{
		this.fileName = fileName;
		this.downloadable = downloadable;
	}

	/**
	 * @return the file name used both by the download URL and the collection API.
	 */
	public String fileName()
	{
		return fileName;
	}

	/**
	 * @return whether this dictionary can be downloaded from osrs-translations
	 */
	public boolean isDownloadable()
	{
		return downloadable;
	}
}
