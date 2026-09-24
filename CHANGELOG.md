# Changelog

All notable changes to Trade Window are documented here.
This project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.3] - 2026-09-24

**The token's name and lore were rendering as raw JSON on every target but one.**
1.4.2 fixed the recipe encoding for 1.21.1 by switching every target to the escaped
string form - and that form is *worse* everywhere else, because from 1.21.2 the
component codec reads a string as **literal text**. The result was a perfectly valid
recipe whose output was a Ghast Tear named
`{"text":"Trade Token","color":"red","italic":false}`, braces and all.

The two forms are not interchangeable and the rule is not uniform:

| | 1.21.1 | 1.21.2 and later (incl. 1.21.11, 26.x) |
|---|---|---|
| component **object** | rejected - `Not a string` - recipe dropped | **correct**: a styled name |
| escaped **string** | **correct**: parsed as JSON | accepted, read as **literal** - raw JSON as the name |

Each side has exactly one usable spelling, so the generator now writes the form per
era: the flat string for `eras/1.21` (1.21.1 alone), a component object for the other
eight trees. Every version was verified by booting a real server with a deliberately
*invalid* JSON string - a parse error proves the string is parsed, silence proves it is
literal - and by loading the object form, which 1.21.1 rejects outright.

### Fixed

- **The Trade Token is named and described correctly on all fourteen targets.**
  26.2 (the reported case) and every other 1.21.2+ target now carry the component
  object; 1.21.1 keeps the escaped string, which is the only form its codec parses.
- **The self-check from 1.4.2 was tightened to match.** It no longer asserts one form
  everywhere - it reports the form this jar uses and whether it is the right one for
  the Minecraft version being loaded, and errors with the specific consequence.

### Added

- **A startup self-check for the token recipe** (`TokenRecipeCheck`). It reads the
  recipe out of the mod's own jar and logs one line at every start:
  `Trade Token recipe check: object text components, correct for Minecraft 26.2`, or an
  error naming the consequence if the encoding does not fit the running version. The
  failure it guards against is invisible to the compiler and to the build, and it
  shipped twice; now it cannot ship silently.
- The packaging gate asserts the component form for every target **and** for every era
  tree, and fails if a single tree ever serves targets that need different forms.

### Changed

- `mod_version` is **1.4.3**; the packaging gate now reads that version from
  `gradle.properties` instead of duplicating it.

## [1.4.2] - 2026-09-24

**The mod did not work at all when the server was your own computer.** Booting a real
server per target found three faults that no compiler and no unit test can see, because
all three live in data files that are read at runtime:

1. the recipe's components were written in a form 1.21.1 cannot parse;
2. the advancement's `recipe_unlocked` condition used the wrong key on 26.3, which is
   fatal there - the server refused to finish loading;
3. the manifest said `"environment": "server"`, and Fabric Loader **skips** a mod with
   that value on a client - so on a single-player world, which is a client, the mod was
   never loaded and every command and right-click was silently ignored.

### Fixed

- **The recipe now loads on 1.21.1 through 1.21.10.** Its components were JSON
  *objects*; those versions' component codec wants the escaped-*string* form. On 1.21.1
  the old file produced `Parsing error loading recipe tradewindow:trade_token` and the
  recipe simply did not exist - no crash, no chat line, just a Trade Token nobody could
  craft. One encoding now works on every target, so the reference file and all nine
  generated trees carry it.
- **26.3 starts again.** The advancement's `recipe_unlocked` condition names its recipe
  with `recipe` up to 26.2 and **`recipes`** on 26.3. The old key made 26.3 abort with
  `Failed to load datapacks, can't proceed with server load` - it would not boot at all.
- **The mod loads on a client, so single-player works.** `"environment": "server"`
  makes Fabric Loader skip the mod entirely on a client. The manifest is now `"*"`: the
  same `main` entrypoint on both sides, still no `client` entrypoint and still no client
  code. Every callback is a server-side Fabric API event or is guarded by
  `!world.isClientSide()`, so on a client the mod initialises, writes its config, and
  then does nothing. Dedicated servers are unaffected - this only ever *added* a place
  the mod runs.

### Added

