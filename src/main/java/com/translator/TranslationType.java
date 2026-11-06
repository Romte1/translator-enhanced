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
	DIALOGUE("dialogue", true, false),
	/** NPC names, eg. {@code Goblin}. */
	NPC("npc", true, false),
	/** Item names, eg. {@code Abyssal whip}. */
	ITEM("item", true, false),
	/** Game object names, eg. {@code Tree}. */
	OBJECT("object", true, false),
	/** Right click menu options, eg. {@code Talk-to}. */
	MENU_OPTION("menu_entry_option", false, true),
	/**
	 * Interface texts (quest journals, achievement diaries, combat achievements...).
	 *
	 * <p>Not downloadable from osrs-translations: these texts are curated by the project, because
	 * machine translations of them are unreliable and they contain proper nouns (NPCs, places,
	 * monsters) that must stay in english.</p>
	 */
	INTERFACE("interface", false, true);

	private final String fileName;
	private final boolean downloadable;
	private final boolean bundled;

	TranslationType(String fileName, boolean downloadable, boolean bundled)
	{
		this.fileName = fileName;
		this.downloadable = downloadable;
		this.bundled = bundled;
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

	/**
	 * @return whether this dictionary has a reviewed copy bundled with the plugin
	 */
	public boolean isBundled()
	{
		return bundled;
	}
}
