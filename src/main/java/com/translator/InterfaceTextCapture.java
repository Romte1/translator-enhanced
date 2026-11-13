package com.translator;

import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

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

	/**
	 * Texts waiting for the executor. Kept separate from {@link #captured} so a failed write
	 * can be retried and a text cannot be queued twice while it is being flushed.
	 */
	private final Set<String> queued = ConcurrentHashMap.newKeySet();

	private final Queue<PendingCapture> pending = new ConcurrentLinkedQueue<>();

	private final Set<Filepath> loadedFiles = ConcurrentHashMap.newKeySet();

	private final AtomicBoolean flushScheduled = new AtomicBoolean();

	private Filepath directory;

	private ExecutorService executor;

	private boolean writeFailed;

	@Inject
	InterfaceTextCapture()
	{
	}

	void setDirectory(Filepath directory)
	{
		this.directory = directory;
	}

	void setExecutor(ExecutorService executor)
	{
		this.executor = executor;
		if (executor != null && !pending.isEmpty())
		{
			scheduleFlush();
		}
	}

	/**
	 * Queues a text to be appended to the contribution file. This runs on the client thread,
	 * so it must never touch the filesystem.
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

		if (captured.contains(key) || !queued.add(key))
		{
			return;
		}

		pending.add(new PendingCapture(file, text, key));
		scheduleFlush();
	}

	private void scheduleFlush()
	{
		ExecutorService current = executor;
		if (current == null || !flushScheduled.compareAndSet(false, true))
		{
			return;
		}

		try
		{
			current.execute(this::flush);
		}
		catch (RejectedExecutionException e)
		{
			flushScheduled.set(false);
		}
	}

	private void flush()
	{
		try
		{
			PendingCapture capture;
			while ((capture = pending.poll()) != null)
			{
				loadExistingKeys(capture.file);

				if (captured.contains(capture.key))
				{
					queued.remove(capture.key);
					continue;
				}

				try
				{
					capture.file.getParent().createDirectories();
					capture.file.write(capture.text + ";\n",
						StandardOpenOption.CREATE, StandardOpenOption.APPEND);
					captured.add(capture.key);
					queued.remove(capture.key);
				}
				catch (IOException e)
				{
					queued.remove(capture.key);
					if (!writeFailed)
					{
						writeFailed = true;
						log.warn("failed to write untranslated interface texts to {}",
							capture.file, e);
					}
				}
			}
		}
		finally
		{
			flushScheduled.set(false);
			if (!pending.isEmpty())
			{
				scheduleFlush();
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

	private static final class PendingCapture
	{
		private final Filepath file;
		private final String text;
		private final String key;

		private PendingCapture(Filepath file, String text, String key)
		{
			this.file = file;
			this.text = text;
			this.key = key;
		}
	}
}