- **A boot test per target.** Each of the fourteen was started as a real server and its
  log read for the recipe, the advancement and the mod's own load line:
  **all fourteen start with zero recipe-parse errors and zero registry errors.**
  This is what caught the three faults above; the packaging gate cannot see any of them.

### Changed

- `mod_version` is **1.4.2**; the jars are `tradewindow-1.4.2-<mc>.jar`.

## [1.4.1] - 2026-09-23

**All fourteen targets.** Three things: the shipped config defaults are retuned for a
live server; the distance rule is now asked *before* a Trade Token is spent instead of
only by the tick loop - the one path that could charge a player for a trade that never
happened; and the 1.21.11 design was ported to every other target, so
`tradewindow-1.4.1-<version>.jar` is now one design for 1.21.1 through 26.3 instead of
one design on a single version and the old one everywhere else.

### Fixed

- **A Trade Token is no longer spent on a trade that cannot open.** With
  `crossDistanceTrading` off, accepting a request from out of range used to consume the
  requester's tear, open the window, and let the next tick cancel the trade: the player
  paid for a window they never got to use. The distance question is now answered before
  the spend - and before a request is even sent - and a refused acceptance puts the
  request back on the pending queue unchanged, so walking closer and pressing
  `[ Accept ]` again inside its timeout still works and still costs exactly one token.

### Changed

- **New defaults:** `tradeTimeoutSeconds` 30 → **15**, `guiTimeoutSeconds` 60 →
  **120**, `maxDistanceBlocks` 8 → **20**, `cancelOnMove` true → **false**. A request is
  answered inside fifteen seconds or expires silently, which is all the time a chat line
  needs and doubles as a bound on how long a token is reserved. The window gets two
  minutes, because that is where the actual work happens. The distance rule ships
  **off**: `crossDistanceTrading` already defaulted to `true`, and `cancelOnMove` now
  defaults to `false` as well, so both switches have to be turned over before range can
  end a trade.
- **The distance rule has one definition.** New `TradeDistance` in the shared core: a
  pure function over the two switches, the radius and the pair's separation, called by
  the request, the acceptance and the tick loop so the three cannot disagree. It is
  unit tested, and the packaging gate now fails if an acceptance ever checks the rule
  *after* the token spend.
- **Two new refusals**, both free of charge: `You are too far away to trade — move
  within N blocks of the other player.` to whoever acted, and `X is too far away to
  trade — move within N blocks of them.` to the other side.

### Ported to every target

Until this release only 1.21.11 carried the rewritten design; the other thirteen
targets still shipped the v1.1.0 code — a registered `tradewindow:trade_token` item, a
drawn client GUI, two packet classes and the old fixed grid. They now all ship the
current design, and the fourteen jars listed below are built by one command.

- **One reference tree, one generator.** `eras/1.21.11` is the only hand-written
  Minecraft-facing tree; `tools/port_eras.py` derives the other nine from it by
  applying a per-era dialect (a small, enumerated table of substitutions). A fix made
  once reaches every target.
- **Fourteen targets, nine source trees.** 1.21.1; 1.21.2–1.21.4; 1.21.5;
  1.21.6–1.21.8; 1.21.9–1.21.10; 1.21.11; 26.1; 26.2; 26.3.
- **26.x split into three trees, not one.** 26.2 collapsed the sixteen colour variants
  of each item into `ColorCollection` records (`Items.CONCRETE.green()`), which every
  spacer and both painted controls touch, and 26.3 gave `Player.drop` a `Prediction`
  argument. The old single `eras/26` tree could not serve all three — and only ever
  claimed to be verified for 26.3.
- **1.20.1 dropped.** It predates data components, which the token's `custom_name`,
  `lore` and `max_stack_size` all depend on.
- **No target registers anything, on any version.** No item, block, entity or packet;
  `"environment": "server"` with a single `main` entrypoint, asserted for all fourteen
  jars.
- **The packaging gate was rewritten** for what actually ships: 1314 checks across the
  fourteen jars, including the class-file major version of every jar (61 for the Java 21
targets, 69 for the Java 25 ones) — the property that silently decides whether a jar
loads at all.

## [1.4.0] - 2026-09-23

