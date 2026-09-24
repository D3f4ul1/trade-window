# Porting Notes — 1.21.1 through 26.3

Fourteen targets, nine API surfaces, one item-movement algorithm.

This document records what actually differs between the supported Minecraft
versions, what those differences forced, and how to add a sixteenth target. Every
API claim below was verified against real artefacts — the unobfuscated 26.x
server and client jars from Mojang, the obfuscated jars as remapped by Loom, and
the Fabric API module jars for each version — rather than recalled from memory.

---

## 1. The headline facts

| | 1.21.1 – 1.21.11 | 26.1 – 26.3 |
|---|---|---|
| Obfuscated? | **Yes** | **No** — ships readable Mojang names |
| Mappings | required (official Mojang) | **none exist** |
| Java | 21 | 25 |
| Loom plugin id | `net.fabricmc.fabric-loom-remap` | `net.fabricmc.fabric-loom` |
| Dependency scope | `modImplementation` | `implementation` |
| Remap step | `remapJar` | plain `jar` |
| Resource id class | `ResourceLocation` | `Identifier` |
| GUI render context | `GuiGraphics` | `GuiGraphicsExtractor` |
| Item data | data components | data components |
| Networking | typed payloads | typed payloads |

**The hard evidence for the obfuscation boundary.** Querying Fabric's metadata for
Yarn returns a build for every version from 1.21.1 to 1.21.11, and an
**empty list** for 26.1, 26.2 and 26.3. Mojang's own version manifest agrees:
26.3 has no `server_mappings` and no `client_mappings` entry at all. There is
nothing to map because nothing is obfuscated.

That is also why the 26.x modules must **not** carry a `mappings` line: it is not
a harmless no-op, it is an error.

### Corrections to the versions supplied in the brief

The brief's version table was partly stale. These are the versions that actually
resolve, and the ones the build matrix uses:

| Target | Brief said | Actually published |
|---|---|---|
| 1.21.5 | `0.119.6+1.21.5` | `0.128.2+1.21.5` |
| 1.21.10 | `0.137.0+1.21.10` | `0.138.4+1.21.10` |
| 26.1 | `0.143.1+26.1` | `0.155.3+26.1.2` |
| 26.2 | `0.159.0+26.2` | `0.161.0+26.2` |
| 26.3 | `0.160.5+26.3` | `0.161.0+26.3` |

Loader versions in the brief were stated as minimums. We pin one value for all
fourteen targets — the loader is version-independent, so one value removes
fourteen chances to get it wrong — and that value is **`0.19.3`**, the oldest we
support, not the newest. `fabric.mod.json` declares `fabricloader: ">=0.19.3"`, so 0.19.4, 0.19.5
and anything later are accepted automatically. See §4b.

---

## 2. Why nine source trees, not fourteen

Fourteen Minecraft versions do not mean fourteen codebases. Between 1.21.1 and 26.3
there are **nine** API surfaces — places where a Minecraft-facing name or signature
changes enough to stop compiling. Targets that share a surface share one source
tree, so a fix is made once and applies to the whole group.

```
shared/          pure Java, no Minecraft — identical for all 14 targets
eras/1.21/       1.21.1
eras/1.21.2/     1.21.2, 1.21.3, 1.21.4
eras/1.21.5/     1.21.5
eras/1.21.6/     1.21.6, 1.21.7, 1.21.8
eras/1.21.9/     1.21.9, 1.21.10
eras/1.21.11/    1.21.11 — the reference tree, written by hand
eras/26.1/       26.1
eras/26.2/       26.2
eras/26.3/       26.3
versions/<mc>/   one thin module per target: 3-line build.gradle + fabric.mod.json
```

Only `eras/1.21.11` is maintained by hand. `tools/port_eras.py` derives every other
tree from it by applying that era's dialect (the full matrix, with the evidence for
each column, is in that file's docstring) — so the trade logic exists in exactly one
place, the nine trees cannot drift apart, and adding a version is one line in
`gradle/versions.gradle` plus one script run. A **new break** is one new row in the
generator's `ERAS` table, which then applies to every tree it writes.

The boundaries were chosen by compiling, not by guessing at round numbers:

- **1.21.2** changed the recipe ingredient schema from `{"item": "minecraft:x"}`
  objects to bare `"minecraft:x"` strings.
- **1.21.5** turned `ClickEvent` and `HoverEvent` into sealed records
  (`new ClickEvent.RunCommand(…)`, `new HoverEvent.ShowText(…)`), and introduced
  `Inventory.getSelectedSlot()`. Before it, the selected hotbar index is the public
  `selected` field — `getSelected()` returns the selected *stack*, which does not
  compile as an index.
- **1.21.6** added `Commands.hasPermission(int)`, so the admin tree's permission gate
  stops being written as a lambda.
- **1.21.9** removed `Entity.getServer()` (it becomes `level().getServer()`) and added
  `ResolvableProfile.createResolved(GameProfile)` for player heads that already carry
  their skin.
- **1.21.11** renamed `ResourceLocation` to `Identifier`, and moved the notify sound
  from `player.playNotifySound(…)` to `player.level().playSound(…)`.
- **26.1** renamed the click enum `ClickType` → `ContainerInput` and
  `ItemStack.getItemHolder()` → `typeHolder()`; both keep their constants.
- **26.2** collapsed the sixteen colour variants of each item into
  `ColorCollection` records — `Items.GREEN_CONCRETE` becomes `Items.CONCRETE.green()`
  and `Items.BLACK_STAINED_GLASS_PANE` becomes
  `Items.STAINED_GLASS_PANE.black()`. The registry ids are unchanged.
- **26.3** gave `Player.drop` a third argument, a `Prediction`. See §13.2 for why it
  needs its own tree for that one signature.

1.20.1 is no longer a target. It predates data components, and this design's token is
built out of three of them (`custom_name`, `lore`, `max_stack_size`), so the version
is genuinely out of range rather than merely unpolished. Its tree was deleted; §13.1
records what that removed.

---

## 3. The API surfaces, side by side

Nine trees, but most of the surface is identical across them. Only these concerns
actually differ, and each difference is localised to the tree where it appears:

