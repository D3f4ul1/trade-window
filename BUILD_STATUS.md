# Build Status

**All 14 targets build, and all 14 boot.** Every jar is produced from a single
`./gradlew buildAll`, every jar is `"environment": "*"` with a single `main` entrypoint
and no `client` entrypoint, and every jar now carries the **same design** — the fourteen
targets differ only in build configuration, not in behaviour. Each one has also been
started as a real server and its log read: **zero recipe-parse errors and zero registry
errors on all fourteen**.

Last updated: after the per-version token encoding fix and its startup self-check
(v1.4.3), which follows the boot-test pass that fixed the three runtime faults the build
could not see (v1.4.2), the port of the 1.21.11 design to every other target (v1.4.1),
the vanilla-token rewrite (v1.2.0), the in-window LOCK / CANCEL buttons (v1.3.0), the
mirrored per-player view with `/tradeadmin` (v1.4.0), and the retuned defaults with the
token-safe distance gate (v1.4.1).

The sections below v1.4.3 are kept as history: they describe builds of an older design
(and of targets since retired), so their jar names, counts and tree lists are records of
those runs rather than current claims.

---

## v1.4.3 — the token's name was raw JSON on thirteen of fourteen targets (verified)

| | |
|---|---|
| Command | `./gradlew buildAll` |
| Result | **BUILD SUCCESSFUL** — all 14 targets |
| Artifacts | `versions/<mc>/build/libs/tradewindow-1.4.3-<mc>.jar` (plus a `-sources.jar` each) |
| Packaging gate | `python tools/verify_packaging.py` → **OK: all 1437 packaging checks passed** |
| Shared tests | `./gradlew :shared:test` → **73 tests, 0 failures** |
| Boot test | all 14: **self-check line correct, no parse errors, no registry errors, no datapack failures** |

### What was wrong

1.4.2 fixed the recipe's text components for 1.21.1 by switching all fourteen targets to
the escaped-string form, on the reasoning that it was "accepted everywhere". It is
accepted everywhere - and on 1.21.2 and later a string is read as **literal text**, so
thirteen targets shipped a valid recipe whose output was a Ghast Tear named
`{"text":"Trade Token","color":"red","italic":false}`. Reported on 26.2.

The rule is not uniform and it is not guessable; it flips at 1.21.2 in opposite
directions, and each side mishandles the other's spelling without an error:

| | 1.21.1 | 1.21.2+ (incl. 1.21.11, 26.x) |
|---|---|---|
| object | `Not a string` — recipe dropped | **correct** |
| escaped string | **correct** (parsed as JSON) | accepted, read **literally** |

### How it was measured, not assumed

Every target was booted twice with a probe recipe: once with object components (to find
who rejects them: **1.21.1 alone**), and once with a deliberately **invalid** JSON string
(to find who parses strings: a parse error means parsed, silence means literal -
**1.21.1 parses**, **1.21.11 and 26.2 do not**). The two answers are complementary, so
the correct form for each side is forced rather than chosen.

The generator now carries `RECIPE_COMPONENT_STYLE`, the gate asserts the form per
target *and* per era tree, and **every jar logs its own verdict at startup**:

```
Trade Token recipe check: object text components, correct for Minecraft 26.2
Trade Token recipe check: string text components, correct for Minecraft 1.21.1
```

Recorded from the real servers, all fourteen: 1.21.1 → `string`; 1.21.2 through 1.21.11
and 26.1, 26.2, 26.3 → `object`; zero parse errors, zero registry errors, zero datapack
failures.

---

## v1.4.2 — the three faults a compiler cannot see, found by booting every target (verified)

| | |
|---|---|
| Command | `./gradlew buildAll` |
| Result | **BUILD SUCCESSFUL** — all 14 targets |
| Artifacts | `versions/<mc>/build/libs/tradewindow-1.4.2-<mc>.jar` (plus a `-sources.jar` each) |
| Packaging gate | `python tools/verify_packaging.py` → **OK: all 1383 packaging checks passed** |
| Shared tests | `./gradlew :shared:test` → **73 tests, 0 failures** |
| Boot test | all 14 started as real servers: **no recipe-parse errors, no registry errors** |

### Why this release exists

The 1.4.1 port compiled everywhere and passed every check, and was still unusable. All
three faults lived in files that are only read when the game runs — a recipe JSON, an
advancement JSON and the manifest's `environment` value — so no compiler, no unit test
and no packaging assertion had an opinion about them. Starting each target as a real
server did, immediately.

