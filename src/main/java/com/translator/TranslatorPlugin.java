/*
 * Copyright (c) 2017, Aria <aria@ar1as.space>
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

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.events.BeforeMenuRender;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@PluginDescriptor(
	name = "Translator Enhanced",
	description = "Translate OSRS dialogues, menus, items, NPCs, objects and journals",
	tags = {"text", "language", "translate"}
)
public class TranslatorPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(TranslatorPlugin.class);

	private static final String CONFIG_GROUP = "translator-enhanced";
	private static final long DATA_COLLECTION_PERIOD_MINUTES = 5;

	@Inject
	private Client client;

	@Inject
	private TranslatorConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private TranslatorAPI api;

	@Inject
	private TranslationStore store;

	@Inject
	private TranslationSettings settings;

	@Inject
	private MenuEntryTranslator menuTranslator;

	@Inject
	private WidgetTranslator widgetTranslator;

	private ExecutorService loader;

	private ScheduledExecutorService collector;

	private ScheduledFuture<?> collectionTask;

	private final Set<TranslationType> loading = ConcurrentHashMap.newKeySet();

	@Provides
	TranslatorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TranslatorConfig.class);
	}

	@Override
	protected void startUp()
	{
		loader = Executors.newFixedThreadPool(2, daemonThreads("translator-loader"));
		collector = Executors.newSingleThreadScheduledExecutor(daemonThreads("translator-collector"));

		store.setLanguage(config.selectLanguage());
		api.setTextCaptureEnabled(config.enableTextCapture());
		menuTranslator.setEnabled(settings.anyMenuTranslationEnabled());

		reloadEnabledTranslations();
		updateCollectionTask();
	}

	@Override
	protected void shutDown()
	{
		if (collectionTask != null)
		{
			collectionTask.cancel(false);
			collectionTask = null;
		}

		if (loader != null)
		{
			loader.shutdownNow();
			loader = null;
		}

		if (collector != null)
		{
			collector.shutdownNow();
			collector = null;
		}

		loading.clear();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		switch (event.getKey())
		{
			case "SelectLanguage":
				store.setLanguage(config.selectLanguage());
				loading.clear();
				menuTranslator.reset();
				reloadEnabledTranslations();
				break;

			case "enableOSRSTextCollection":
				api.setTextCaptureEnabled(config.enableTextCapture());
				updateCollectionTask();
				break;

			case "useOnlineInterfaceTranslations":
				resetTranslations();
				break;

			case "reloadTranslations":
				if (config.reloadTranslations())
				{
					configManager.setConfiguration(CONFIG_GROUP, "reloadTranslations", false);
					resetTranslations();
				}
				break;

			default:
				menuTranslator.setEnabled(settings.anyMenuTranslationEnabled());
				menuTranslator.reset();
				reloadEnabledTranslations();
				break;
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		menuTranslator.collect(event.getMenuEntry());
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		menuTranslator.translateMenu(event.getMenuEntries());
	}

	@Subscribe
	public void onBeforeMenuRender(BeforeMenuRender event)
	{
		menuTranslator.translateMenu(client.getMenuEntries());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		widgetTranslator.onGameTick();
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.QUEST_UPDATE_LINECOUNT)
		{
			widgetTranslator.translateQuestInterfaces();
		}
	}

	private void reloadEnabledTranslations()
	{
		for (TranslationType type : settings.enabledTypes())
		{
			if (store.isLoaded(type) || !loading.add(type))
			{
				continue;
			}

			runAsync(() ->
			{
				try
				{
					store.load(type);
				}
				catch (Exception e)
				{
					log.warn("failed to load {} translations", type.fileName(), e);
				}
				finally
				{
					loading.remove(type);
				}
			});
		}
	}

	private void resetTranslations()
	{
		loading.clear();
		store.clear();
		menuTranslator.reset();
		reloadEnabledTranslations();
	}

	private void updateCollectionTask()
	{
		if (!config.enableTextCapture())
		{
			if (collectionTask != null)
			{
				collectionTask.cancel(false);
				collectionTask = null;
			}
			return;
		}

		if (collectionTask == null)
		{
			collectionTask = collector.scheduleAtFixedRate(
				this::sendCollectedTexts,
				DATA_COLLECTION_PERIOD_MINUTES,
				DATA_COLLECTION_PERIOD_MINUTES,
				TimeUnit.MINUTES
			);
		}
	}

	private void sendCollectedTexts()
	{
		try
		{
			api.flushCollectedTexts();
		}
		catch (Exception e)
		{
			log.warn("failed to send collected texts", e);
		}
	}

	private void runAsync(Runnable task)
	{
		ExecutorService executor = loader;
		if (executor == null)
		{
			return;
		}

		try
		{
			executor.execute(task);
		}
		catch (RejectedExecutionException e)
		{
			log.debug("dropping task, the plugin is shutting down");
		}
	}

	private static ThreadFactory daemonThreads(String prefix)
	{
		AtomicInteger counter = new AtomicInteger();
		return runnable ->
		{
			Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}
}