| Concern | Differs at | Old form | New form |
|---|---|---|---|
| Click/hover events | **1.21.5** | `new ClickEvent(Action, String)` / `new HoverEvent(Action, Component)` | `new ClickEvent.RunCommand(…)` / `new HoverEvent.ShowText(…)` |
| Held slot index | **1.21.5** | `inventory.selected` (field) | `inventory.getSelectedSlot()` |
| Permission gate | **1.21.6** | `.requires(source -> source.hasPermission(LEVEL_GAMEMASTERS))` | `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))` |
| Server access | **1.21.9** | `player.getServer()` | `player.level().getServer()` |
| Player head profile | **1.21.9** | `new ResolvableProfile(GameProfile)` | `ResolvableProfile.createResolved(GameProfile)` |
| Resource id | **1.21.11** | `ResourceLocation` | `Identifier` |
| Sound | **1.21.11** | `player.playNotifySound(…)` | `player.level().playSound(…)` |
| Click enum | **26.1** | `ClickType` | `ContainerInput` |
| Item holder | **26.1** | `stack.getItemHolder()` | `stack.typeHolder()` |
| Coloured items | **26.2** | `Items.GREEN_CONCRETE` | `Items.CONCRETE.green()` |
| Authoritative drop | **26.3** | `player.drop(stack, false)` | `player.drop(stack, false, Prediction.SERVER_ONLY)` |
| Item data | — | data components on every target (1.20.1 is retired) | — |
| Networking | — | **none** — the vanilla container needs no custom packets | — |
| Menu opening | — | `MenuProvider` + `MenuType.GENERIC_9x6`; no `getMenuType()` | same |

The rows that used to dominate this table — `blit` vs `blitSprite`, `GuiGraphics`
vs `GuiGraphicsExtractor`, `MouseButtonEvent` / `KeyEvent`, and the whole item
registration group — are gone: the custom GUI was deleted in v1.1.0 and the
registered item in v1.4.1. See §9 and §13.

Findings that were **not** what the brief predicted, each established by compiling:

1. **`MenuProvider` has no `getMenuType()`** on any target. The menu type comes
   from the `AbstractContainerMenu` itself. (§9.1)
2. **`Slot.allowModification` and `Slot.setByPlayer` exist on every era this build
   ships for**, so the slot code needs no dialect at all. (§9.2)
3. **`getSelected()` is not `getSelectedSlot()`.** On 1.21.1 – 1.21.4 the selected
   hotbar index is the public `selected` field; `getSelected()` returns the selected
   *stack*. The generator originally assumed the accessor and the compiler caught
   it in one pass.
4. **26.2 did not remove the colour variants from the registry**, only from the
   `Items` class: `block.minecraft.green_concrete` is still present in 26.3's
   language file, so the collapse is a Java-naming split and the data-pack side is
   untouched by it.
5. **26.3 needs its own tree for a single signature** — `Player.drop(ItemStack,
   boolean)` gained a `Prediction` argument. `PREDICTED` belongs to client-predicted
   actions; vanilla's own authoritative server-side callers
   (`AdvancementRewards`, `ServerPlaceRecipe`, the menus) pass `SERVER_ONLY`, which
   is what the inventory-overflow path uses here.

---

## 4. The break that defines the project: obfuscated → unobfuscated

### 4.1 `ResourceLocation` → `Identifier`

```java
// 1.21.x
new ResourceLocation("tradewindow", "trade_token")

// 26.x
Identifier.fromNamespaceAndPath("tradewindow", "trade_token")
```

There is no `ResourceLocation` class in 26.3 at all — `net/minecraft/resources/`
contains `Identifier` and `IdentifierException` — and the constructor went
private in favour of `fromNamespaceAndPath` / `parse` / `tryParse`.

**Contained to one method per tree** (`TradeWindow.id(String)`), which is why the
rename did not ripple into the shared core.

### 4.2 The rendering stack was rebuilt — in two stages

The break that matters most is **1.21.6**, not 26.x.

**Stage 1 (1.21.6): images become sprites.**

| 1.21.1 – 1.21.5 | 1.21.6+ |
|---|---|
| `blit(id, x, y, u, v, w, h)` | *(gone)* |
| — | `blitSprite(RenderPipeline, id, x, y, w, h)` |
| texture path in `textures/gui/` | must be registered in `assets/<ns>/atlases/gui.json` |
| `com.mojang.blaze3d.pipeline.RenderPipeline` | same (renamed to `renderpearl` by 26.x) |

**Stage 2 (26.x): the extractor model and the text API.**

| 1.21.6 | 26.x |
|---|---|
| `GuiGraphics` | `GuiGraphicsExtractor` |
| `Screen#render(...)` | `Screen#extractRenderState(...)` |
| `AbstractContainerScreen#renderBg(...)` | `#extractBackground(...)` |
| `#renderLabels(...)` | `#extractLabels(...)` |
| `#renderTooltip(...)` | `#extractTooltip(...)` |
| `drawString` / `drawCenteredString` | `text` / `centeredText` |
| `PoseStack` | `Matrix3x2fStack` |
| `com.mojang.blaze3d…RenderPipeline` | `com.mojang.renderpearl…RenderPipeline` |
| `keyPressed(int, int, int)` | `keyPressed(KeyEvent)` |
| `mouseClicked(double, double, int)` | `mouseClicked(MouseButtonEvent, boolean)` |

The `extract*` naming is not cosmetic. 26.x splits "describe what to draw" from
"draw it"; 1.21.6 only went as far as routing images through the sprite atlas.

**Forced:** `TradeScreen` is written three times — once for the `blit` era
(1.21.1 – 1.21.5), once for the `blitSprite` era (1.21.6 – 1.21.11) and once for
the extractor era (26.x). The layout constants, the button state machine and the
colour palette are identical in all three; only the calls differ. That is why the
era boundary sits at 1.21.6 rather than at the obfuscation break.

### 4.3 GUI assets are declared differently

1.21.x points at `textures/gui/trade_gui.png` directly. 26.x resolves a *sprite*,
which must be registered:

```json
{ "sources": [ { "type": "minecraft:directory", "prefix": "", "source": "gui" } ] }
```

in `assets/<ns>/atlases/gui.json`. Without it the header band silently draws
nothing.

**Consequence:** real player-skin heads render on 1.21.x but not on 26.x, because
a remote player's skin is not an atlas sprite. See `DESIGN_DECISIONS.md` #18.

### 4.4 Menus: a constructor that became private

`MenuType`'s constructor is private in 26.x, so a mod cannot build one directly.
The supported route is Fabric's `ExtendedMenuType<T, D>`, which carries an
opening payload alongside the window id.

This is a genuine improvement, and the 1.21.x tree is the one that has to work
around a limitation:

| | 1.21.x | 26.x |
|---|---|---|
| Type | `ExtendedScreenHandlerType<T, D>` (`screenhandler.v1`) | `ExtendedMenuType<T, D>` (`menu.v1`) |
| Provider | `ExtendedScreenHandlerProvider<D>` | `ExtendedMenuProvider<D>` |
| Client learns its side from | the open-screen message | the open-screen message |