**Minecraft 1.21.11 only.** Fourth pass over the 1.21.11 target: the window is now
mirrored per player, the status paper is gone, the token stacks to 16, and the four
operator tools live behind `/tradeadmin`. The other fourteen targets kept the 1.1.0
design described below until 1.4.1 ported this one to all of them; this section is the
contract for `tradewindow-1.4.0-1.21.11.jar`.

### Added

- **A mirrored view: your offer is always the middle rows.** Each player now gets
  their own menu instance over the same data. Rows 2–3 (slots 9–26) always hold the
  offer of the player looking at the window and rows 5–6 (slots 36–53) always hold
  their partner's, whoever they are, so neither player has to translate a shared
  layout. The header rows follow suit: your own head is at slot 4 and your partner's
  at slot 31, in both windows.
- **`/tradeadmin`, four commands at permission level 2:**

  | command | what it does |
  |---|---|
  | `/tradeadmin crossdistance <on\|off>` | allow or forbid trading at any distance; written to the config file immediately |
  | `/tradeadmin list` | every live trade, one line each: names, elapsed time, both lock states |
  | `/tradeadmin spectate <player>` | watch that player's trade read-only; the trade's state, timer and locks are untouched |
  | `/tradeadmin history [player]` | the last 20 completed trades, newest first, optionally for one player |
- `crossDistanceTrading` in the config, default `true`: when on, the distance rule
  does not run at all. Turning it off restores the classic `maxDistanceBlocks` check.
- `TradeHistory`, a 20-trade in-memory ring that is also written to
  `tradewindow-history.jsonl` (or SQLite when `useDatabase` is on and a driver is
  present) and reloaded at start-up.
- The token now carries `minecraft:max_stack_size: 16`, so a stack of sixteen
  Trade Tokens is one slot instead of sixty-four.

### Changed

- **The container title is exactly `Trading Window`** — no player names, no "vs".
- **Row 1 and row 4 have a new layout**, both from the viewer's point of view:

  | slot | contents |
  |---|---|
  | row start + 0, 1, 2 | black stained glass pane named `" "` |
  | row start + 3 | green concrete `LOCK`, lore "Click to lock your side" |
  | row start + 4 | the player that row belongs to, as a real player head |
  | row start + 5 | red concrete `CANCEL`, lore "Click to cancel the trade" |
  | row start + 6, 7, 8 | black stained glass pane named `" "` |
- **The status paper is gone.** The green `LOCK` block turning into a green pane
  reading `LOCKED` is the whole state display, for both players.
- **Player heads carry no lore** — the tooltip is the player's name and nothing else.
- The 1.21.11 config is now the shared, unit-tested model rather than a private
  three-field copy, so every documented option exists and is honoured.
- `/trade` is down to `accept` and `decline`: locking and cancelling were already
  clicks, and everything else is now an operator command.
- Chat is exactly eight lines — request, start, own lock, complete, cancel, timeout,
  disconnect and (only when the distance rule is on) moved too far away.

### Fixed

- **The window no longer says which player is which by side.** A player used to see
  their partner's offer above their own half the time, depending on whether they had
  sent or accepted the request.
- Token identity now checks the stack-size component as well as the name, so a plain
  Ghast Tear — or one renamed in an anvil — cannot be spent as a token.

## [1.3.0] - 2026-09-23

**Minecraft 1.21.11 only.** Third pass over the 1.21.11 target: the window now
carries its own buttons, the token is spent when a trade actually opens, and every
string is read from the language file. The other fourteen targets keep the 1.1.0
design described below; this section is the contract for
`tradewindow-1.3.0-1.21.11.jar`.

### Added

- **LOCK and CANCEL are clickable blocks inside the window.** The chest's first row
  is player 1's header and its fourth row is player 2's; each holds that player's
  real player head, their status paper, a green concrete `LOCK` block and a red
  concrete `CANCEL` block, with black separator panes filling the rest. Clicking the
  green block locks the clicking player's own side; clicking either red block cancels
  the trade. Nothing is typed, no chat button is pressed, and the window never has to
  close first.
- `TradeText`, which reads `assets/tradewindow/lang/en_us.json` from the jar at
  start-up. Java holds language keys and format arguments only: no sentence is
  written in code. A key that is missing is logged once and caught by
  `tools/verify_packaging.py`, rather than reaching a player as
  `tradewindow.chat.complete`.

