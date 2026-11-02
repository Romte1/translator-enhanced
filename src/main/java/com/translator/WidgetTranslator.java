package com.translator;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

class WidgetTranslator
{
	private static final Logger log = LoggerFactory.getLogger(WidgetTranslator.class);

	private static final int LINE_HEIGHT = 20;
	private static final int DIARY_MAX_LINE_LENGTH = 60;
	private static final int MAX_WIDGET_DEPTH = 12;
	private static final int QUEST_INTERFACE_POLL_TICKS = 3;

	private static final int[] QUEST_INTERFACES =
	{
		InterfaceID.Questjournal.UNIVERSE,
		InterfaceID.QuestjournalOverview.UNIVERSE,
		InterfaceID.Questdisplay.UNIVERSE,
		InterfaceID.Questscroll.UNIVERSE,
		InterfaceID.Journalscroll.UNIVERSE,
		InterfaceID.AreaTask.UNIVERSE,
		InterfaceID.CaTasks.UNIVERSE,
	};

	private static final Set<Integer> TITLE_WIDGETS = new HashSet<>(Arrays.asList(
		InterfaceID.Questjournal.TITLE,
		InterfaceID.QuestjournalOverview.TITLE,
		InterfaceID.Questdisplay.TITLE,
		InterfaceID.Journalscroll.TITLE,
		InterfaceID.AreaTask.HEADER));

	private static final String ANNOTATION_PREFIX = "<col=ffffff>(";

	private final Client client;
	private final TranslationStore store;
	private final TranslatorConfig config;
	private final TranslatorAPI api;
	private final InterfaceTextCapture interfaceCapture;

	private int ticks;

	@Inject
	WidgetTranslator(Client client, TranslationStore store, TranslatorConfig config,
		TranslatorAPI api, InterfaceTextCapture interfaceCapture)
	{
		this.client = client;
		this.store = store;
		this.config = config;
		this.api = api;
		this.interfaceCapture = interfaceCapture;
	}

	void onGameTick()
	{
		translateDialogue();
		translateDialogueOptions();

		if (++ticks % QUEST_INTERFACE_POLL_TICKS == 0)
		{
			translateQuestInterfaces();
		}
	}

	void translateQuestInterfaces()
	{
		if (!config.translateQuestGuide())
		{
			return;
		}

		for (int root : QUEST_INTERFACES)
		{
			Widget widget = client.getWidget(root);
			if (widget != null)
			{
				translateRecursively(widget, 0, Collections.newSetFromMap(new IdentityHashMap<Widget, Boolean>()));
			}
		}
	}

	private void translateRecursively(Widget widget, int depth, Set<Widget> visited)
	{
		if (widget == null || depth > MAX_WIDGET_DEPTH || !visited.add(widget))
		{
			return;
		}

		if (widget.getType() == WidgetType.TEXT && !TITLE_WIDGETS.contains(widget.getId()))
		{
			translateWidgetText(widget);
		}

		translateChildren(widget.getChildren(), depth, visited);
		translateChildren(widget.getDynamicChildren(), depth, visited);
		translateChildren(widget.getNestedChildren(), depth, visited);
		translateChildren(widget.getStaticChildren(), depth, visited);
	}

	private void translateChildren(Widget[] children, int depth, Set<Widget> visited)
	{
		if (children == null)
		{
			return;
		}

		for (Widget child : children)
		{
			translateRecursively(child, depth + 1, visited);
		}
	}

	private void translateWidgetText(Widget widget)
	{
		String text = widget.getText();
		if (text == null || text.isEmpty())
		{
			return;
		}

		String source = text;
		String translated = store.lookup(TranslationType.INTERFACE, text);

		if (translated == null)
		{
			String original = store.lookupOriginal(TranslationType.INTERFACE, text);
			if (original == null)
			{
				api.collect(TranslationType.INTERFACE, text);

				if (config.captureInterfaceTexts())
				{
					interfaceCapture.capture(config.selectLanguage(), text);
				}
				return;
			}

			source = original;
			translated = store.lookup(TranslationType.INTERFACE, original);
		}

		if (translated == null || translated.equals(text)
			|| TranslationStore.signature(translated).equals(TranslationStore.signature(text)))
		{
			return;
		}

		translate(widget, source, translated);
	}

	private void translate(Widget widget, String source, String translated)
	{
		int originalAnnotation = source.indexOf(ANNOTATION_PREFIX);
		if (originalAnnotation > 0 && !translated.contains(ANNOTATION_PREFIX))
		{
			translated = translated + source.substring(originalAnnotation);
		}

		int annotation = translated.indexOf(ANNOTATION_PREFIX);
		if (annotation > 0 && WidgetReflow.visibleLength(translated) > DIARY_MAX_LINE_LENGTH)
		{
			String requirement = translated.substring(annotation);
			List<String> lines = WidgetReflow.wrap(requirement, DIARY_MAX_LINE_LENGTH);
			translated = translated.substring(0, annotation) + "<br>" + String.join("<br>", lines);
		}

		widget.setText(translated);
		relayout(widget);
	}