Both eras carry the session snapshot as opening data, so neither relies on packet
ordering. (The 1.20.1 tree does, because `SimpleMenuProvider` has no payload
channel — the code says so at the call site.)

### 4.5 Tooltips became data

On 26.x, `Item#appendHoverText(ItemStack, TooltipContext, TooltipDisplay,
Consumer<Component>, TooltipFlag)` is **deprecated**: tooltips are now declared
through the `lore` component.

```java
// 26.x — no method override at all
new Item.Properties()
    .stacksTo(16)
    .component(DataComponents.LORE, new ItemLore(List.of(hint, consumeHint)))
```

`ItemLore` is named `Lore` in 1.21.x, and the 1.21.x tree still overrides
`appendHoverText` because that is the only mechanism there — and it is *not*
deprecated in that era.

### 4.6 Damage observation

Fabric API 0.92.x (1.20.1) has no `AFTER_DAMAGE`. Neither do the 1.21.x API
builds. Only 26.x does.

So the older trees observe damage from `ALLOW_DAMAGE` — a *pre*-damage veto — and
unconditionally return `true`: they observe, they never veto. 26.x uses the
semantically correct post-damage hook, which cannot influence the outcome and
reports the damage actually dealt. Rationale in `DESIGN_DECISIONS.md` #14.

### 4.7 Miscellany found by the compiler

| Older eras | 26.x |
|---|---|
| `player.getServer()` | `player.level().getServer()` |
| `player.playNotifySound(event, source, vol, pitch)` | `player.level().playSound(null, x, y, z, …)` |
| `player.drop(stack, false)` (1.21.4+ adds `Prediction`) | `player.drop(stack, false, Prediction.SERVER_ONLY)` |
| `new ClickEvent(Action, String)` (1.21.4+ uses records) | `new ClickEvent.RunCommand(String)` |
| `new HoverEvent(Action, Component)` | `new HoverEvent.ShowText(Component)` |
| `LocalPlayer#displayClientMessage` | `Player#sendSystemMessage` |
| `ClickType` | `ContainerInput` |
| `NbtIo.read(DataInput)` | `ItemStack.CODEC` + `JsonOps` |

### 4.8 The registry key became mandatory — and the compiler cannot see it

**Boundary: 1.21.2.** From 1.21.2 onward an item's description id and its default
model are derived from the item's own registry key, so `Item`'s constructor reads
that key out of the `Item.Properties` it is handed:

```java
// net.minecraft.world.item.Item$Properties, javap -c against 1.21.11
protected String effectiveDescriptionId() {
    return this.descriptionId.get(Objects.requireNonNull(this.id, "Item id not set"));
}
```

`new Item(new Item.Properties().stacksTo(16))` — properties with no `setId` —
throws from the constructor, which for a mod means the game dies while the
entrypoint class is still initialising:

```
Caused by: java.lang.NullPointerException: Item id not set
	at net.minecraft.class_1792$class_1793.method_63689(class_1792.java:405)
	at net.minecraft.class_1792.<init>(class_1792.java:144)
	at com.tradewindow.item.TradeTokenItem.<init>(TradeTokenItem.java:42)
	at com.tradewindow.TradeWindow.<clinit>(TradeWindow.java:59)
```

(`class_1792` is `Item`, `class_1792$class_1793` is `Item$Properties` and
`method_63689` is `effectiveDescriptionId`; the released jar is remapped to
intermediary, so that is how it prints in a player's crash log.)

**Why no target's build caught it.** The key is a *field*, not a constructor
argument, and to the type system `setId` is optional. A missing id is therefore
a compile-time no-op and a runtime failure — the case §8 warns about, and the only
break in this document that passed `javac` on every target. Read out of
`net/minecraft/world/item/Item$Properties.class` in the merged jar Loom remapped
for each version:

| Version | `Properties#setId` | `"Item id not set"` | Mod must call it |
|---|---|---|---|
| 1.20.1 | no | no | no |
| 1.21.1 | no | no | no |
| 1.21.2 | yes | yes | **yes** |
| 1.21.5 | yes | yes | yes |
| 1.21.6 | yes | yes | yes |
| 1.21.9 | yes | yes | yes |
| 1.21.11 | yes | yes | yes |
| 26.3 | yes | yes (`itemIdOrThrow`) | yes |

The two oldest trees have neither the setter nor the message, which is why
`eras/1.20.1` and `eras/1.21` kept working while the other six were broken on
start-up.

**Fix, once per era from 1.21.2 on** — one key, used for both the properties and
the registration, so the two can never drift:

```java
private static final ResourceKey<Item> TRADE_TOKEN_KEY =
		ResourceKey.create(Registries.ITEM, id("trade_token"));

public static final TradeTokenItem TRADE_TOKEN =
		new TradeTokenItem(TradeTokenItem.createProperties(TRADE_TOKEN_KEY));
...
Registry.register(BuiltInRegistries.ITEM, TRADE_TOKEN_KEY, TRADE_TOKEN);
```

`eras/1.20.1` and `eras/1.21` keep the no-argument `createProperties()`: `setId`
does not exist there, so the same edit would not compile. That is the one place
where the era split is *required* rather than merely convenient.

---

## 4b. Item registration: the key must exist before the Item

> **No longer applicable to the shipped mod.** v1.4.1 deleted the registered
> `tradewindow:trade_token` item, so there is nothing registered and this trap
> cannot be reached by any jar in this repository. The section is kept because it
> documents a real, silent failure — and because this is *the* bug that started the
> project: the crash report that prompted the rewrite was exactly the stack trace
> quoted below, from `TradeTokenItem.<init>` via `TradeWindow.<clinit>`.

**This is the one break that does not show up as a compile error.** A mod that
builds perfectly can still crash the game at startup.

From **1.21.2**, `Item.Properties` has a `ResourceKey<Item> id` field, and
`Item`'s constructor does `Objects.requireNonNull` on it. Constructing an `Item`
in a static field initialiser therefore throws:

```
java.lang.NullPointerException: Item id not set
    at net.minecraft.world.item.Item$Properties.<...>(Item.java:405)
    at net.minecraft.world.item.Item.<init>(Item.java:144)
    at com.tradewindow.item.TradeTokenItem.<init>(TradeTokenItem.java:42)
    at com.tradewindow.TradeWindow.<clinit>(TradeWindow.java:59)
```

The correct pattern, used by every era at or above 1.21.2:

```java
// A key, not an Item.
public static final ResourceKey<Item> TOKEN_KEY =
        ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "trade_token"));

// Assigned in onInitialize(), never at class-init time.
public static TradeTokenItem TOKEN;

@Override
public void onInitialize() {
    TOKEN = new TradeTokenItem(createProperties(TOKEN_KEY));   // settings carry the key
    Registry.register(Registries.ITEM, TOKEN_KEY, TOKEN);      // registered under the same key
}

static Item.Properties createProperties(ResourceKey<Item> key) {
    return new Item.Properties().setId(key).stacksTo(16);      // setId FIRST
}
```

Four things matter:

1. **Declare a `ResourceKey<Item>`, not an `Item`.** The key can be built at
   class-init time safely; the Item cannot.
2. **`.setId(key)` goes first** in the settings chain, before anything else.
3. **Construct inside `onInitialize()`** and register under that exact key, so the
   id an item is built with can never drift from the id it is registered under.
4. **The `TradeTokenItem` constructor does nothing but `super(settings)`.** Any
   work in the constructor runs before the key is known.

**Mapping note:** the setter is called **`setId`** in the official Mojang mappings
used here, not `registryKey`. The resource-key class is `ResourceKey`, not
`RegistryKey`, and there is no `Identifier.of(...)` — the factory is
`fromNamespaceAndPath` (or `parse`). The same `setId` name holds from 1.21.2 all the
way through 26.3.

**1.20.1 and 1.21.1 are exempt.** `Item.Properties` has no `id` field in those
versions, so the plain `new Item.Properties()` in a static field is correct there
and `setId` does not exist. This is a genuine per-era split, not a stylistic one.

### Blocks

The mod contains **no blocks**, so there is no `BlockItem` and no
`Block.Settings#registryKey` to get wrong — the Trade Token is the only registered
content besides the menu type. If a block is added later it needs the same
treatment: `Block.Settings` takes its registry key the same way, and a
`BlockItem` must be built with `new Item.Properties().setId(itemKey)` using the
*item* key (which is normally the same id, but is a separate `ResourceKey<Item>`).

### Fabric Loader compatibility

The build pins **Loader `0.19.3`** — the oldest supported version — and
`fabric.mod.json` declares `fabricloader: ">=0.19.3"`. Building against the oldest
rather than the newest proves the mod works there, and 0.19.4, 0.19.5 and anything
later satisfy the `>=` constraint automatically. Loader is version-independent, so
no code changes were needed for this.

## 5. Data pack and asset layout

The data-pack folder names and file schemas are **not** uniform across the range.
This table was read straight out of each version's `minecraft-client.jar` (the only
authoritative source — the earlier assumption that the recipe folder was singular
everywhere was wrong for 1.20.1, and the advancement folder was plural everywhere
when it must be singular from 1.21.1):

