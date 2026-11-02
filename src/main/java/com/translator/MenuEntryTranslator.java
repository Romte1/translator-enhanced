package com.translator;

import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Translates right click menu entries.
 *
 * <p>Two independent things can be translated, each with its own toggle:</p>
 * <ul>
 *   <li>the option (verb): {@code Talk-to} -&gt; {@code Hablar con}</li>
 *   <li>the target (name): {@code Goblin} -&gt; {@code Duende}</li>
 * </ul>
 *
 * <p>Entries are only modified right before the menu is rendered, never when they are added.
 * Other plugins (Menu Entry Swapper is the classic example) decide what to swap and in which
 * order by reading the option and target text, so translating earlier makes their text
 * matching fail and the menu ends up in the wrong order.</p>
 *
 * <p>Names are resolved against the downloaded dictionaries by their english name, never by
 * id: the dictionaries are indexed by name.</p>
 */
class MenuEntryTranslator
{
	/**
	 * Interface groups whose menu entries are item ops even when the client does not report an
	 * item id (worn equipment).
	 */
	private static final int EQUIPMENT_GROUP = InterfaceID.Equipment.UNIVERSE >>> 16;
	private static final int EQUIPMENT_SIDE_GROUP = InterfaceID.EquipmentSide.ITEMS >>> 16;

	private final TranslatorConfig config;
	private final TranslationStore store;
	private final TranslatorAPI api;

	/**
	 * Cached result of {@link TranslationSettings#anyMenuTranslationEnabled()}, updated on
	 * configuration changes, so the hot path (every menu entry of every frame) is a single
	 * field read when menus are not translated at all.
	 */
	private volatile boolean enabled;

	/**
	 * Text this plugin already applied to each entry, by object identity.
	 *
	 * <p>The menu is not rebuilt on every frame, so the same {@link MenuEntry} objects are
	 * rendered again and again. Without this, a translated text would be translated a second
	 * time on the next frame and, because a few upstream entries are indexed by an already
	 * translated string, {@code Use} turned into {@code Usar} and then into {@code usar}.</p>
	 */
	private Map<MenuEntry, String[]> appliedTexts = new IdentityHashMap<>();

	@Inject
	MenuEntryTranslator(TranslatorConfig config, TranslationStore store, TranslatorAPI api)
	{
		this.config = config;
		this.store = store;
		this.api = api;
	}

	void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	/**
	 * Forgets what was applied, eg. after a language change or a reload.
	 */
	void reset()
	{
		appliedTexts.clear();
	}

	/**
	 * Translates every entry of the menu about to be rendered, at most once per entry state.
	 */
	void translateMenu(MenuEntry[] entries)
	{
		if (!enabled || entries == null || entries.length == 0)
		{
			if (!appliedTexts.isEmpty())
			{
				appliedTexts.clear();
			}
			return;
		}

		Map<MenuEntry, String[]> previous = appliedTexts;
		Map<MenuEntry, String[]> current = new IdentityHashMap<>(entries.length);

		for (MenuEntry entry : entries)
		{
			if (entry == null)
			{
				continue;
			}

			String option = entry.getOption();
			String target = entry.getTarget();
			String[] applied = previous.get(entry);

			// translate only when the entry is new or its text changed since we touched it
			if (applied == null || !equals(applied[0], option) || !equals(applied[1], target))
			{
				translateOption(entry);
				translateTarget(entry);
			}

			current.put(entry, new String[]{entry.getOption(), entry.getTarget()});
		}

		// replacing the map also drops entries that are no longer in the menu
		appliedTexts = current;
	}

	private static boolean equals(String a, String b)
	{
		return a == null ? b == null : a.equals(b);
	}

	/**
	 * Queues the english option for the opt-in data collection. Called when the entry is
	 * added, so the collected text is always the untranslated one.
	 */
	void collect(MenuEntry entry)
	{
		if (entry == null || !config.enableTextCapture())
		{
			return;
		}

		String option = entry.getOption();
		if (option != null && !option.isEmpty())
		{
			api.collect(TranslationType.MENU_OPTION, option);
		}
	}