### Changed

- **New 54-slot layout.** Each player's header row sits directly above their offer:

  | slots | contents | who may touch it |
  |---|---|---|
  | 0–8 | player 1's header: head at 0, status at 1, `LOCK` at 4, `CANCEL` at 5, panes at 2–3 and 6–8 | nobody |
  | 9–26 | player 1's offer (rows 2–3) | player 1 only |
  | 27–35 | player 2's header: head at 27, status at 28, `LOCK` at 31, `CANCEL` at 32, panes at 29–30 and 33–35 | nobody |
  | 36–53 | player 2's offer (rows 5–6) | player 2 only |

  The heads carry each player's real name, red for the top half and green for the
  bottom, and each status paper sits immediately beside its owner's head, so the pair
  reads as "Alice: EDITING" without either item repeating the name. Both head and
  paper are painted from the live player's own `GameProfile`, so the vanilla client
  renders the real skin.
- **The LOCK block is spent when it is used.** Locking your side turns your status
  paper green and replaces your green concrete with a lime pane reading `LOCKED`, so
  there is nothing left to click.
- **Locking is one-way within a trade.** `unlock` is gone: a locked offer cannot be
  changed by anyone, and a player who locks by mistake cancels instead. That removes
  the half-unlocked offer the old `unlock` created.
- **The token is spent when the window opens.** Right-clicking a player (or typing
  `/trade <player>`) costs nothing; the request remembers which inventory slot the
  tear was in, and accepting it spends exactly one token from that slot — the one the
  sender used. If it is no longer there, no window opens and both players are told.
- **The command tree is three commands:** `/trade <player>`, `/trade accept`,
  `/trade decline`. Every in-window action is a click, so it needs no command.
- **Chat carries only what the window cannot show.**

  ```
  [Trade] Alice wants to trade. [ Accept ] [ Decline ]
  [Trade] Trade started with Bob.
  [Trade] You locked your side.
  [Trade] Trade complete.
  [Trade] Trade cancelled.
  [Trade] Trade timed out.
  [Trade] Other player disconnected. Trade cancelled.
  ```

  Still silent: placing and removing items, your partner locking (their paper turns
  green), the requester's own request (their window is the answer), and the last
  thirty seconds. `[Trade]` is gold, body text is white, errors are red, and buttons
  are bracketed with one space on each side.

### Removed

- `/trade lock`, `/trade unlock` and `/trade cancel`, and the whole
  confirm/unconfirm vocabulary they replaced.
- The `[ Lock ]`, `[ Unlock ]` and `[ Cancel ]` chat buttons. Only `[ Accept ]` and
  `[ Decline ]` remain, because a request has to be answerable before any window
  exists.
- The "Buttons are in chat" status-paper lore, and `TradeSession.unlockBoth()`.

### Fixed

- **A player's own lock is announced once, not once per click.** The menu ignores a
  click on an already-locked side, so a second click (or a forged one) neither
  re-locks nor re-announces anything.
- **A stranger's click cannot reach your side.** The green block only responds to the
  player whose header row it sits in, so nobody can lock the other player's offer
  from across the window. Either red block still cancels, which is what "cancel the
  whole trade" has to mean.

## [1.2.0] - 2026-09-23

**Minecraft 1.21.11 only.** This release rebuilds the 1.21.11 target around a
design that cannot crash on start-up and cannot kick a vanilla client. The other
fourteen targets keep the 1.1.0 design described below; this section is the
contract for `tradewindow-1.2.0-1.21.11.jar`.

### Fixed