| | 1.20.1 | 1.21.1 | 1.21.2+ |
|---|---|---|---|
| Recipe folder | `recipes/` | `recipe/` | `recipe/` |
| Advancement folder | `advancements/` | `advancement/` | `advancement/` |
| Recipe ingredients | `[{"item": …}]` | `[{"item": …}]` | `["minecraft:paper"]` |
| Recipe result | `{"item": …, "count": …}` | `{"id": …, "count": …}` | `{"id": …, "count": …}` |
| Advancement `items` predicate | `"items": ["minecraft:paper"]` | `"items": "minecraft:paper"` | same |
| Advancement trigger | `minecraft:recipe_unlocked` + `recipe` | same | same |
| Advancement requirements | OR — `[["has_the_recipe", "has_item"]]` | same | same |
| Item model | `models/item/` | same | + `items/` definition from 1.21.4 |

`tools/port_eras.py` writes the folders and schemas per tree, and
`tools/verify_packaging.py` asserts them (along with the lang, asset, manifest and
command invariants) so the rules cannot silently regress — 1314 checks across the
fourteen jars. The 1.20.1 column is history rather than a live target (§13.1); it is
kept because it is the evidence that the plural folder form existed at all. Three
traps worth naming:

- The recipe advancement must use **`minecraft:recipe_unlocked`** (condition
  `recipe`), not `recipe_crafted`, and the two criteria must be **OR**-ed. An
  `AND` requirement never fires before the player has already crafted the item,
  which defeats the point of revealing the recipe.
- 1.21.1 is the awkward middle: singular folders, but still object ingredients.
  Grouping it with 1.21.2 for the ingredient schema silently breaks crafting there.
- **The recipe result carries `components` on every target, including 1.21.1** — the
  field is not a 1.21.2 addition. 1.21.1's `ItemStack` codec has the same
  `id`/`count`/`components` keys as 1.21.11's; it simply has no *vanilla* recipe that
  uses them, which is why grepping the vanilla data files suggests otherwise. That is
  what makes the token's `custom_name`, `lore` and `max_stack_size` uniform across the
  whole range.

For 26.3 the evidence came from `data/minecraft/recipe/blaze_powder.json` and
`data/minecraft/advancement/recipes/…` inside the client jar.

---

## 6. What is shared, and what is duplicated

**Shared, compiled into all fourteen jars from `shared/src/main/java`:**

`TradeSessionCore` (the confirmation/timeout state machine — the window renders a
confirmed side as `LOCKED`), `TradeSwap` + `StackOps` (the
item-movement algorithm), `TradeRegistry` (requests, queueing, session pairing),
`TradeConfigData`, `TradeDistance` (the one distance rule — §12.1), `TradeValueTable`,
`ItemSnapshot`, `TradeRecord`, `TradeRecordJson`, `TradeLogFormatter`,
`TradeHistoryStore` and both backends, `TradeRequest`, `TradeSide`, `CancelReason`.

73 unit tests cover that tree, and because every target compiles it, a green run
means the algorithm is correct on all fourteen at once.

**Generated per era tree — nine copies, each derived from `eras/1.21.11`:**

`TradeUtils`, `TradeItems`, `TradeText`, `TradeConfig`, `PendingReturns`,
`TradeSession`, `TradeManager`, `TradeHistory`, `TradeMenu`, `TradeInventory`,
`TradeHeader`, `TradeOffer`, `TradeSlot`, `TradeCommand`, `TradeAdminCommand`,
`TradeToken`, `TradeWindow`.

Most of those copies differ by an import or a single call, and every difference is
enumerated in `tools/port_eras.py` rather than left to a human to keep in step. That
is the honest cost of the split: the Minecraft-facing surface is written once per
API shape, and the logic that actually moves items is written and tested once.

Note what is **not** duplicated any more. There is no `TradeScreen`, no
`ClientTradeState`, no `TradeClientNetwork`, no packet classes and no
`TradeTokenItem`, because v1.1.0 deleted the custom GUI and v1.4.1 deleted the
registered item. Removing them also removed the largest dialect in the old matrix.