> **Correction (v1.4.3):** the first row below is half wrong, and the fix it describes
> is what v1.4.3 had to undo. Only **1.21.1** rejects the object form; 1.21.2 – 1.21.10
> accept it. Applying the escaped string form to *every* target is what made thirteen
> of them render the token's name as raw JSON. See the v1.4.3 section above.

| Target | Symptom before the fix | Cause |
|---|---|---|
| 1.21.1 | recipe silently absent (`Parsing error loading recipe tradewindow:trade_token`) | components written as JSON objects; 1.21.1's codec wants the escaped-string form (the other nine targets do **not** — see the correction above) |
| 26.3 | server refused to boot (`Failed to load datapacks, can't proceed with server load`) | `recipe_unlocked` takes `recipe` up to 26.2, `recipes` on 26.3 |
| all 14, on a client | mod never initialised — no commands, no right-click, nothing | `"environment": "server"` makes Fabric Loader skip the mod on a client, and single-player is a client |

The third is the one that mattered most in practice: a dedicated server was fine, but
the same jar installed on a single-player world did nothing at all, which is exactly the
symptom reported. The manifest is now `"*"` — one `main` entrypoint on both sides, no
`client` entrypoint, no client code, every callback either server-side or behind
`!world.isClientSide()`. That is a capability change in one direction only: the mod now
runs in one more place and behaves identically on a dedicated server.

### Boot-test evidence

Recorded per target from a real `runServer` start: mod load line, recipe count, no parse
errors, no registry errors, `Done (Ns)` reached. The re-runs that proved the fixes:
1.21.1 went from 1290 to 1291 recipes (ours is back) and 1.21.11 stayed clean on the flat
encoding; 26.3 went from *refusing to boot* to `Done (2.284s)` with 1867 advancements.

---

## v1.4.1 — one design on every target, calmer defaults, and a token that is never spent on a trade that cannot open (verified)

Fourteen targets moved: the 1.21.11 design was ported to the other thirteen, and the
retuned defaults and token-safe distance gate apply to all of them.

| | |
|---|---|
| Command | `./gradlew buildAll` |
| Result | **BUILD SUCCESSFUL** — all 14 targets |
| Artifacts | `versions/<mc>/build/libs/tradewindow-1.4.1-<mc>.jar` (plus a `-sources.jar` each) |
| Packaging gate | `python tools/verify_packaging.py` → **OK: all 1314 packaging checks passed** |
| Shared tests | `./gradlew :shared:test` → **all pass** (73 tests, four of them for the distance rule) |

### What changed in the port

1. **One reference tree, one generator.** `eras/1.21.11` is the only hand-written
   Minecraft-facing tree; `tools/port_eras.py` derives the other nine from it through
   a per-era dialect table, so a fix in the reference reaches all fourteen targets.
   A generated tree is never hand-edited — the next regeneration would discard it.
2. **Nine source trees, not eight.** 26.x split into `eras/26.1`, `eras/26.2` and
   `eras/26.3` because the compiler found two real breaks inside the line: 26.2
   collapsed the colour variants into `ColorCollection` records, and 26.3 added a
   `Prediction` argument to `Player.drop`. The old single `eras/26` tree only ever
   claimed to be verified for its newest member.
3. **1.20.1 retired**, along with its tree, module and jar. It has no data components,
   and the token is defined by three of them.
4. **Every target registers nothing.** The generated manifests are
   `"environment": "server"` with one `main` entrypoint — the template had silently
   restored a `client` entrypoint naming a class that no longer exists, and all
   fourteen jars failed the gate on exactly that until it was fixed (§13.3 of
   `PORTING_NOTES.md`).
5. **The gate was rewritten** for what ships now: 14 jars × era-resource, manifest,
   layout, config and class-file-version invariants, 1314 checks in total.

### What changed since v1.4.0

1. **A Trade Token is never spent on a trade that cannot open.** With
   `crossDistanceTrading` off, v1.4.0 spent the tear, opened the window and let the
   *next tick* cancel the trade — the player paid for a window they never got to use.
   The distance question is now answered before the spend, and before a request is even
   sent: `/trade <player>` out of range is refused outright, and an acceptance out of
   range is refused with the request left on the pending queue, so walking closer and
   pressing `[ Accept ]` again inside its timeout still works and still costs exactly
   one token.
