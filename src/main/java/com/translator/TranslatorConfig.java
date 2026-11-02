/*
 * Copyright (c) 2018, Morgan Lewis <https://github.com/MESLewis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.translator;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("translator-enhanced")
public interface TranslatorConfig extends Config
{
	enum Language
	{
		finnish,
		french,
		german,
		italian,
		portuguese,
		spanish,
		swedish,
		dutch
	}

	String GENERAL_SECTION = "general";
	String MENUS_SECTION = "menus";
	String TEXT_SECTION = "text";
	String JOURNALS_SECTION = "journals";
	String QUALITY_SECTION = "quality";

	@ConfigSection(position = 1, name = "General", description = "Language and data collection")
	String general = GENERAL_SECTION;

	@ConfigItem(
		keyName = "SelectLanguage",
		name = "Language",
		description = "Language to translate to",
		position = 1,
		section = GENERAL_SECTION
	)
	default Language selectLanguage()
	{
		return Language.spanish;
	}

	@ConfigItem(
		keyName = "enableOSRSTextCollection",
		name = "Enable text capture",
		description = "Sends texts that have no translation yet to the project API so they can be added.<br>" +
			"Opt-in: nothing is sent unless you enable this.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 2,
		section = GENERAL_SECTION
	)
	default boolean enableTextCapture()
	{
		return false;
	}

	@ConfigSection(
		position = 2,
		name = "Menus",
		description = "What to translate inside right click menus"
	)
	String menus = MENUS_SECTION;

	@ConfigItem(
		keyName = "translateMenuOptions",
		name = "Menu options",
		description = "Translate the action itself: Talk-to, Examine, Bank, Walk here...",
		position = 1,
		section = MENUS_SECTION
	)
	default boolean translateMenuOptions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "translateNpcNames",
		name = "NPC names",
		description = "Translate names of NPCs and monsters.<br>" +
			"Other players and guides use the english names.",
		position = 2,
		section = MENUS_SECTION
	)
	default boolean translateNpcNames()
	{
		return false;
	}

	@ConfigItem(
		keyName = "translateItemNames",
		name = "Item names",
		description = "Translate names of items, in the inventory and on the ground.<br>" +
			"Trading and guides use the english names.",
		position = 3,
		section = MENUS_SECTION
	)
	default boolean translateItemNames()
	{
		return false;
	}

	@ConfigItem(
		keyName = "translateObjectNames",
		name = "Object names",
		description = "Translate names of scenery and objects.<br>" +
			"Guides use the english names.",
		position = 4,
		section = MENUS_SECTION
	)
	default boolean translateObjectNames()
	{
		return false;
	}

	@ConfigSection(position = 3, name = "Text", description = "Dialogues and messages")
	String text = TEXT_SECTION;

	@ConfigItem(
		keyName = "translateDialogues",
		name = "Chatbox: dialogues",
		description = "Translate the NPC and player dialogue lines",
		position = 1,
		section = TEXT_SECTION
	)
	default boolean translateDialogues()
	{
		return true;
	}

	@ConfigItem(
		keyName = "translateDialogueOptions",
		name = "Chatbox: dialogue options",
		description = "Translate the clickable answers of a dialogue.<br>" +
			"Plugins that pick answers for you match the english text.",
		position = 2,
		section = TEXT_SECTION
	)
	default boolean translateDialogueOptions()
	{
		return false;
	}

	@ConfigSection(
		position = 4,
		name = "Journals",
		description = "Quest journal, achievement diaries and combat achievements"
	)
	String journals = JOURNALS_SECTION;

	@ConfigItem(
		keyName = "translateQuestGuide",
		name = "Translate journals",
		description = "Translate the quest journal, achievement diaries and combat achievements<br>" +
			"with the reviewed translations that ship with the plugin.",
		position = 1,
		section = JOURNALS_SECTION
	)
	default boolean translateQuestGuide()
	{
		return true;
	}

	@ConfigItem(
		keyName = "captureInterfaceTexts",
		name = "Capture untranslated",
		description = "Save every journal text that has no reviewed translation to<br>" +
			"<b>~/.runelite/translator/untranslated/&lt;language&gt;.txt</b> so it can be translated.",
		position = 2,
		section = JOURNALS_SECTION
	)
	default boolean captureInterfaceTexts()
	{
		return false;
	}

	@ConfigItem(
		keyName = "useOnlineInterfaceTranslations",
		name = "Download translations",
		description = "Keep the journals dictionary updated without a plugin update.<br>" +
			"Turn it off while editing the bundled file locally.",
		position = 3,
		section = JOURNALS_SECTION
	)
	default boolean useOnlineInterfaceTranslations()
	{
		return true;
	}

	@ConfigSection(
		position = 5,
		name = "Translation quality",
		description = "Corrections applied on top of the downloaded translations"
	)
	String quality = QUALITY_SECTION;

	@ConfigItem(
		keyName = "useLocalCorrections",
		name = "Use corrections",
		description = "Apply the corrections bundled with the plugin plus your own from<br>" +
			"<b>~/.runelite/translator/overrides/&lt;language&gt;.txt</b>",
		position = 1,
		section = QUALITY_SECTION
	)
	default boolean useLocalCorrections()
	{
		return true;
	}

	@ConfigItem(
		keyName = "reloadTranslations",
		name = "Reload translations",
		description = "Download the translation files again",
		position = 2,
		section = QUALITY_SECTION
	)
	default boolean reloadTranslations()
	{
		return false;
	}
}