---

## 7. The build system

The version matrix lives in `gradle/versions.gradle` and is the single source of
truth. Each version module's `build.gradle` is three lines:

```groovy
plugins {
	id 'net.fabricmc.fabric-loom-remap' version "${loom_version}"
}

apply from: rootProject.file('gradle/target-module.gradle')
```

`gradle/target-module.gradle` reads the module's matrix entry and configures the
source directories, the Minecraft and Fabric API coordinates, the Java level, the
artifact name, and — the one place the obfuscation break shows up in the build —
whether mappings and `modImplementation` are used at all.

```groovy
if (target.remap) {
	mappings loom.officialMojangMappings()
	modImplementation "net.fabricmc:fabric-loader:${target.loader}"
	modImplementation "net.fabricmc.fabric-api:fabric-api:${target.api}"
} else {
	// No `mappings` line at all: 26.1+ ships unobfuscated.
	implementation "net.fabricmc:fabric-loader:${target.loader}"
	implementation "net.fabricmc.fabric-api:fabric-api:${target.api}"
}
```

```bash
./gradlew buildAll                 # every target, plus the core tests
./gradlew buildAll -Ponly=1.21.6   # one era's targets
./gradlew :v1_21_1:build           # one target
./gradlew listTargets              # print the matrix
```

`tools/generate_modules.py` regenerates the fourteen module directories and
`settings.gradle` from the matrix, and `tools/port_eras.py` regenerates the era trees
those modules compile. Adding a target is one line plus two script runs; see §8.

---

## 8. Adding a fifteenth target

1. Add one entry to `gradle/versions.gradle` — pick the `era` whose API surface
   the new version matches.
2. Run `python tools/generate_modules.py` to write the module and `settings.gradle`.
3. `./gradlew buildAll` and let the compiler list the drift.
4. If the new version disagrees with its era on some API, promote it to a tree of its
   own: one row in `tools/port_eras.py`'s `ERAS` table, one new `era` value in the
   matrix, `python tools/port_eras.py`, then record the break here and in §3.

Step 3 is the important one. The compiler finds signature changes; it does *not*
find a method whose **behaviour** changed while its signature stayed the same.
For those, disassemble the vanilla caller:

```bash
javap -p -c -classpath <minecraft-jar> net.minecraft.<SomeVanillaClass>
```

That is how every entry in §4.7 was confirmed, and it is faster than guessing.

---

## 9. The vanilla-container rewrite (v1.1.0)

v1.1.0 deleted the custom client GUI (`TradeScreen`, its buttons, its two packets
and the client entrypoint) and replaced it with a plain vanilla container. The
trade window is now a `TradeMenu` opened as `MenuType.GENERIC_9x6`, so an
unmodified vanilla client renders it as a double chest.

Removing the GUI removed the entire rendering dialect — the `blit` / `blitSprite`
/ `GuiGraphicsExtractor` splits in §4.2 and the atlas/GUI-asset split in §4.3 no
longer apply, and §4.4's private `MenuType` constructor no longer bites because
nothing constructs a `MenuType`. What remains are four splits in the chat and
registration layer, all resolved by `tools/generate_era_dialects.py` from the
1.21.11 reference:

| Split | Older form | Newer form | Boundary |
|---|---|---|---|
| `ClickEvent` | `new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd)` | `new ClickEvent.RunCommand(cmd)` | 1.21.5 |
| `HoverEvent` | `new HoverEvent(HoverEvent.Action.SHOW_TEXT, c)` | `new HoverEvent.ShowText(c)` | 1.21.5 |
| item key | `new Item.Properties().stacksTo(16)` (1.20.1) / `createProperties()` (1.21) | `createProperties(ResourceKey<Item>)` | 1.21.2 |
| glass pane | `Items.WHITE_STAINED_GLASS_PANE` | `Items.GLASS_PANE` | 26.x |

### 9.1 `MenuProvider` has no `getMenuType()`

An early draft declared `getMenuType()` on the `MenuProvider` record so the server
would know which screen to open. It does not exist — the compiler rejects the
`@Override` on every target. The menu type is taken from the
`AbstractContainerMenu` itself: `ServerPlayer#openMenu` creates the menu and sends
`menu.getType()` in the open-screen packet. `TradeMenu` passes
`MenuType.GENERIC_9x6` to `super`, so `getType()` already answers correctly and
the provider must not declare anything.

### 9.2 The slot permission methods are genuinely portable

The obvious worry was that `Slot.allowModification(Player)` and
`Slot.setByPlayer(ItemStack)` are recent additions. They are not recent *enough*:
both compile unchanged on **every** era this build ships for. So `TradeSlot` (which
overrides `mayPlace` / `mayPickup` / `isActive` / `allowModification`) and
`TradeMenu` (which overrides `quickMoveStack` and calls `setByPlayer`) are copied
verbatim to all nine trees — no dialect, no per-era variant. This was established by
compiling, not by assumption.

### 9.3 Generator idempotency

`tools/port_eras.py` reads the 1.21.11 reference and writes the other nine trees; it
**never writes the reference itself**, which is what makes "edit the reference, then
regenerate" a safe loop rather than a transform that feeds on its own output. (The
earlier generator did rewrite its own input, which forced the hover transform to run
in both directions — `ShowText` → `Action.SHOW_TEXT` for the old eras and the reverse
for the rest — and mis-ran once exactly that way.) Regenerating twice must therefore
produce byte-identical trees, which is the check to run after touching the dialect
table: regenerate, confirm `git status` shows only the trees you meant, and confirm a
generated tree differs from the reference only where its dialect says it should.

### 9.4 Closing the window ends the trade

A vanilla chest can be closed at any moment — Esc, or the inventory key — and
once closed the player has no way back into it. Leaving the session running would
strand both offers until the timeout, with nobody able to see or act on them. So
`TradeMenu` overrides `removed(Player)`:

```java
@Override
public void removed(Player player) {
    super.removed(player);
    if (player instanceof ServerPlayer
            && session != null && !session.core().isFinished()) {
        TradeManager.endSession(session, CancelReason.MANUAL);
    }
}
```

The `isFinished()` guard makes it a no-op whenever the **server** closes the menu
itself (on success, timeout or cancel), because by then the session is already
resolved and the items are already settled. The `ServerPlayer` check keeps the
client-side `removed` call from doing anything. An executed session additionally
refuses a later `cancel`, so the guard is belt-and-braces.

What the vanilla container still gives up: there are no in-GUI buttons or drawn
countdown. Confirm/cancel are chat links and the timer is announced at 30 s and
10 s — chat is the only channel a vanilla client is guaranteed to render.

