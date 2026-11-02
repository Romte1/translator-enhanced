package com.translator;

import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saves interface texts that have no reviewed translation, so they can be translated and
 * shipped with the plugin.
 *
 * <p>The goal is a curated dictionary instead of machine translation: journal texts contain
 * names of NPCs, monsters and places that must stay in english, which is something an
 * automatic translation can not know.</p>
 *
 * <p>Output: {@code untranslated/<language>.txt} inside the plugin data directory, one
 * {@code english;} line per text, ready to be filled in and merged into
 * {@code src/main/resources/translations/interface/<language>.txt}.</p>
 */
@Singleton
class InterfaceTextCapture
{
	private static final Logger log = LoggerFactory.getLogger(InterfaceTextCapture.class);

	/**
	 * {@code file + \u0000 + text} of everything already written or already present in the file,
	 * so restarting the client does not duplicate the contribution file.
	 */
	private final Set<String> captured = ConcurrentHashMap.newKeySet();

	private final Set<Filepath> loadedFiles = ConcurrentHashMap.newKeySet();

	private Filepath directory;

	private boolean writeFailed;

	@Inject
	InterfaceTextCapture()
	{
	}

	void setDirectory(Filepath directory)
	{
		this.directory = directory;
	}

	/**
	 * Appends a text to the contribution file. Duplicates are ignored, including the ones
	 * already in the file from previous sessions.
	 *
	 * @param language target language this text would be translated to
	 * @param text     english text as it appears in game
	 */
	void capture(TranslatorConfig.Language language, String text)
	{
		if (directory == null || text == null || text.isEmpty())
		{
			return;
		}

		Filepath file = directory.join("untranslated").join(language + ".txt");
		String key = file + "\u0000" + text;

		if (captured.contains(key) || !captured.add(key))
		{
			return;
		}

		loadExistingKeys(file);

		try
		{
			file.getParent().createDirectories();
			file.write(text + ";\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException e)
		{
			if (!writeFailed)
			{
				writeFailed = true;
				log.warn("failed to write untranslated interface texts to {}", file, e);
			}
		}
	}

	/**
	 * Reads the keys already present so they are not written again.
	 */
	private void loadExistingKeys(Filepath file)
	{
		if (!loadedFiles.add(file) || !file.isFile())
		{
			return;
		}

		try (BufferedReader reader = file.openBufferedReader())
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.charAt(0) == '#')
				{
					continue;
				}

				int separator = line.indexOf(';');
				String key = separator >= 0 ? line.substring(0, separator) : line;
				if (!key.isEmpty())
				{
					captured.add(file + "\u0000" + key);
				}
			}
		}
		catch (IOException e)
		{
			log.debug("failed to read existing untranslated texts from {}", file, e);
		}
	}
}
