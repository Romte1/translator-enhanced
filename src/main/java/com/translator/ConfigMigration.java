package com.translator;

import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Copies the settings that the original Translator plugin stored under {@code translator.*}
 * into the group used by this plugin. The migration runs once and only replaces values that
 * are still at their default, so a user who already configured the new group keeps it.
 */
@Singleton
class ConfigMigration
{
	private static final Logger log = LoggerFactory.getLogger(ConfigMigration.class);

	static final String LEGACY_GROUP = "translator";
	static final String CONFIG_GROUP = "translator-enhanced";
	static final String MARKER = "migratedFromTranslatorConfig";

	private static final Migration[] MIGRATIONS =
	{
		new Migration("SelectLanguage", "spanish"),
		new Migration("enableOSRSTextCollection", "false"),
		new Migration("translateMenuOptions", "true"),
		// NPC, item and object names stay off by default. They were often enabled in the
		// original plugin, but translating names is confusing during trading and guides.
		new Migration("translateDialogues", "true"),
		new Migration("translateDialogueOptions", "false"),
		new Migration("translateQuestGuide", "true"),
		new Migration("captureInterfaceTexts", "false"),
		new Migration("useLocalCorrections", "true"),
	};

	private final ConfigManager configManager;

	@Inject
	ConfigMigration(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	void migrate()
	{
		if ("true".equals(configManager.getConfiguration(CONFIG_GROUP, MARKER)))
		{
			return;
		}

		int migrated = 0;

		for (Migration migration : MIGRATIONS)
		{
			String legacy = configManager.getConfiguration(LEGACY_GROUP, migration.key);
			if (legacy == null)
			{
				continue;
			}

			String current = configManager.getConfiguration(CONFIG_GROUP, migration.key);
			if (current != null && !current.equalsIgnoreCase(migration.defaultValue))
			{
				continue;
			}

			configManager.setConfiguration(CONFIG_GROUP, migration.key, legacy);
			migrated++;
		}

		configManager.setConfiguration(CONFIG_GROUP, MARKER, "true");
		if (migrated > 0)
		{
			log.debug("migrated {} settings from the legacy translator config", migrated);
		}
	}

	private static final class Migration
	{
		private final String key;
		private final String defaultValue;

		private Migration(String key, String defaultValue)
		{
			this.key = key;
			this.defaultValue = defaultValue;
		}
	}
}