### 9.5 The interaction is detected on the server

Removing the client entrypoint also removed the client-side right-click
detection, so it had to be replaced with a server-side hook. The old comment
claimed "vanilla has no server-side hook for player X right-clicked player Y" —
that is false: Fabric API's `UseEntityCallback` fires on the server for exactly
that interaction.

```java
UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
    if (!world.isClientSide()
            && hand == InteractionHand.MAIN_HAND
            && player instanceof ServerPlayer sender
            && entity instanceof ServerPlayer target
            && TradeManager.holdsToken(sender)) {
        TradeManager.requestTrade(sender, target);
    }
    return InteractionResult.PASS;
});
```

It always returns `PASS` (observe-only) so it cannot disturb any other
right-click behaviour. `InteractionResult`, `InteractionHand` and
`UseEntityCallback` are identical across all eight trees, so this is dialect-free
and is generated verbatim into every era.

## 10. The 1.21.11 divergence (v1.2.0)

**1.21.11 is no longer a dialect of the other eras.** Since v1.2.0 that target
implements a stricter design, and the rest of this document describes the seven
legacy-era trees plus 26.x, which still register `tradewindow:trade_token`.

What changed and why:

1. **The item is gone.** `eras/1.21.11` has no `item/` package, no `TradeTokenItem`
   and no `Registry.register` call. The token is a vanilla Ghast Tear with a
   `minecraft:custom_name` component from a data-pack recipe. Two failures
   disappear at once: the `Item id not set` crash (the settings never carried a
   registry key) and the vanilla-client kick a registered item would cause
   ("Received a registry entry that is unknown to this client").
2. **The tree is no longer generated.** `tools/generate_era_dialects.py` reads
   `eras/1.21.9` as its reference and does **not** list 1.21.11 in its dialect
   matrix, so a regeneration pass cannot overwrite the v1.2.0 source with
   legacy-dialect code. `tools/fix_data_folders.py` skips 1.21.11 for the same
   reason (its advancement sits at `advancement/recipes/misc/`).
3. **The dialect it used to demonstrate is now only 26.x's.** `Identifier` instead
   of `ResourceLocation`, `level().playSound(...)` instead of
   `playNotifySound(...)`. The 26.x tree still exercises both, and the 1.21.9 tree
   exercises the 1.21.9-and-later command-record API, so no dialect lost its
   proving ground.
4. **API surface it newly exercises**: `ResolvableProfile.createResolved(GameProfile)`
   + `DataComponents.PROFILE` for the player heads, `DataComponents.CUSTOM_NAME`
   and `ItemLore` for the labels and buttons, `SimpleMenuProvider` for the window
   title, and `AbstractContainerMenu.clicked(...)` as the single server-side click
   gate.
   Since v1.3.0 that gate is also the whole button mechanism: the click is matched
   against `TradeInventory.lockSlot(side)` / `cancelSlot(side)`, `ClickType.PICKUP`
   separates a button press from a shift-click or drag, and the handler returns
   before `super.clicked` so vanilla never touches a header slot. The other root
   this target added is `Class.getResourceAsStream` on its own jar, which is how
   `TradeText` loads `assets/tradewindow/lang/en_us.json` - mod resources are on a
   dedicated server's classpath, so the same code path works in dev and in
   production, and the server (not the client) resolves every string.
   Note that `ItemStack` has **no** `get(DataComponentType)` in this version - the
   token name is read through `getHoverName()`, which is also the semantically
   correct check (it returns the custom name, then the item name, and never a
   translation key that could collide).

5. **Its strings are its own.** `assets/tradewindow/lang/en_us.json` is the source of
   every sentence this target sends, read at start-up rather than resolved by the
   client, because no client has this mod installed. That file is therefore checked
   against the Java that uses it (see below) instead of being a catalogue nobody
   reads.

`tools/verify_packaging.py` enforces the divergence: for 1.21.11 it checks the
registration-free recipe and advancement, the absence of item assets and of the
`item`/`network`/`screen`/`client` packages, the three-command set, that every
`tradewindow.*` key used in Java exists in that era's `en_us.json`, that the token
name constant agrees with both the language file and the recipe's `custom_name`, and
that both painted controls (`lockSlot`/`cancelSlot`) are also recognised in
`TradeMenu`. That section described the 1.2.0-era gate, which covered one target and
357 checks; it now covers all fourteen targets and 1314 (§13.4).

## 11. The 1.21.11 refinements (v1.4.0)

Four things this pass learned about the version itself, all of which cost time to
find and are worth writing down before the next port touches them.

### 11.1 `ResourceLocation` is `Identifier` from 1.21.11

1.21.11 renamed the class: it is `net.minecraft.resources.Identifier`, with
`ResourceKey.identifier()` replacing `ResourceKey.location()`. Every other era tree
in this repository uses `ResourceLocation`, so the dialect files and the era
generator would produce code that does not compile here. `TradeItems` is the only
place this target needs an id, and it takes it from the stack's own item holder
(`getItemHolder().unwrapKey()`) rather than from any registry, which keeps the
difference to one method call.

### 11.2 Numeric permission levels are gone

`CommandSourceStack.hasPermission(int)` no longer exists in 1.21.11; commands are
gated against a `PermissionSet`, which is why the admin tree is registered with
`requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))` — level 2, expressed
as `PermissionProviderCheck`, exactly as vanilla's own commands do it.

### 11.3 `max_stack_size` is a real, patchable component

Verified against the 1.21.11 game rather than assumed, because a rejected component
would silently leave the token as a 64-stack tear:

- `DataComponents.MAX_STACK_SIZE` is registered as
  `persistent(ExtraCodecs.intRange(1, 99))` and `networkSynchronized(VAR_INT)`, so a
  data-pack recipe result may set it and `16` is inside its range;
- `ItemStack.getMaxStackSize()` reads that component rather than the item's own
  default, so the crafted tear really does stack to 16;
- a *plain* Ghast Tear has no such component (`getOrDefault(..., 1)`), which is what
  makes `TradeToken.isToken`'s component check a usable identity test: the recipe's
  tear has it and nothing else does.

### 11.4 One session, two menus

A container cannot be arranged differently per viewer, but a **menu** can: both
players are sent the same `GENERIC_9x6` type and the same 54 slot ids, and each
`TradeMenu` maps those ids onto different containers. Since `ServerPlayer.tick()`
calls `containerMenu.broadcastChanges()` and then `stillValid` every tick, the two
arrangements stay live with no packet of our own, and `stillValid` returning false is
what closes a window when the trade ends or a spectator's view goes stale. The only
thing to keep in mind when editing this: any per-player decision must read the menu's
viewer, never the session's fixed `PLAYER_1`/`PLAYER_2` sides.

