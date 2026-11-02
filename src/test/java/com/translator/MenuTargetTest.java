package com.translator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MenuTargetTest
{
	@Test
	public void parsesNpcWithCombatLevel()
	{
		MenuTarget target = MenuTarget.parse("<col=ffff00>Goblin<col=ff0000> (level-2)</col>");

		assertEquals("Goblin", target.getName());
		assertEquals("<col=ffff00>Duende<col=ff0000> (level-2)</col>", target.withName("Duende"));
	}

	@Test
	public void parsesObjectWithColourTag()
	{
		MenuTarget target = MenuTarget.parse("<col=00ffff>Tree</col>");

		assertEquals("Tree", target.getName());
		assertEquals("<col=00ffff>Árbol</col>", target.withName("Árbol"));
	}

	@Test
	public void keepsStackedAmount()
	{
		MenuTarget target = MenuTarget.parse("Shark (2)");

		assertEquals("Shark (2)", target.getName());
		assertEquals("Shark", target.getCoreName());
		assertEquals(" (2)", target.getCoreSuffix());
	}

	@Test
	public void keepsParenthesesThatBelongToTheName()
	{
		MenuTarget target = MenuTarget.parse("Clue scroll (easy)");

		assertEquals("Clue scroll (easy)", target.getName());
	}

	@Test
	public void parsesPlainTarget()
	{
		MenuTarget target = MenuTarget.parse("Bank booth");

		assertEquals("Bank booth", target.getName());
		assertEquals("Mostrador de banco", target.withName("Mostrador de banco"));
	}

	@Test
	public void returnsNullWhenThereIsNothingToTranslate()
	{
		assertNull(MenuTarget.parse(null));
		assertNull(MenuTarget.parse(""));
		assertNull(MenuTarget.parse("<col=ffff00>"));
	}
}