	private void translateDialogue()
	{
		if (!config.translateDialogues())
		{
			return;
		}

		Widget widget = client.getWidget(ComponentID.DIALOG_NPC_TEXT);
		if (widget == null)
		{
			widget = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
		}

		if (widget == null)
		{
			return;
		}

		String text = widget.getText();
		if (text == null || text.isEmpty())
		{
			return;
		}

		String playerName = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
		String key = text.replace("<br>", " ");
		if (playerName != null && !playerName.isEmpty())
		{
			key = key.replace(playerName, "[PLAYER_NAME]");
		}

		String slayerTask = "";
		if (key.startsWith("Your new task is to kill"))
		{
			slayerTask = key.replace("Your new task is to kill ", "");
			key = "Your new task is to kill [SLAYER_TASK_AMT_AND_MONSTER]";
		}

		String translated = store.lookup(TranslationType.DIALOGUE, key);
		if (translated == null)
		{
			api.collect(TranslationType.DIALOGUE, key);
			return;
		}

		if (playerName != null && !playerName.isEmpty())
		{
			translated = translated.replace("[PLAYER_NAME]", playerName);
		}
		if (!slayerTask.isEmpty())
		{
			translated = translated.replace("[SLAYER_TASK_AMT_AND_MONSTER]", slayerTask);
		}

		if (!translated.equals(text))
		{
			widget.setText(translated);
		}
	}

	private void translateDialogueOptions()
	{
		if (!config.translateDialogueOptions())
		{
			return;
		}

		Widget options = client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS);
		if (options == null || options.getChildren() == null)
		{
			return;
		}

		for (Widget child : options.getChildren())
		{
			if (child == null)
			{
				continue;
			}

			String text = child.getText();
			if (text == null || text.isEmpty())
			{
				continue;
			}

			String key = text.replace("<br>", " ").trim();
			String translated = store.lookup(TranslationType.DIALOGUE, key);
			if (translated == null)
			{
				api.collect(TranslationType.DIALOGUE, key);
			}
			else if (!translated.equals(text))
			{
				child.setText(translated);
			}
		}
	}

	private void relayout(Widget widget)
	{
		Widget parent = findParent(client.getWidget(InterfaceID.Journalscroll.UNIVERSE), widget, 0);

		if (parent == null)
		{
			parent = findParent(client.getWidget(InterfaceID.AreaTask.UNIVERSE), widget, 0);
		}

		if (parent == null)
		{
			return;
		}

		List<Widget> children = new ArrayList<>();
		Set<Widget> seen = Collections.newSetFromMap(new IdentityHashMap<Widget, Boolean>());
		collectChildren(children, seen, parent.getChildren());
		collectChildren(children, seen, parent.getDynamicChildren());
		collectChildren(children, seen, parent.getNestedChildren());
		collectChildren(children, seen, parent.getStaticChildren());

		if (children.isEmpty())
		{
			return;
		}

		int y = 0;
		boolean previousWrapped = false;

		for (Widget child : children)
		{
			String text = child.getText();

			if (text != null && !text.contains("<br>"))
			{
				int annotation = text.indexOf(ANNOTATION_PREFIX);
				if (annotation > 0 && WidgetReflow.visibleLength(text) > DIARY_MAX_LINE_LENGTH)
				{
					text = text.substring(0, annotation) + "<br>" + text.substring(annotation);
					child.setText(text);
				}
			}

			int lines = countLines(text);
			boolean empty = text == null || text.trim().isEmpty();
			int height = empty && previousWrapped ? 0 : lines * LINE_HEIGHT;

			child.setOriginalHeight(height);
			child.setOriginalY(y);
			child.revalidate();

			y += height;

			if (!empty)
			{
				previousWrapped = lines > 1;
			}
		}

		parent.setScrollHeight(Math.max(parent.getHeight(), y));
		parent.revalidate();
		parent.revalidateScroll();
	}

	private static int countLines(String text)
	{
		if (text == null)
		{
			return 1;
		}

		int lines = 1;
		int index = 0;

		while ((index = text.indexOf("<br>", index)) >= 0)
		{
			lines++;
			index += 4;
		}

		return lines;
	}

	private static void collectChildren(List<Widget> target, Set<Widget> seen, Widget[] array)
	{
		if (array == null)
		{
			return;
		}

		for (Widget child : array)
		{
			if (child != null && seen.add(child))
			{
				target.add(child);
			}
		}
	}

	private static Widget findParent(Widget node, Widget target, int depth)
	{
		if (node == null || depth > MAX_WIDGET_DEPTH)
		{
			return null;
		}

		Widget[][] arrays = {node.getChildren(), node.getDynamicChildren(),
			node.getNestedChildren(), node.getStaticChildren()};

		for (Widget[] array : arrays)
		{
			if (array == null)
			{
				continue;
			}

			for (Widget child : array)
			{
				if (child == target)
				{
					return node;
				}
			}

			for (Widget child : array)
			{
				Widget found = findParent(child, target, depth + 1);
				if (found != null)
				{
					return found;
				}
			}
		}

		return null;
	}
}