---

## 12. The token-safe distance gate (v1.4.1)

### 12.1 One question, three askers

The distance rule now lives in `shared/src/main/java/com/tradewindow/core/TradeDistance.java`
as a pure function - `tooFar(config, sameDimension, distanceSquared)` - and the manager
asks it in three places: when a request is sent, when one is accepted, and on every tick
(`tooFarApart` is the adapter that reads the live positions). v1.4.0 asked it only in the
tick loop, so with `crossDistanceTrading` off a request could be *accepted* out of range,
the requester's token spent and the window opened, only for the next tick to cancel the
trade. Anything that can refuse a trade now runs at the point where money changes hands,
not only at the point where the trade is watched.

If you port this era forward, keep all three call sites. Two of them are easy to lose:
the request-time check is a courtesy, and the accept-time one is the one that protects the
token, so a future refactor that keeps only "the tick loop plus one other" must keep the
accept path.

### 12.2 A refused acceptance puts the request back

`accept()` removes the pending request from `REQUESTS` before it validates anything, so
the distance refusal re-inserts the same record unchanged. That way the answer is lost
but the request is not: walking closer and pressing `[ Accept ]` again inside the original
timeout still opens the window and still spends exactly one token. It also means the
refusal must come *before* `TradeToken.consume` - the gate encodes that ordering, so a
future edit that moves the check downwards fails `tools/verify_packaging.py`.

### 12.3 Defaults tuned for live play

`tradeTimeoutSeconds` 15, `guiTimeoutSeconds` 120, `maxDistanceBlocks` 20,
`cancelOnMove` false. These live in the shared model, so they apply to every target that
reads it; `verify_packaging.py`'s `verify_config_docs()` compares each one against the
README's config block, which is why the four numbers had to change in both places at
once. The two distance switches are now both off by default, so the bug above is
unreachable on a fresh install and only the operator who deliberately turns the rule on
meets it - `TESTING.md` §0.9 is the checklist for that configuration.

---

## 13. Porting every target to the v1.4.1 design (v1.4.1)

Until this pass only 1.21.11 carried the current design. Every other target still
shipped the v1.1.0 code — a registered `tradewindow:trade_token` item, a drawn
client GUI, two packet classes and the old fixed grid in which both players saw the
same rows. This section records what it took to make all fourteen targets one
design, and what surprised the compiler.

### 13.1 One reference, and a generator instead of nine hand-copies

