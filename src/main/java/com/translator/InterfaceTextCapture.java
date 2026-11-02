package com.translator;

import net.runelite.client.RuneLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
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
 * <p>Output: {@code ~/.runelite/translator/untranslated/<language>.txt}, one
 * {@code english;} line per text, ready to be filled in and merged into
 * {@code src/main/resources/translations/interface/<language>.txt}.</p>
 */
@Singleton
class InterfaceTextCapture
{
	private static final Logger log = LoggerFactory.getLogger(InterfaceTextCapture.class);

	private static final File OUTPUT_DIR = new File(RuneLite.RUNELITE_DIR, "translator/untranslated");

	/**
	 * {@code file + \u0000 + text} of everything already written or already present in the file,
	 * so restarting the client does not duplicate the contribution file.
	 */
	private final Set<String> captured = ConcurrentHashMap.newKeySet();

	private final Set<Path> loadedFiles = ConcurrentHashMap.newKeySet();

	private boolean writeFailed;

	@Inject
	InterfaceTextCapture()
	{
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
		if (text == null || text.isEmpty())
		{
			return;
		}

		Path file = new File(OUTPUT_DIR, language + ".txt").toPath();
		String key = file + "\u0000" + text;

		if (captured.contains(key) || !captured.add(key))
		{
			return;
		}

		loadExistingKeys(file);

		try
		{
			Files.createDirectories(OUTPUT_DIR.toPath());
			Files.write(file, (text + ";\n").getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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
	private void loadExistingKeys(Path file)
	{
		if (!loadedFiles.add(file) || !Files.isRegularFile(file))
		{
			return;
		}

		try
		{
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			for (String line : lines)
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