	private void translateOption(MenuEntry entry)
	{
		if (!config.translateMenuOptions())
		{
			return;
		}

		String option = entry.getOption();
		if (option == null || option.isEmpty())
		{
			return;
		}

		String translated = store.lookup(TranslationType.MENU_OPTION, option);
		if (translated == null || translated.equals(option))
		{
			return;
		}

		entry.setOption(translated);
	}

	private void translateTarget(MenuEntry entry)
	{
		TranslationType type = typeFor(entry);
		if (type == null || !isEnabled(type))
		{
			return;
		}

		MenuTarget target = MenuTarget.parse(entry.getTarget());
		if (target == null)
		{
			return;
		}

		// always try the full name first: "Clue scroll (easy)" is a name, not a suffix
		String translated = lookup(type, target.getName());
		if (translated != null && !translated.equals(target.getName()))
		{
			entry.setTarget(target.withName(translated));
			return;
		}

		// fall back to the name without its "(2)" / "(level-2)" suffix
		String core = target.getCoreName();
		if (core == null)
		{
			return;
		}

		translated = lookup(type, core);
		if (translated == null || translated.equals(core))
		{
			return;
		}

		entry.setTarget(target.withName(translated + target.getCoreSuffix()));
	}

	/**
	 * Resolves a name, falling back to the sibling dictionary: a few item ops are reported as
	 * object ops and vice versa.
	 */
	private String lookup(TranslationType type, String name)
	{
		String translated = store.lookup(type, name);
		if (translated != null)
		{
			return translated;
		}

		TranslationType alternative = type == TranslationType.ITEM ? TranslationType.OBJECT
			: type == TranslationType.OBJECT ? TranslationType.ITEM
			: null;

		return alternative == null || !isEnabled(alternative) ? null : store.lookup(alternative, name);
	}

	/**
	 * Classifies an entry to know which dictionary to use. Returns {@code null} for entries
	 * that do not have a translatable target (players, widgets, RuneLite's own entries...).
	 */
	private static TranslationType typeFor(MenuEntry entry)
	{
		if (entry.getNpc() != null)
		{
			return TranslationType.NPC;
		}

		// player names must never be translated
		if (entry.getPlayer() != null)
		{
			return null;
		}

		if (entry.getItemId() > 0)
		{
			return TranslationType.ITEM;
		}

		MenuAction action = entry.getType();
		if (isGroundItemAction(action))
		{
			return TranslationType.ITEM;
		}

		Widget widget = entry.getWidget();
		if (widget != null)
		{
			// worn equipment: item ops on a widget, often without an item id
			return isEquipmentWidget(widget.getId()) ? TranslationType.ITEM : null;
		}

		if (isIgnoredAction(action))
		{
			return null;
		}

		return entry.getIdentifier() > 0 ? TranslationType.OBJECT : null;
	}

	private static boolean isEquipmentWidget(int widgetId)
	{
		int group = widgetId >>> 16;
		return group == EQUIPMENT_GROUP || group == EQUIPMENT_SIDE_GROUP;
	}

	private static boolean isGroundItemAction(MenuAction action)
	{
		if (action == null)
		{
			return false;
		}

		switch (action)
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
			case ITEM_USE_ON_GROUND_ITEM:
			case WIDGET_TARGET_ON_GROUND_ITEM:
			case EXAMINE_ITEM_GROUND:
				return true;
			default:
				return false;
		}
	}

	/**
	 * Actions whose identifier is not a game object id.
	 */
	private static boolean isIgnoredAction(MenuAction action)
	{
		if (action == null)
		{
			return true;
		}

		switch (action)
		{
			case WALK:
			case CC_OP:
			case CC_OP_LOW_PRIORITY:
			case RUNELITE:
			case RUNELITE_HIGH_PRIORITY:
			case RUNELITE_LOW_PRIORITY:
			case RUNELITE_WIDGET:
			case RUNELITE_OVERLAY:
			case RUNELITE_OVERLAY_CONFIG:
			case RUNELITE_PLAYER:
			case RUNELITE_INFOBOX:
				return true;
			default:
				return false;
		}
	}

	private boolean isEnabled(TranslationType type)
	{
		switch (type)
		{
			case NPC:
				return config.translateNpcNames();
			case ITEM:
				return config.translateItemNames();
			case OBJECT:
				return config.translateObjectNames();
			default:
				return false;
		}
	}
}
