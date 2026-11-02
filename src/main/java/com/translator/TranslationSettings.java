package com.translator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Translates the user configuration into decisions the rest of the plugin can use.
 *
 * <p>Keeping this mapping in one place means a new toggle only has to be wired here, and the
 * loaders (which dictionaries to download) and the translators (which entries to touch) can
 * never disagree.</p>
 */
@Singleton
class TranslationSettings
{
	private final TranslatorConfig config;

	@Inject
	TranslationSettings(TranslatorConfig config)
	{
		this.config = config;
	}

	/**
	 * @return whether the given dictionary is needed by at least one enabled feature
	 */
	boolean isEnabled(TranslationType type)
	{
		switch (type)
		{
			case DIALOGUE:
				return config.translateDialogues() || config.translateDialogueOptions();
			case NPC:
				return config.translateNpcNames();
			case ITEM:
				return config.translateItemNames();
			case OBJECT:
				return config.translateObjectNames();
			case MENU_OPTION:
				return config.translateMenuOptions();
			case INTERFACE:
				return config.translateQuestGuide();
			default:
				return false;
		}
	}

	List<TranslationType> enabledTypes()
	{
		List<TranslationType> types = new ArrayList<>(TranslationType.values().length);
		for (TranslationType type : TranslationType.values())
		{
			if (isEnabled(type))
			{
				types.add(type);
			}
		}
		return types;
	}

	/**
	 * @return whether anything inside right click menus should be translated
	 */
	boolean anyMenuTranslationEnabled()
	{
		return config.translateMenuOptions()
			|| config.translateNpcNames()
			|| config.translateItemNames()
			|| config.translateObjectNames();
	}
}
