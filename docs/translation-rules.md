# Translation rules

These rules are the contract for every reviewed translation: contributors, maintainers and
the automated first pass all apply the same checklist. `InterfaceDictionaryTest` enforces the
mechanical parts (tags, numbers, placeholders, duplicates).

## Words that stay in English

| Category | Examples |
| --- | --- |
| NPCs and bosses | `Duke Horacio`, `Redbeard Frank`, `Zulrah`, `Araxxor`, `Vorkath` |
| Items | `Mackerel`, `Flax`, `Jute seeds`, `Rune Hasta`, `Abyssal whip` |
| Places | `East Ardougne`, `Port Sarim`, `Catherby`, `Varrock Museum`, `Gu'Tanoth` |
| Quest names | `Biohazard`, `Plague City`, `Cook's Assistant`, `Bone Voyage` |
| Storylines | `Elf #2`, `Pirate #2`, `Order of Wizards #1` |
| Skills | `Ranged`, `Cooking`, `Hunter`, `Agility`, `Farming`, `Herblore` |

Rule of thumb: if the word is a proper name in game (capitalised and referring to a unique
entity), it is not translated.

**Item names stay in English even inside a sentence.** A diary task says `Buy a candle from
the Chandler`, not `Compra una vela`: `candle` is an item, exactly like `flax`, `coal`, `egg`,
`flour`, `milk`, `cake`, `tea`, `stew`, `bass`, `mackerel`, `shark`, `Jute seeds`,
`Limpwurt root`, `Maple logs`, `Stamina Mix`... The surrounding words are translated; the item
noun is not.

Examples:

| English | Spanish |
| --- | --- |
| `I brought the cook an egg, some flour and some milk` | `Le llevé al cocinero un egg, algo de flour y algo de milk` |
| `Buy a candle from the Chandler in Catherby.` | `Compra un candle al Chandler de Catherby.` |
| `Mine some coal near the coal trucks.` | `Mina algo de coal cerca de los coal trucks.` |
| `Buy a Stew from the Seers' pub.` | `Compra un Stew en el pub de los Seers.` |

## Words that are translated

* descriptive sentences (`I can start this quest by talking to ...` -> `Puedo empezar esta misión hablando con ...`);
* interface labels: `Difficulty:` -> `Dificultad:`, `Length:` -> `Duración:`,
  `Storyline:` -> `Trama:`, `Release Year:` -> `Año de lanzamiento:`, `Requirements:` ->
  `Requisitos:`, `Recommendations:` -> `Recomendaciones:`, `Show on Map` -> `Mostrar en el mapa`;
* tiers: `Easy/Medium/Hard/Elite`, `Novice/Intermediate/Experienced/Master/Grandmaster`,
  `Short/Long/Very Short/Very Long`;
* generic words in headings: `Tasks` -> `Tareas`.
* generic nouns: `quest` -> `misión`, `miniquest` -> `minimisión`, `task` -> `tarea`.
  The **name** of a quest or miniquest stays in English (`Bone Voyage`), the generic word does
  not: `I can start this miniquest by talking to ...` -> `Puedo empezar esta minimisión
  hablando con ...`.

## Structure (enforced by tests)

1. Every `<col=...>`, `</col>`, `<br>`, `<str>` tag appears **exactly the same, in the same
   order**. A wrong tag breaks the colours on screen.
2. Keep every number that appears in the original.
3. `re:` entries keep their `$1`, `$2` placeholders and must not add new groups.
4. A paragraph split across several widgets is translated **line by line**, without adding or
   removing line breaks. Example (Cook's Assistant journal):

   ```
   <str>It was the Duke of Lumbridge's birthday, but his cook had
   <str>forgotten to buy the ingredients he needed to make him a
   ```

5. Keys are exact: leading/trailing spaces, punctuation and capitalisation included.

## Style

* Neutral Spanish; avoid regionalisms where possible.
* Diary tasks use imperative: `Pesca`, `Compra`, `Recoge`, `Planta`, `Mata`.
* Keep sentences short: the interface wraps at a fixed width.
* Do not add information that is not in the original.

## Automated first pass (prompt template)

```
You translate Old School RuneScape interface texts from English to <language>.

Rules:
- Keep NPCs, bosses, items, places, quest names, storylines and skill names in English.
- Translate only the descriptive text and interface labels.
- Copy every <col=...>, </col>, <br> and <str> tag exactly, in the same order.
- Keep all numbers.
- Do not add or remove line breaks.
- Neutral, short, imperative for tasks.

Reply with one line per input, in the same order, as: <translation>
Do not include the original text, numbering or explanations.

Input:
<one english text per line>
```

The output is reviewed before merging; it is never committed automatically.

## Review checklist

- [ ] names and skills untouched
- [ ] tags identical
- [ ] numbers identical
- [ ] no new/removed line breaks
- [ ] reads naturally in Spanish
- [ ] `./gradlew build` passes