- **`NullPointerException: Item id not set` is gone, because the custom item is
  gone.** `eras/1.21.11` no longer has an `item/` package, a `TradeTokenItem` or
  any call to `Registry.register`. The 1.21.11 jar adds nothing to any registry,
  so the client-side kick this item would also have caused ("Received a registry
  entry that is unknown to this client") is impossible by construction.

### Changed

- **The token is a renamed Ghast Tear**, produced by a data-pack recipe: 1 Ghast
  Tear + 1 Gold Ingot (shapeless) → 1 Ghast Tear with `minecraft:custom_name`
  `{"text":"Trade Token","color":"red","italic":false}` and gray italic lore
  "Right-click a player to trade". A stack counts as a token when it is a Ghast
  Tear whose hover name is exactly `Trade Token`; renaming one in an anvil stops
  it being a token. The token is never consumed, so a crafted one keeps working.
- **New 54-slot layout.** Both players see the same grid, because a double chest
  cannot be arranged differently per viewer:

  | slots | contents | who may touch it |
  |---|---|---|
  | 0–17 | player 1's offer (rows 1–2) | player 1 only |
  | 18–35 | player 2's offer (rows 3–4) | player 2 only |
  | 36–44 | labels: player 2's head at 36, black panes, player 1's head at 44 | nobody |
  | 45–53 | status: player 1's paper at 45, black panes, player 2's paper at 53 | nobody |

  Heads are real player heads carrying the live player's own `GameProfile`, so the
  vanilla client renders the real skin. Status papers are named
  `<Name>: EDITING` (yellow) or `<Name>: LOCKED` (green) - with a name on each, the
  two papers cannot be confused even though both players see both of them.
- **Six commands, and only six.** `/trade <player>`, `/trade accept`,
  `/trade decline`, `/trade lock`, `/trade unlock`, `/trade cancel`. `confirm`,
  `unconfirm`, `toggle` and `history` are gone, along with the trade log, the
  history stores and the SQLite backend.
- **Chat carries events, not state.** One line with clickable buttons at start,
  at lock, at unlock, on completion, on cancellation, on timeout. Nothing is said
  when an item is placed or removed, the partner locking says nothing, and the
  30 s / 10 s countdown warnings are gone. The window shows the state.
- **Unlocking clears both sides.** If one player changes their mind after both had
  locked, the other side is unlocked too, so nobody is left believing a
  half-changed offer is about to be swapped.
- **`/trade cancel`, closing the window, death, disconnect and a 60-second timeout
  all return every item to its owner.** Walking further than 8 blocks apart ends
  the trade silently; timeout, death and disconnect say so in one line. Items that
  cannot be handed over because their owner is gone are held on disk
  (`tradewindow-pending-returns.json`) and returned on their next login, including
  across a server restart.
- **Config is three keys**: `timeoutSeconds` (60), `maxDistanceBlocks` (8),
  `requireToken` (true). The options the old build parsed but never honoured are
  gone from the file rather than left pretending to work.

### Verified

- `./gradlew :v1_21_11:build` - BUILD SUCCESSFUL, producing
  `tradewindow-1.2.0-1.21.11.jar` (72 KB).
- The jar declares `"environment": "server"`, one `main` entrypoint, no `client`
  entrypoint, no mixins, and contains no item, screen or network classes.

## [1.1.0] - 2026-09-22

**The custom client GUI is gone.** Trading now uses a plain vanilla container, so
an **unmodified vanilla client** can trade with no client-side mod installed. The
old `TradeScreen`, its confirm buttons, its two custom packets and the whole
client entrypoint were deleted; every interaction now flows through the server and
ordinary `/trade` sub-commands.

### Changed

- **Vanilla double-chest GUI.** The trade window is a `TradeMenu` opened as
  `MenuType.GENERIC_9x6`, which every client already knows how to render. Slot
  layout mirrors a double chest exactly:
  - slots **0–17** — player A's offer,
  - slots **18–35** — locked white-glass divider,
  - slots **36–53** — player B's offer,
  - followed by the viewer's own 36 inventory slots.
- **Right-click detection moved to the server.** With no client mod there is
  nothing to detect the interaction on the client, so the server now watches its
  own interaction event (`UseEntityCallback`). Right-clicking another player
  while holding a Trade Token sends the request exactly as before; the callback
  only observes (it always returns `PASS`), so it never interferes with other
  right-click behaviour.
- **Chat-driven controls.** Confirm, cancel and un-confirm are clickable chat
  links (`ClickEvent.RunCommand`) that run `/trade confirm`, `/trade cancel` and
  `/trade unconfirm`. The container title is `Trade: You vs PlayerName`.
- **Server-side validation moved into the slots.** `TradeSlot` overrides
  `mayPlace`, `mayPickup`, `isActive` and `allowModification`, and `TradeMenu`
  overrides `quickMoveStack`, so clicking, shift-clicking, dragging, number-key
  swaps and hopper insertion are all gated by the same rules — a player may only
  touch their own 18 slots, and only while unconfirmed.
- **Timer warnings in chat.** The remaining time is announced at **30 s** and
  **10 s**; on expiry both players see "Trade expired" and the window closes with
  every item returned.
- **Closing the window ends the trade.** `TradeMenu.removed()` cancels the session
  and returns every item, so a player who closes the chest (Esc, or the inventory
  key) is released immediately instead of waiting out the timeout.
- **`fabric.mod.json` is now `"environment": "server"`** on all 15 targets, with
  the `client` entrypoint removed. Install it on the server only.
- **Recipe-book advancement** added for the Trade Token recipe, in the vanilla
  shape for each era (`minecraft:recipe_unlocked` trigger, OR requirements). The
  data-pack folders were also corrected per version: `recipes/` + `advancements/`
  on 1.20.1, and the singular `recipe/` + `advancement/` from 1.21.1 on. The 1.21.1
  recipe additionally needed the object ingredient form.

### Added

- `/trade confirm` — lock your offer.
- `/trade unconfirm` — unlock it again (also clears your partner's confirmation).

### Removed

- `TradeScreen`, `ConfirmButton`, the custom GUI textures and the atlas entries.
- `TradeUpdatePacket` / `TradeResponsePacket` and all custom networking.
- The client entrypoint (`TradeWindowClient`).

### Fixed

- **Start-up crash from 1.21.2 through 26.3: `NullPointerException: Item id not
  set`.** Since 1.21.2 an item derives its description id and default model from
  its own registry key, and `Item`'s constructor reads that key out of the
  `Item.Properties` it is given. The Trade Token was built from properties that
  carried no key, so the game crashed while the mod's entrypoint class was still
  initialising — before `onInitialize` ever ran, and therefore identically on
  every one of the thirteen affected targets. The token now carries the same
  `ResourceKey<Item>` that it is registered under. 1.20.1 and 1.21.1 never
  required a key and are unchanged.
- **Fabric Loader 0.19.3 / 0.19.4 support.** Every target declares
  `fabricloader: ">=0.19.3"` and the build pins the oldest supported loader.
- **Slot-ownership rule extracted and hardened.** The rule that decides who may
  touch which slot — the anti-scam boundary — moved out of `TradeMenu` into
  `com.tradewindow.core.TradeLayout`, where it is now unit tested (7 new tests).
  The extraction also fixed a latent bug: the old inline rule returned `true` for
  every slot at or past the divider when the side was `null`, because the
  comparison against `PLAYER_1` degenerated. Not reachable through the current
  callers, but a footgun.

### Porting notes

The vanilla GUI exposed four genuine per-era API splits, all resolved in the
generator rather than by hand:

| Split | Old form | New form |
|---|---|---|
| `ClickEvent` | `new ClickEvent(Action.RUN_COMMAND, cmd)` (1.20.1–1.21.2) | `new ClickEvent.RunCommand(cmd)` (1.21.5+) |
| `HoverEvent` | `new HoverEvent(Action.SHOW_TEXT, c)` (1.20.1–1.21.2) | `new HoverEvent.ShowText(c)` (1.21.5+) |
| item key | `new Item.Properties().stacksTo(16)` (1.20.1), `createProperties()` (1.21) | `createProperties(ResourceKey<Item>)` (1.21.2+) |
| glass pane | `Items.WHITE_STAINED_GLASS_PANE` (≤1.21.x) | `Items.GLASS_PANE` (26.x, coloured panes collapsed) |

`MenuProvider` has no `getMenuType()` in any of these versions — the menu type is
taken from the `AbstractContainerMenu` itself.

## [1.0.0] - 2026-09-22

First release. Built simultaneously for fifteen Minecraft versions, from 1.21.1
through 26.3, spanning the point where the game stopped being obfuscated. The
trade protocol, the item-movement algorithm and every policy check are shared
verbatim across all of them.

### Added

**Trade Token**
- Craftable item, `tradewindow:trade_token`, stack size 16.
- Shapeless recipe: 1 paper + 1 gold ingot yields 4 tokens.
- Right-clicking another player while holding one sends a trade request.
- The token is consumed only when the trade window actually opens. Declined,
  queued and timed-out requests cost the sender nothing.
- The recipient gets a chat prompt with clickable `[Accept]` and `[Decline]`
  buttons.
- Requests expire after 30 seconds (configurable).

**Trade window**
- Server-authoritative container with two 4x9 grids, 36 slots per side.
- Each player can only touch their own 36 slots, enforced in the slot itself so
  that clicking, shift-clicking, dragging and hopper insertion are all covered.
- A side's items are frozen the moment that player confirms.
- Both sides must confirm before anything moves.
- Un-confirming resets *both* confirmations, so neither player can change their
  offer after the other has committed to it.
- 60-second countdown (configurable) with the remaining time shown in the
  divider.
- Confirm buttons render in three states: grey (unconfirmed), green (this side
  confirmed), dark green (locked, both sides confirmed).

**Atomicity and anti-scam**
- Items are moved as the very stacks that were taken, never as fresh copies, so
  no code path can duplicate an item.
- Every precondition is re-checked on the server at the moment of commit;
  nothing the client says is trusted.
- Failed validation rolls the whole trade back and returns every item.
- Item data (1.20.1 NBT / 26.3 data components) is preserved exactly, and the
  log records a fingerprint of it so a lost enchantment is detectable after the
  fact.
- Every completed and cancelled trade is appended to `tradewindow.log` with a
  timestamp, both player names and the items exchanged.
- Optional SQLite trade history (`useDatabase`), used by `/trade history`.

**Commands**
- `/trade <player>` — send a request.
- `/trade accept` / `/trade decline` — answer the request currently shown.
- `/trade toggle` — stop or resume receiving requests.
- `/trade cancel` — leave the trade you are in.
- `/trade history` — list the last 10 completed trades.
- All at permission level 0.

**Edge cases handled**
- Disconnect, death, distance beyond `maxDistanceBlocks`, damage (configurable),
  cross-dimension trading (configurable), creative/survival mismatch
  (configurable), self-trade, trading while already trading, two players
  requesting the same third player (FIFO queue, one prompt at a time),
  blacklisted items, value cap, full inventory on completion (overflow drops at
  the player's feet), and server restart (stranded items are written to disk and
  restored on next login).

**Testing**
- 68 JUnit tests over the shared core covering item swaps (empty, partial, full,
  non-stackable, item-data preservation, copy independence), blacklist
  rejection, over-stacking, the value cap, the confirm/unconfirm state machine,
  GUI and request timeouts, and concurrency/queueing/disconnect handling.
- `TESTING.md` manual checklist, covering each API surface rather than each
  version.

**Targets — fifteen of them, from four source trees**

| Minecraft | Java | Era tree | Obfuscated |
|---|---|---|---|
| 1.21.1, 1.21.2, 1.21.3 | 21 | `eras/1.21` | yes |
| 1.21.4, 1.21.5 | 21 | `eras/1.21.4` | yes |
| 1.21.6 … 1.21.11 | 21 | `eras/1.21.6` | yes |
| 26.1, 26.2, 26.3 | 25 | `eras/26` | **no** |
| 1.20.1 (bonus) | 17 | `eras/1.20.1` | yes |

Each target produces `tradewindow-1.0.0-<minecraft version>.jar`. The build matrix
lives in `gradle/versions.gradle`; the modules are generated from it by
`tools/generate_modules.py`.

Obfuscated targets use the `net.fabricmc.fabric-loom-remap` plugin, official
Mojang mappings and `modImplementation`. Unobfuscated targets (26.1+) use
`net.fabricmc.fabric-loom`, **no mappings line at all**, and `implementation` —
because there is nothing to remap.

### Known limitations

- Player skin heads render on 1.20.1 but not on 26.3, which resolves GUI images
  through the sprite atlas. See `PORTING_NOTES.md`.
- The SQLite backend requires a JDBC driver to be added by the server operator;
  the mod bundles no dependencies. Without one it falls back to a JSON-lines
  history file.
- Trades do not survive a server restart by design; only the items do.