The design now lives in exactly one hand-written tree, `eras/1.21.11`.
`tools/port_eras.py` derives every other tree from it: copy the Java sources and the
resources, then apply that era's dialect, which is a small, fully enumerated table of
string substitutions (the matrix, and the evidence for each column, is in the
script's docstring). Two consequences worth remembering:

- **A fix goes in one place.** Edit the reference, run the generator, and all nine
trees move together. That is the only way fourteen targets stay consistent — nothing
else in the build compares them to each other.
- **A generated tree must not be hand-edited.** The next regeneration discards it.
  Change the reference, or change the dialect table, then regenerate.

1.20.1 was **retired** rather than ported. Its item data is NBT, and this design's
token is defined by three data components, so porting it would have meant a second
implementation of the token's identity check — precisely the divergence the
single-reference design exists to prevent. The tree, its module and its jar are gone;
the brief's fourteen targets are what the build ships.

### 13.2 The 26.x line had to be split three ways

The old matrix treated 26.1, 26.2 and 26.3 as one `eras/26` tree and admitted that
only its newest member was actually verified. Porting the current design showed why
that was unsafe — the compiler found two real splits inside the line:

- **26.2** turned the sixteen colour variants of each item into a `ColorCollection`.
  `Items.GREEN_CONCRETE` is no longer a constant; it is `Items.CONCRETE.green()`, and
  `Items.BLACK_STAINED_GLASS_PANE` is `Items.STAINED_GLASS_PANE.black()`. Both painted
  controls and every blank spacer in the layout touch this, so it is not a corner
  case. The registry ids are unchanged — `block.minecraft.green_concrete` is still in
  26.3's language file — so it is a Java-naming split only, and recipes, advancements
  and item ids needed no change at all.
- **26.3** gave `Player.drop` a `Prediction` argument. One call site is affected
  (handing a stack back to a player whose inventory was full), and
  `Prediction.SERVER_ONLY` is what vanilla's own authoritative server-side callers
  pass.

So 26.x is now three trees, and 26.1 is no longer a version nobody built. The general
lesson is §8's: the compiler does find signature changes, and it found these. The
failure mode to watch is a changed **behaviour** behind an unchanged signature, which
is what disassembly is for.

### 13.3 The manifests are generated too — and that is where the design leaked

Every `fabric.mod.json` is written by `tools/generate_modules.py`, and that template
is as much a part of the design as the code: it had kept `"environment": "*"` and a
`client` entrypoint pointing at `com.tradewindow.client.TradeWindowClient`, a class
that no longer exists anywhere. Regenerating the modules silently restored both, and
all fourteen jars failed the packaging gate on exactly those two checks.Two things to take from that: the gate caught it and the **compiler never could** (a
`fabric.mod.json` is not compiled at all), and a generated file's template deserves
the same review as handwritten code. The manifest is now `"environment": "*"` with a
single `main` entrypoint and no `client` entrypoint, asserted for all fourteen jars by
the gate. (It was `"server"` for a while; §14.3 explains why that was wrong.)

### 13.4 What is verified, and what is not

**Verified here:** all fourteen targets compile; `./gradlew buildAll` produces all
fourteen jars plus their sources jars; `tools/verify_packaging.py` passes **1437
checks**, including the **class-file major version** of every jar — 61 for the Java 21
targets, 69 for the Java 25 ones — which is the one property that silently decides
whether a jar loads at all; the shared core's **73 tests** pass; and no jar contains an
`item`, `network`, `screen` or `client` package.

Also verified, per target, by booting a real server: the mod load line, the token
self-check line (see §14.5), no `Parsing error loading recipe`, no
`Registry loading errors`, and no `Failed to load datapacks`.

**Not verified here:** anything in an actual game. No target has been launched against
a real server with two vanilla clients, on any loader. TESTING.md §2 is the per-target
checklist for that, and it is per target rather than per era on purpose: two versions
sharing a source tree still have different jars, different mappings and different
loaders, and "it compiles" is not "it loads ".

## 14. The runtime dialects the compiler cannot see (v1.4.2)

Everything in §3–§13 is about *compiling* for fourteen targets. This section is about
the three faults that compiled perfectly on all fourteen and still made the mod
unusable, and about the method that found them: **boot a real server per target and
read the log.**

Two of the three live in data-pack JSON, which no compiler reads; the third lives in
the manifest, which `gradle` treats as an opaque resource. The packaging gate passed on
all three, and would have kept passing. A `runServer` start reported each one in a
single line.

### 14.1 Text components: the form is not portable, and it flips at 1.21.2

This is the trap in this project. The two ways of spelling a component in a recipe are
not interchangeable, and **each side of the 1.21.2 line accepts exactly one of them and
silently mishandles the other**:

```json
"minecraft:custom_name": "{\"text\":\"Trade Token\",\"color\":\"red\",\"italic\":false}"
"minecraft:custom_name":  {"text": "Trade Token", "color": "red", "italic": false}
```

| | **1.21.1** | **1.21.2 and later** (incl. 1.21.11, 26.1–26.3) |
|---|---|---|
| **object** form | rejected: `JsonParseException: Not a string: {...}` — the whole recipe is dropped | **correct** — a styled name |
| **string** form | **correct** — the string is parsed as JSON | accepted, and read as **literal text** — the item is named with the raw JSON |

Both failure modes are silent in the ways that matter: the recipe-drop logs a line but
leaves a token that can never be crafted, and the literal case logs *nothing at all* and
only shows up on a player's client, as a Ghast Tear whose name is
`{"text":"Trade Token","color":"red","italic":false}`. Neither a compiler nor a unit
test nor the packaging gate can see either one.

**How it was pinned down.** Not from the schema, and not from the brief — from real
server boots, with one probe per question:

1. *Is the object form accepted?* Boot each target with object components and read the
   log. Result: rejected on **1.21.1** (`Not a string`), accepted on **1.21.2 through
   1.21.11 and 26.1, 26.2** and 26.3.
2. *Is the string form parsed or literal?* Boot with a deliberately **invalid** JSON
   payload (`{"text": BOGUS_NOT_JSON`). A version that parses the string must reject
   it; a version that reads it literally accepts it in silence. Result: **1.21.1**
   parses (`EOFException ... path $.text`); **1.21.11 and 26.2** accept it silently.

Those two answers are complementary, so no guesswork is left: where the object is
rejected the string *must* be the parsed form, and where the object is accepted the
string is literal — which makes the object the only spelling that can render. Hence
`RECIPE_COMPONENT_STYLE = {"1.21": "string"}` and objects everywhere else, one era
tree (1.21.1's own) still carrying the flat form.

**The lesson, in the shape that generalises:** a value that loads is not a value that
*works*. The object form on 1.21.1 loads as nothing, and the string form on 26.2 loads
as raw JSON — the loader reports success in both cases. Only booting the game and
reading what it says, or driving the codec with a deliberately bad input to see whether
it complains, separates the two.

### 14.2 `recipe_unlocked`: `recipe` everywhere except 26.3

```json
{ "trigger": "minecraft:recipe_unlocked", "conditions": { "recipe":  "tradewindow:trade_token" } }
{ "trigger": "minecraft:recipe_unlocked", "conditions": { "recipes": "tradewindow:trade_token" } }  // 26.3 only
```

The advancement names the recipe it unlocks with **`recipe`** from 1.21.1 up to and
including 26.2, and with **`recipes`** on **26.3**. A wrong key there is not a silent
loss: 26.3 aborts with `Failed to load datapacks, can't proceed with server load` and
never finishes starting. `tools/port_eras.py` writes the per-era key, and the gate
checks it, so the single tree that differs is the single key that differs.

### 14.3 `"environment": "server"` means *not on a client* — including single-player

This is the one that mattered most in practice, and the most counter-intuitive.

`"environment": "server"` does not mean "this mod is server-side"; it means **Fabric
Loader skips this mod when it is running in a client**. `ModMetadata.matches(EnvType)`
returns false for `CLIENT` on a server-only mod, and the loader never invokes the
entrypoint. A single-player world is a client, so the same jar that worked perfectly on
a dedicated server did **nothing at all** in single-player: no commands, no right-click,
no error, no log line. That is the reported symptom.

The value is now **`"*"`**: one `main` entrypoint on both sides, still no `client`
entrypoint and still no client code. This costs nothing in safety, because the
entrypoint's callbacks are all server-side Fabric API events or are explicitly guarded:

- `UseEntityCallback` fires on both sides, and is guarded by `!world.isClientSide()`;
- `ServerTickEvents`, `ServerPlayConnectionEvents`, `ServerLivingEntityEvents`,
  `ServerLifecycleEvents` and `CommandRegistrationCallback` only ever fire with a
  running server;
- on a client the mod therefore initialises, writes its config file, and does nothing.

The general lesson for this project: **`"environment": "server"` is a decision about
where a jar is *allowed to load*, not a statement about which game systems it touches.**
For a mod whose only interface is a vanilla container, `"*"` is both accurate and
strictly more useful.

### 14.4 The boot test, and what it is

For each target: a `runServer` start with `eula.txt` and `server.properties` in the
module's `run/` directory, polled until the mod initialises or a datapack failure, then
the log read for the mod's load line, the token self-check line, the recipe count,
`Parsing error loading recipe` and `Registry loading errors`. All fourteen start clean.
It takes a few minutes per era and it is the only check in this project that exercises
the JSON — which is why it found what 1437 static checks could not.

### 14.5 The self-check, so this cannot ship silently twice

`com.tradewindow.util.TokenRecipeCheck` runs at the end of `onInitialize`, before the
mod's "loaded" line. It reads `data/tradewindow/recipe/trade_token.json` back out of the
mod's **own jar** (not the source tree, so it describes what was actually packaged),
regexes out the first character of the `minecraft:custom_name` value, and compares the
form it finds with the form the running Minecraft version accepts:

```
Trade Token recipe check: object text components, correct for Minecraft 26.2
Trade Token recipe check: string text components, correct for Minecraft 1.21.1
```

and when they disagree it says *which* consequence, because the two are different:

```
Trade Token recipe check FAILED: this jar spells the token's text components as
object but Minecraft 1.21.1 needs string - the recipe will NOT load and no Trade
Token can be crafted. Rebuild with the matching era tree (tools/port_eras.py).
```

It is deliberately not a codec round-trip: it asserts the *encoding* against a one-line
version table, which is exactly the decision the generator makes and the decision that
was wrong. The verification that the table itself is right lives in §14.1, in the boot
probes. The packaging gate asserts the same rule statically, for every target and every
era tree.