2. **The distance rule has one definition.** New `TradeDistance` in the shared core:
   a pure function over `cancelOnMove`, `crossDistanceTrading`, `maxDistanceBlocks`,
   `allowCrossDimensionTrading` and the pair's separation, called by the request, the
   acceptance and the tick loop. Four unit tests cover the gate, the radius and the
   cross-dimension case; the packaging gate fails if an acceptance ever checks the rule
   after `TradeToken.consume`.
3. **New defaults, all chosen for live play:** `tradeTimeoutSeconds` 30 → **15**
   (a request is answered from a chat line, and the window also bounds how long a token
   is reserved), `guiTimeoutSeconds` 60 → **120** (the window is where the work
   happens), `maxDistanceBlocks` 8 → **20**, and `cancelOnMove` true → **false** — so
   the distance rule now needs *both* switches turned over before it can end a trade.
4. **Two new refusal strings** in the language file, neither of which mentions a token
   being spent: `You are too far away to trade — move within N blocks of the other
   player.` and the partner's `X is too far away to trade — move within N blocks of
   them.`

### What was verified

- `./gradlew :shared:test` — 73 tests, 0 failures, including the four distance-rule
  tests and the updated default assertions. The shared core is compiled into all
  fourteen jars, so one green run covers every target.
- `./gradlew buildAll` — all fourteen targets, 28 artifacts (a jar and a sources jar
  each).
- `python tools/verify_packaging.py` — **1314 checks, all passing, across the fourteen
  jars**. The gate compares the documented defaults against the code, so every
  retuned value had to be updated in `README.md` together with `TradeConfigData`; it
  reads the shared `TradeDistance` to confirm the gate is still over both config
  switches; and it checks the order inside `accept()` so the distance refusal cannot
  slip below the token spend again.
- **Class-file major version per jar** — 61 (Java 21) for the eleven 1.21.x jars, 69
  (Java 25) for the three 26.x ones. This is the property that decides whether a jar
  loads on the runtime it is installed into, and the one a wrong toolchain breaks
  silently.
- Jar contents: `com/tradewindow/core/TradeDistance.class` present, both new strings in
  `assets/tradewindow/lang/en_us.json` at the path `TradeText` reads, `environment`
  `server` with a single `main` entrypoint, and **no** `com/tradewindow/item`, `screen`,
  `network` or `client` package in any of the fourteen.
- Source order confirmed in `TradeManager.accept`: the `tooFarApart` refusal, then
  `TradeToken.consume`.

### Not verified

- Anything in-game: the refusal path needs a real server and two unmodified clients,
  which is `TESTING.md` §0.9. The unit tests cover the rule itself, not the wiring.

---

## v1.4.0 — a mirrored window, one title, and operator tools (verified)

One target moved: Minecraft 1.21.11. The other fourteen are untouched and still
implement the 1.1.0 design.

| | |
|---|---|
| Command | `./gradlew :v1_21_11:build` |
| Result | **BUILD SUCCESSFUL** — `compileJava`, `processResources`, `jar`, `sourcesJar`, `remapJar` |
| Artifact | `versions/1.21.11/build/libs/tradewindow-1.4.0-1.21.11.jar` |
| Also produced | `tradewindow-1.4.0-1.21.11-sources.jar` |
| Packaging gate | `python tools/verify_packaging.py` → **OK: all 354 packaging checks passed** |
| Shared tests | `./gradlew :shared:test` → **all pass** (69 tests, including the new `crossDistanceTrading` default) |

### What changed since v1.3.0

1. **The view is mirrored per player.** Each player now gets their own menu instance
   over the same session: rows 2–3 (slots 9–26) hold the viewer's own offer and rows
   5–6 (slots 36–53) their partner's, in both windows. The header containers are per
   viewer too, so your own head is at slot 4 and your partner's at slot 31 whichever
   side of the request you were on. Nothing is duplicated: both menus point at the two
   shared offer containers and vanilla syncs each one.
2. **The title is exactly `Trading Window`,** resolved server-side from
   `container.tradewindow.title`. A translatable component would reach a vanilla
   client as the raw key, because no client has this mod's language file.
3. **New header rows.** `LOCK` moves to slot 3 (row 4: 30), the player head to slot 4
   (31) and `CANCEL` to slot 5 (32); the status paper is gone and the spent `LOCK`
   block is the state display. Heads carry a name and no lore. Slot 3 and slot 30 are
   both "lock my side", so neither can be used against the other player.
4. **The token stacks to 16**, via `minecraft:max_stack_size` in the recipe, and the
   identity check now requires that component as well as the name.
5. **`/tradeadmin`** — `crossdistance <on|off>` (persisted to the config),
   `list`, `spectate <player>` (read-only) and `history [player]`, all at permission
   level 2. `TradeHistory` keeps the last 20 completed trades in memory and, with
   `logTrades` on, in `tradewindow-history.jsonl` (or SQLite when a driver is
   present).
6. **The config is the shared model again** — thirteen fields, every one honoured on
   this target, instead of the three-field copy v1.3.0 shipped.

### What was verified

- `:v1_21_11:compileJava` clean, then a full `:v1_21_11:build`.
- Jar contents: one `main` entrypoint, `"environment": "server"`, no `client`
  entrypoint, no mixins, **no** `com/tradewindow/item`, `screen`, `network` or
  `client` package, and the language file at exactly the path `TradeText` reads.
- The gate gained 63 checks for this design, including: the title key is resolved and
  no translatable component exists anywhere in the tree; the layout constants put the
  head at offset 4 with `LOCK`/`CANCEL` either side; no status paper and no head lore
  have crept back; both menus map rows 2–3 and 5–6 onto opposite sides; a header click
  calls `TradeManager.lock(server)`/`cancel(server)` and never resolves against the
  row's owner; the distance rule is gated by `crossDistanceTrading` and `cancelOnMove`;
  the recipe's `max_stack_size` and the Java constant are both 16; and **every field
  of the shared config model is mentioned somewhere in the 1.21.11 tree**, which is how
  a config that pretends to work gets caught.
- `minecraft:max_stack_size` was verified against the game, not assumed: the component
  is registered as `persistent(ExtraCodecs.intRange(1, 99))` and network-synchronised,
  and `ItemStack.getMaxStackSize()` reads it. See PORTING_NOTES.md §11.

Not verified here: in-game behaviour. TESTING.md §0 is the checklist for that, and it
is the one thing this build cannot check for itself — in particular the mirrored view
and the read-only spectator window have never run in a real game.

### Known trade-offs in this jar

- Locking still cannot be undone except by cancelling (DESIGN_DECISIONS.md #22).
- `cancelOnDamage` and the value cap default to the shared model's values, which means
  damage ends a trade by default — a behaviour change from v1.3.0. It is now also a
  documented, switchable setting rather than nothing at all.
- Spectators are identified by UUID only and are not persisted; a server restart with
  an admin watching simply drops the view.

---

## v1.3.0 — in-window buttons, a spent token, and strings from the lang file (verified)

One target moved: Minecraft 1.21.11. The other fourteen are untouched and still
implement the 1.1.0 design.

| | |
|---|---|
| Command | `./gradlew :v1_21_11:build` |
| Result | **BUILD SUCCESSFUL** — `compileJava`, `processResources`, `jar`, `sourcesJar`, `remapJar` |
| Artifact | `versions/1.21.11/build/libs/tradewindow-1.3.0-1.21.11.jar` (74 KB) |
| Also produced | `tradewindow-1.3.0-1.21.11-sources.jar` |
| Packaging gate | `python tools/verify_packaging.py` → **OK: all 291 packaging checks passed** |

### What changed since v1.2.0

1. **The window has buttons.** Each player's header row - slots 0-8 for player 1,
   27-35 for player 2 - holds that player's head, their status paper, a green
   concrete `LOCK` block and a red concrete `CANCEL` block, with black panes filling
   the rest. `TradeMenu.clicked` turns a click on one of those two slots into
   `TradeManager.lock` / `TradeManager.cancel` *before* any vanilla movement logic
   runs, so nothing is typed, no chat button is pressed, and the slots can never be
   picked up, moved or duplicated.
2. **The token is spent when the window opens,** from the exact slot the sender used:
   `PendingRequest` remembers the slot, `TradeToken.consume` decrements that stack or
   clears it, and a sender who no longer has it gets no window instead of a free one.
3. **Strings moved into the language file.** `TradeText` reads
   `assets/tradewindow/lang/en_us.json` from the jar at start-up; Java holds language
   keys and format arguments only.
4. **Commands are down to three** (`/trade <player>`, `/trade accept`,
   `/trade decline`) and chat is down to one line per event. Locking and cancelling
   are clicks, so no command stands in for them.

### What was verified

- `:v1_21_11:compileJava` clean, then a full `:v1_21_11:build`.
- Jar contents: one `main` entrypoint, `"environment": "server"`, no `client`
  entrypoint, no mixins, **no** `com/tradewindow/item`, `screen`, `network` or
  `client` package, and `assets/tradewindow/lang/en_us.json` present at exactly the
  path `TradeText` reads (a missing language file would show every player a key
  instead of a sentence).
- `fabric.mod.json` expands to `"version": "1.3.0"` and `"minecraft": "1.21.11"`.
- The packaging gate grew three checks' worth of teeth for this design: a
  `tradewindow.*` key used in Java but missing from the era's `en_us.json` fails, the
  token-name constant must equal both the language entry and the recipe's
  `custom_name`, and paint-side/click-side of both controls must both mention
  `lockSlot` and `cancelSlot`.

Not verified here: in-game behaviour. TESTING.md §0 is the checklist for that, and it
is the one thing this build cannot check for itself.

### Known trade-offs in this jar (unchanged from v1.2.0)

- The shared core is still compiled into the jar even though this design uses only
  `TradeSide` from it; it is inert, and excluding it per-target would weaken the
  guarantee the other seven trees rely on.
- Locking cannot be undone except by cancelling. That is deliberate (see
  DESIGN_DECISIONS.md #22): an offer that can change after a lock is a scam window.

---

## v1.2.0 — the 1.21.11 vanilla-token rewrite (superseded by v1.3.0)

This pass rebuilt **one** target: Minecraft 1.21.11. The other fourteen targets are
untouched and still implement the 1.1.0 design; their sections below remain the
record of that build.

| | |
|---|---|
| Command | `./gradlew :v1_21_11:build` |
| Result | **BUILD SUCCESSFUL** — `compileJava`, `processResources`, `jar`, `sourcesJar`, `remapJar` |
| Artifact | `versions/1.21.11/build/libs/tradewindow-1.2.0-1.21.11.jar` (72 KB) |
| Also produced | `tradewindow-1.2.0-1.21.11-sources.jar` |

### Why 1.21.11 diverges

Two failures forced it, and they pull in the same direction:

1. **`NullPointerException: Item id not set` at start-up.** The `TradeTokenItem`
   was constructed without its registry key. Fixed on every era by attaching the key
   first - and then made impossible on 1.21.11 by deleting the item entirely.
2. **A custom item id is a client-compatibility hazard.** A vanilla client that has
   never heard of `tradewindow:trade_token` is kicked when the registry sync arrives.
   Registering nothing removes the class of bug rather than one instance of it.

So the 1.21.11 jar now registers **nothing**: the token is a renamed Ghast Tear from
a data-pack recipe, the window is the vanilla double chest, the labels are renamed
items in locked slots, and Lock / Unlock / Cancel / Accept / Decline are vanilla chat
click events running `/trade` sub-commands.

### What was verified

- `:v1_21_11:compileJava` clean, then a full `:v1_21_11:build`.
- Jar contents: one `main` entrypoint, `"environment": "server"`, no `client`
  entrypoint, no mixins, and **no** `com/tradewindow/item`, `screen` or `network`
  classes. Every JSON in the jar parses.
- `fabric.mod.json` expands to `"version": "1.2.0"` and
  `"minecraft": "1.21.11"`.
- Recipe and advancement are packaged at
  `data/tradewindow/recipe/trade_token.json` and
  `data/tradewindow/advancement/recipes/misc/trade_token.json`.

Not verified here: in-game behaviour. TESTING.md §0 is the checklist for that.

### Known trade-offs in this jar

- `shared/src/main/java` is still added to every target's source set by
  `gradle/target-module.gradle`, so the shared core (`TradeRegistry`, `TradeSwap`,
  `TradeSessionCore`, the history stores, `TradeConfigData`) is packaged into the
  1.21.11 jar even though this design only uses `TradeSide` from it. It is inert -
  nothing loads or calls those classes - but it is dead weight that could be
  excluded for this target alone.
- The 1.21.11 tree no longer uses `CancelReason`, `TradeSwap`, `TradeLayout`,
  `TradeRegistry`, `TradeSessionCore`, `ItemSnapshot` or the trade log; the
  accompanying documentation was rewritten in place rather than left describing
  code that is gone.

---

## Verified: 14 of 14 targets

| Target | Jar | Loader constraint | Era tree | Obfuscated |
|---|---|---|---|---|
| 1.21.1 | `tradewindow-1.4.1-1.21.1.jar`, 87 KB | `>=0.19.3` | `eras/1.21` | yes |
| 1.21.2 | `tradewindow-1.4.1-1.21.2.jar`, 87 KB | `>=0.19.3` | `eras/1.21.2` | yes |
| 1.21.3 | `tradewindow-1.4.1-1.21.3.jar`, 87 KB | `>=0.19.3` | `eras/1.21.2` | yes |
| 1.21.4 | `tradewindow-1.4.1-1.21.4.jar`, 87 KB | `>=0.19.3` | `eras/1.21.2` | yes |
| 1.21.5 | `tradewindow-1.4.1-1.21.5.jar`, 87 KB | `>=0.19.3` | `eras/1.21.5` | yes |
| 1.21.6 | `tradewindow-1.4.1-1.21.6.jar`, 87 KB | `>=0.19.3` | `eras/1.21.6` | yes |
| 1.21.7 | `tradewindow-1.4.1-1.21.7.jar`, 87 KB | `>=0.19.3` | `eras/1.21.6` | yes |
| 1.21.8 | `tradewindow-1.4.1-1.21.8.jar`, 87 KB | `>=0.19.3` | `eras/1.21.6` | yes |
| 1.21.9 | `tradewindow-1.4.1-1.21.9.jar`, 87 KB | `>=0.19.3` | `eras/1.21.9` | yes |
| 1.21.10 | `tradewindow-1.4.1-1.21.10.jar`, 87 KB | `>=0.19.3` | `eras/1.21.9` | yes |
| 1.21.11 | `tradewindow-1.4.1-1.21.11.jar`, 87 KB | `>=0.19.3` | `eras/1.21.11` | yes |
| 26.1 | `tradewindow-1.4.1-26.1.jar`, 86 KB | `>=0.19.3` | `eras/26.1` | **no** |
| 26.2 | `tradewindow-1.4.1-26.2.jar`, 86 KB | `>=0.19.3` | `eras/26.2` | **no** |
| 26.3 | `tradewindow-1.4.1-26.3.jar`, 86 KB | `>=0.19.3` | `eras/26.3` | **no** |

```bash
versions/<minecraft version>/build/libs/tradewindow-1.4.1-<minecraft version>.jar
```

Every jar carries the **same shared core classes** and declares an exact
`minecraft` dependency, so a mismatched jar refuses to load rather than misbehaving.
The core's **73 unit tests pass** (`./gradlew :shared:test`).

The class-file version is checked per target, because it is what decides whether a jar
loads at all: **major 61** (Java 21) for the 1.21.x jars and **major 69** (Java 25) for
the 26.x ones.

---

## v1.1.0 — the vanilla-container rewrite

The custom client GUI (`TradeScreen`, its confirm buttons, its two packets and the
client entrypoint) was deleted and replaced with a plain vanilla container, so an
**unmodified vanilla client** can trade.

- The window is a `TradeMenu` opened as `MenuType.GENERIC_9x6` — a double chest.
- Slots **0–17** are player A, **18–35** are locked glass, **36–53** are player B,
  then the viewer's own 36 inventory slots.
- `TradeSlot` overrides `mayPlace` / `mayPickup` / `isActive` / `allowModification`
  and `TradeMenu` overrides `quickMoveStack`, so every interaction path is gated
  server-side.
- Confirm / cancel / un-confirm are clickable chat links running `/trade confirm`,
  `/trade cancel` and `/trade unconfirm`.
- `fabric.mod.json` is `"environment": "server"` on all 15 targets; the `client`
  entrypoint is gone.

### Four per-era API splits this exposed

The first clean build of the rewrite surfaced four genuine version splits. All are
resolved by `tools/generate_era_dialects.py`, which derives each era's
`TradeManager` / `TradeWindow` / `TradeInventory` from the 1.21.11 reference:

| Split | Older form | Newer form | Boundary |
|---|---|---|---|
| `ClickEvent` | `new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd)` | `new ClickEvent.RunCommand(cmd)` | 1.21.5 |
| `HoverEvent` | `new HoverEvent(HoverEvent.Action.SHOW_TEXT, c)` | `new HoverEvent.ShowText(c)` | 1.21.5 |
| item key | `new Item.Properties().stacksTo(16)` (1.20.1) / `createProperties()` (1.21) | `createProperties(ResourceKey<Item>)` | 1.21.2 |
| glass pane | `Items.WHITE_STAINED_GLASS_PANE` | `Items.GLASS_PANE` | 26.x |

Two further findings, both established by compiling against the real jars:

- **`MenuProvider` has no `getMenuType()` in any of these versions.** The menu type
  is taken from the `AbstractContainerMenu` itself, so the provider must not declare
  it. (`TradeMenu` passes `MenuType.GENERIC_9x6` to `super`.)
- **`TradeSlot` / `TradeMenu` are genuinely dialect-free.** `Slot.allowModification`,
  `Slot.mayPickup`, `Slot.isActive` and `Slot.setByPlayer` all compile unchanged on
  **every** era including 1.20.1, so the slot code is copied verbatim.

### Generator idempotency note

The generator reads the 1.21.11 reference and writes all eight eras. Because the
reference itself is written back in the *newer* `HoverEvent.ShowText` form, the
hover transform must be **bidirectional** — it converts `ShowText` *back* to the
`Action.SHOW_TEXT` constructor for the 1.20.1 / 1.21 / 1.21.2 eras. A one-way
transform silently leaves `ShowText` in place and fails to compile.

---

## Fix 1 — the startup crash (`Item id not set`)

### Cause

From **1.21.2** Mojang gave `Item.Properties` a `ResourceKey<Item> id` field, and
`Item`'s constructor does `Objects.requireNonNull` on it. The mod built the Trade
Token in a static field initialiser, so `TradeWindow.<clinit>` constructed an `Item`
whose settings carried no key:

```
java.lang.NullPointerException: Item id not set
    at net.minecraft.world.item.Item.<init>(Item.java:144)
    at com.tradewindow.item.TradeTokenItem.<init>(TradeTokenItem.java:42)
    at com.tradewindow.TradeWindow.<clinit>(TradeWindow.java:59)
