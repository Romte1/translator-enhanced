package com.translator;

import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class WidgetTranslator
{
	private static final Logger log = LoggerFactory.getLogger(WidgetTranslator.class);

	private static final int LINE_HEIGHT = 20;
	private static final int MAX_WIDGET_DEPTH = 12;
	private static final int QUEST_INTERFACE_POLL_TICKS = 1;
	private static final int JOURNAL_SCROLL_GROUP = InterfaceID.Journalscroll.UNIVERSE >>> 16;
	private static final Pattern LEADING_TAGS = Pattern.compile("^(?:<[^>]+>)+");
	private static final Pattern TRAILING_TAGS = Pattern.compile("(?:<[^>]+>)+$");
	private static final Pattern COLOUR_TAG = Pattern.compile("<col=[^>]+>");

	private int lastLayoutHeight = -1;

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
	private final PluginManager pluginManager;

	private int ticks;
	private long lastCaScan;
	private long lastFrameScan;

	@Inject
	WidgetTranslator(Client client, TranslationStore store, TranslatorConfig config,
		TranslatorAPI api, InterfaceTextCapture interfaceCapture, PluginManager pluginManager)
	{
		this.client = client;
		this.store = store;
		this.config = config;
		this.api = api;
		this.interfaceCapture = interfaceCapture;
		this.pluginManager = pluginManager;
	}

	void onScriptPostFired(int scriptId)
	{
		translateDialogue();
		translateDialogueOptions();

		if (isJournalScript(scriptId))
		{
			translateQuestInterfaces();

			if (scriptId == ScriptID.DIARY_UPDATE_LINECOUNT)
			{
				refreshJournalLayout();
			}
		}

		translateCaTasksThrottled();
	}

	void onVariableChanged()
	{
		translateCaTasksThrottled();
	}

	void onBeforeRender()
	{
		if (!hasVisibleJournal())
		{
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastFrameScan < 16)
		{
			return;
		}

		lastFrameScan = now;
		translateQuestInterfaces();
	}

	private boolean hasVisibleJournal()
	{
		for (int root : QUEST_INTERFACES)
		{
			Widget widget = client.getWidget(root);

			if (widget != null && !widget.isHidden())
			{
				return true;
			}
		}

		return false;
	}

	private void translateCaTasksThrottled()
	{
		if (client.getWidget(InterfaceID.CaTasks.UNIVERSE) == null)
		{
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastCaScan < 50)
		{
			return;
		}

		lastCaScan = now;
		translateCaTasks();
	}

	private void translateCaTasks()
	{
		Widget root = client.getWidget(InterfaceID.CaTasks.UNIVERSE);
		if (root != null)
		{
			translateRecursively(root, 0, Collections.newSetFromMap(new IdentityHashMap<Widget, Boolean>()));
		}
	}

	static boolean isJournalScript(int scriptId)
	{
		return scriptId == ScriptID.DIARY_UPDATE_LINECOUNT || scriptId == ScriptID.QUEST_UPDATE_LINECOUNT;
	}

	void onWidgetLoaded()
	{
		translateDialogue();
		translateDialogueOptions();
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
		String translated = text.contains(ANNOTATION_PREFIX)
			? null
			: store.lookupAnnotated(TranslationType.INTERFACE, text);

		if (translated == null)
		{
			translated = store.lookup(TranslationType.INTERFACE, text);
		}

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

		translated = preserveStrike(widget.getText(), source, translated);

		int annotation = translated.indexOf(ANNOTATION_PREFIX);
		int budget = diaryBudget(widget, null);
		if (annotation > 0 && WidgetReflow.visibleLength(translated) > budget)
		{
			String requirement = translated.substring(annotation);
			List<String> lines = WidgetReflow.wrap(requirement, budget);
			translated = translated.substring(0, annotation) + "<br>" + String.join("<br>", lines);
		}

		boolean struck = isStruck(widget.getText());
		widget.setText(translated);
		relayout(widget, struck);
	}

	static boolean isStruck(String text)
	{
		if (text == null)
		{
			return false;
		}

		int open = text.indexOf("<str>");
		return open >= 0 && WidgetReflow.visibleLength(text.substring(0, open)) == 0;
	}

	static String preserveStrike(String current, String source, String translated)
	{
		if (translated == null)
		{
			return null;
		}

		String original = current != null && !current.isEmpty() ? current : source;
		if (original == null || !original.contains("<str>"))
		{
			return translated;
		}

		int open = original.indexOf("<str>");
		boolean struck = open >= 0
			&& WidgetReflow.visibleLength(original.substring(0, open)) == 0;

		if (!struck)
		{
			return translated;
		}

		int annotation = translated.indexOf(ANNOTATION_PREFIX);
		String translatedMain = annotation > 0 ? translated.substring(0, annotation) : translated;
		if (translatedMain.contains("<str>"))
		{
			return translated;
		}

		String result;
		if (annotation > 0)
		{
			result = wrapStrike(translatedMain) + strikeAllColours(translated.substring(annotation));
		}
		else
		{
			result = wrapStrike(translated);
		}

		return result;
	}

	// The client resets <str> at line breaks and colour changes.
	static String strikeLines(String text)
	{
		StringBuilder result = new StringBuilder();
		String[] lines = text.split("<br>", -1);
		String colour = "";

		for (int i = 0; i < lines.length; i++)
		{
			if (i > 0)
			{
				result.append("<br>");
			}

			String line = lines[i].replace("<str>", "").replace("</str>", "");
			if (!line.startsWith("<col=") && !colour.isEmpty())
			{
				line = colour + line;
			}

			Matcher matcher = COLOUR_TAG.matcher(line);
			if (matcher.find())
			{
				colour = matcher.group();
			}

			if (WidgetReflow.visibleText(line).isEmpty())
			{
				result.append(line);
			}
			else if (line.contains("<col="))
			{
				result.append(strikeAllColours(line));
			}
			else
			{
				result.append(wrapStrike(line));
			}
		}

		return result.toString();
	}

	private static String wrapStrike(String text)
	{
		Matcher leading = LEADING_TAGS.matcher(text);
		String prefix = leading.find() ? leading.group() : "";
		String rest = text.substring(prefix.length());

		Matcher trailing = TRAILING_TAGS.matcher(rest);
		String suffix = trailing.find() ? trailing.group() : "";
		String middle = rest.substring(0, rest.length() - suffix.length());

		return prefix + "<str>" + middle + "</str>" + suffix;
	}

	static String strikeAllColours(String text)
	{
		String plain = text.replace("<str>", "").replace("</str>", "");
		Matcher first = COLOUR_TAG.matcher(plain);
		if (!first.find())
		{
			return wrapStrike(plain);
		}

		StringBuilder result = new StringBuilder();
		int firstStart = first.start();
		String leading = plain.substring(0, firstStart);
		if (WidgetReflow.visibleText(leading).isEmpty())
		{
			result.append(leading);
		}
		else
		{
			result.append("<str>").append(leading).append("</str>");
		}

		Matcher colours = COLOUR_TAG.matcher(plain);
		int last = firstStart;

		while (colours.find())
		{
			result.append(plain, last, colours.end());
			int start = colours.end();
			Matcher next = COLOUR_TAG.matcher(plain);
			next.region(start, plain.length());
			int end = next.find() ? next.start() : plain.length();
			String segment = plain.substring(start, end);

			if (WidgetReflow.visibleText(segment).isEmpty())
			{
				result.append(segment);
			}
			else
			{
				result.append("<str>").append(segment).append("</str>");
			}

			last = end;
		}

		result.append(plain.substring(last));
		return result.toString();
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

		if (store.lookupOriginal(TranslationType.DIALOGUE, key) != null)
		{
			return;
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
			log.debug("dialogue: [{}] -> [{}]", text, translated);
			widget.setText(translated);
		}
	}

	private void translateDialogueOptions()
	{
		if (!config.translateDialogueOptions())
		{
			return;
		}

		if (config.questHelperIntegration() && questHelperActive())
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
			if (store.lookupOriginal(TranslationType.DIALOGUE, key) != null)
			{
				continue;
			}

			String translated = store.lookup(TranslationType.DIALOGUE, key);
			if (translated == null)
			{
				api.collect(TranslationType.DIALOGUE, key);
			}
			else if (!translated.equals(text))
			{
				log.debug("dialogue option: [{}] -> [{}]", text, translated);
				child.setText(translated);
			}
		}
	}

	private boolean questHelperActive()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (isQuestHelperClass(plugin.getClass().getName()) && pluginManager.isPluginActive(plugin))
			{
				return true;
			}
		}

		return false;
	}

	static boolean isQuestHelperClass(String className)
	{
		return className.startsWith("com.questhelper.");
	}

	private void relayout(Widget widget, boolean struck)
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

		relayout(parent, widget.getId(), struck);
	}

	// The built-in Diary Requirements plugin resets the scroll height after our layout.
	private void refreshJournalLayout()
	{
		Widget parent = client.getWidget(InterfaceID.Journalscroll.TEXTLAYER);

		if (parent == null || lastLayoutHeight <= 0)
		{
			return;
		}

		int expectedHeight = Math.max(parent.getHeight(), lastLayoutHeight + LINE_HEIGHT);
		if (parent.getScrollHeight() != expectedHeight)
		{
			relayout(parent, -1, false);
		}
	}

	private void relayout(Widget parent, int targetId, boolean struck)
	{
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
		boolean previousEmbedded = false;
		Set<String> tiers = tierLabels();

		for (Widget child : children)
		{
			String text = child.getText();
			boolean empty = text == null || text.trim().isEmpty();
			boolean requirement = text != null && text.contains(ANNOTATION_PREFIX);

			if (previousEmbedded && requirement)
			{
				child.setOriginalHeight(0);
				child.setOriginalY(y);
				child.revalidate();
				previousEmbedded = false;
				continue;
			}

			if (empty)
			{
				child.setOriginalHeight(0);
				child.setOriginalY(y);
				child.revalidate();
				continue;
			}

			int budget = diaryBudget(child, parent);
			List<String> wrapped = new ArrayList<>();
			for (String segment : text.split("<br>", -1))
			{
				wrapped.addAll(WidgetReflow.wrap(segment, budget));
			}

			String joined = String.join("<br>", wrapped);
			if (!joined.equals(text))
			{
				text = joined;
				child.setText(text);
			}

			if (struck && child.getId() == targetId)
			{
				text = strikeLines(text);
				child.setText(text);
			}

			int lines = countLines(text);
			boolean header = tiers.contains(WidgetReflow.visibleText(text));
			int paddingTop = header ? 12 : 0;
			int paddingBottom = header ? 2 : 0;

			y += paddingTop;

			int height = lines * LINE_HEIGHT;
			child.setOriginalHeight(height);
			child.setOriginalY(y);
			child.revalidate();

			y += height + paddingBottom;
			previousEmbedded = requirement;
		}

		int contentHeight = Math.max(parent.getHeight(), y + LINE_HEIGHT);
		parent.setScrollHeight(contentHeight);
		parent.revalidate();
		parent.revalidateScroll();
		updateScrollbar(parent);
		lastLayoutHeight = y;
	}

	private void updateScrollbar(Widget list)
	{
		if (list == null || (list.getId() >>> 16) != JOURNAL_SCROLL_GROUP)
		{
			return;
		}

		Widget scrollbar = client.getWidget(InterfaceID.Journalscroll.SCROLLBAR);
		// Child 1 is the scrollbar handle created by the client.
		Widget handle = scrollbar == null ? null : scrollbar.getChild(1);

		if (handle == null)
		{
			return;
		}

		int scrollHeight = Math.max(1, list.getScrollHeight());
		int handleHeight = Math.max(10, (scrollbar.getHeight() - 32) * list.getHeight() / scrollHeight);
		handle.setOriginalHeight(handleHeight);
		handle.revalidate();
	}

	private Set<String> tierLabels()
	{
		Set<String> labels = new HashSet<>();

		for (String key : new String[]{"Easy", "Medium", "Hard", "Elite"})
		{
			labels.add(key);

			String value = store.lookup(TranslationType.INTERFACE, key);
			if (value != null)
			{
				labels.add(WidgetReflow.visibleText(value));
			}
		}

		return labels;
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

	private static int diaryBudget(Widget widget, Widget parent)
	{
		int width = widget.getWidth() > 0 ? widget.getWidth() : parent == null ? 0 : parent.getWidth();
		return width > 0 ? Math.max(30, Math.min(53, width / 8)) : 53;
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