```

This is the one break in this version range that **compiles cleanly and still
crashes** — the compiler cannot see it.

### Fix, on every era at or above 1.21.2

```java
// A key, not an Item.
public static final ResourceKey<Item> TRADE_TOKEN_KEY =
        ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "trade_token"));

// Assigned in onInitialize(), never at class-init time.
public static TradeTokenItem TRADE_TOKEN;

@Override
public void onInitialize() {
    TRADE_TOKEN = new TradeTokenItem(TradeTokenItem.createProperties(TRADE_TOKEN_KEY));
    Registry.register(Registries.ITEM, TRADE_TOKEN_KEY, TRADE_TOKEN);
}

static Item.Properties createProperties(ResourceKey<Item> key) {
    return new Item.Properties().setId(key).stacksTo(16);   // setId FIRST
}
```

- `TradeTokenItem`'s constructor now does nothing but `super(settings)`.
- The key is applied before the Item exists, and the Item is registered under that
  exact key, so the id an item is built with cannot drift from the id it is
  registered under.
- Applied to `eras/1.21.2`, `1.21.5`, `1.21.6`, `1.21.9`, `1.21.11`, `26`.

**1.20.1 and 1.21.1 are deliberately unchanged.** `Item.Properties` has no `id`
field in those versions and `setId` does not exist, so static-field construction is
correct there. This is a real per-era split, not a stylistic one.

### Verified, not assumed

Disassembling the compiled 1.21.11 `TradeWindow` shows `<clinit>` referencing only
`Registries.ITEM` (for the key) and `ExtendedScreenHandlerType` — and **zero**
`TradeTokenItem` constructions. The Item is built inside `onInitialize()`.

### Mapping note

The setter is **`setId`** in the official Mojang mappings used here, not
`registryKey`. The key class is `ResourceKey`, not `RegistryKey`, and there is no
`Identifier.of(...)` — the factory is `fromNamespaceAndPath`. `setId` holds from
1.21.2 through 26.3.

### Blocks

The mod registers **no blocks** — the Trade Token and the menu type are the only
registered content, so there is no `BlockItem` and no `Block.Settings` key to get
wrong. The same rule would apply if a block were added: `Block.Settings` takes its
registry key the same way, and a `BlockItem` needs
`new Item.Properties().setId(itemKey)` built from a separate `ResourceKey<Item>`.

---

## Fix 2 — Fabric Loader 0.19.3 and 0.19.4

`fabric.mod.json` now declares `fabricloader: ">=0.19.3"` on every target, and the
build pins **Loader 0.19.3** — the oldest supported version.

Pinning the oldest rather than the newest is deliberate: building against 0.19.3
proves the mod works there, and 0.19.4, 0.19.5 and anything later satisfy the `>=`
constraint automatically. Loader is version-independent, so no source changes were
needed.

`gradle/versions.gradle` carries one `loader` value used both to build and to
declare the minimum.

---

## Fix 3 — toolchain requirement (1.20.1)

1.20.1 briefly failed with:

```
Cannot find a Java installation matching: {languageVersion=17, ...}
```

Only JDK 21 and 25 exist on this machine, and `gradle/target-module.gradle` was
pinning a toolchain at every target's Java level. A pin is only *necessary* for the
Java 25 targets — `--release 25` cannot be produced by an older compiler. Anything
below 25 compiles fine from a newer JDK via `options.release`, so the toolchain pin
is now applied **only** when `javaRelease >= 25`.

---

## Fix 4 — `listTargets` printed its own format string

`./gradlew listTargets` output the literal `{:<10} {:<9} …` header and dropped every
value. The task passed Python-style width specifiers to SLF4J:

```groovy
logger.lifecycle('{:<10} {:<9} {:<8} {:<6} {:<14} {}', 'PROJECT', 'MINECRAFT', …)
```

`logger.lifecycle` only substitutes **bare `{}`**; `{:<10}` is not a placeholder, so
the string was printed verbatim and the arguments were discarded. Padding is now
done with `String.format(Locale.ROOT, '%-10s %-9s …', …)` and the finished line is
handed to `lifecycle`. The task now prints the full 15-row matrix.

`buildAll` and `buildAll -Ponly=<era>` were checked at the same time and are
correct (`Built all 15 targets.` / `Built targets for Minecraft 1.21.6.`).

---

## Nine source trees for fourteen versions

Only `eras/1.21.11` is maintained by hand; every other tree is generated from it by
`tools/port_eras.py`, whose docstring carries the full dialect matrix and the evidence
for each column.

| Era tree | Targets | What distinguishes it |
|---|---|---|
| `1.21` | 1.21.1 | object recipe ingredient; chat-event constructors; `getServer()`; `selected` field |
| `1.21.2` | 1.21.2, 1.21.3, 1.21.4 | bare-string recipe ingredient |
| `1.21.5` | 1.21.5 | sealed `ClickEvent.RunCommand` / `HoverEvent.ShowText`; `getSelectedSlot()` |
| `1.21.6` | 1.21.6, 1.21.7, 1.21.8 | `Commands.hasPermission(int)` permission gate |
| `1.21.9` | 1.21.9, 1.21.10 | `level().getServer()`; `ResolvableProfile.createResolved()` |
| `1.21.11` | 1.21.11 | `Identifier`; `level().playSound()` — **the reference** |
| `26.1` | 26.1 | unobfuscated; `ContainerInput`; `typeHolder()` |
| `26.2` | 26.2 | `Items.CONCRETE.green()` / `Items.STAINED_GLASS_PANE.black()` |
| `26.3` | 26.3 | `Player.drop(stack, false, Prediction.SERVER_ONLY)` |

Each boundary was established by compiling against the real jars, and the two 26.x
splits were found by the compiler during this port rather than predicted. Note what is
**not** in the table any more: the sprite-and-pipeline differences (`blit`,
`blitSprite`, `GuiGraphicsExtractor`) went with the custom GUI, and the item-key split
goes with the registered item.

---

## Building

```bash
./gradlew buildAll                  # all 14 targets + the 73 core tests
./gradlew buildAll -Ponly=1.21.6    # one era's targets
./gradlew :v1_21_11:build           # one target
./gradlew listTargets               # print the matrix
```

Requirements:
- **JDK 21** runs Gradle and compiles the 1.21.x targets via `--release`.
- **JDK 25** is required only for the 26.x targets. Point Gradle at it with
  `org.gradle.java.installations.paths` in `gradle.properties`.
- **Fabric Loader 0.19.3 or newer.**

### Environment notes

- Each target needs its own ~200 MB Minecraft toolchain; a cold `buildAll`
  downloads roughly 3 GB.
- The sandbox intermittently denies Gradle writes outside the workspace.
  `org.gradle.configureondemand=true` is set in `gradle.properties` so a
  single-target build does not resolve all fourteen toolchains at once.
- Windows `javap` needs `C:/...` paths, never Git-Bash `/c/...` ones.
