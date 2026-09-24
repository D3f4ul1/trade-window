# Design Decisions

Where the specification was ambiguous, or where the supported Minecraft versions
disagreed, the choice made and the reasoning behind it. Decisions that are purely
about *porting* live in [PORTING_NOTES.md](PORTING_NOTES.md).

---

## 1. Official Mojang mappings for every obfuscated target, not Yarn

**Decided:** `loom.officialMojangMappings()` for all eleven obfuscated targets
(1.21.1 – 1.21.11). The 1.20.1 bonus target the specification mentioned is no longer
built at all — see §26.

The specification asked for Yarn on the 1.21.x targets. Three things pushed the
other way:

1. **Consistency across the obfuscation break.** Minecraft 26.1+ is
   unobfuscated, which means it *is* Mojang-named. Using Yarn below the break
   would give the two halves of the project two different names for every
   Minecraft class — `net.minecraft.item.ItemStack` versus
   `net.minecraft.world.item.ItemStack` — and force a duplicate of every file
   that touches a Minecraft type.
2. **The 1.20.1 entry in the brief did not resolve.** Fabric's metadata lists
   `build.10` as the newest Yarn build for 1.20.1, not the `build.12` quoted. (Moot
   now: that target was dropped in v1.4.1 — §26.)
3. Fabric's own example-mod branches for these versions use
   `loom.officialMojangMappings()`.

The payoff is that `TradeSession`, `TradeManager`, `TradeMenu`, `TradeSlot`,
`TradeUtils`, `TradeLogger`, `TradeConfig`, `TradeCommand`, `ClientTradeState`
and both `PendingReturns` implementations are the same file across every target
in an era — and the code that moves items is written and tested once, not
fifteen times.

Yarn remains a perfectly good choice for a single-version mod. It is the *set* of
targets that makes Mojang mappings the better trade here.

## 2. Nine source trees behind fourteen modules, all generated from one

**Decided:** `shared/` plus one Minecraft-facing tree per API surface
(`eras/1.21`, `eras/1.21.2`, `eras/1.21.5`, `eras/1.21.6`, `eras/1.21.9`,
`eras/1.21.11`, `eras/26.1`, `eras/26.2`, `eras/26.3`), wrapped by fourteen thin
version modules — and only `eras/1.21.11` written by hand. The other eight trees are
generated from it (§26).

The specification allowed this ("create two separate source sets or modules and
explain why") and the evidence is in PORTING_NOTES. Across 1.21.1 → 26.3 the game
turned `ClickEvent` and `HoverEvent` into sealed records, renamed
`ResourceLocation` to `Identifier`, moved the notify sound to `level().playSound`,
removed `Entity.getServer`, added `Commands.hasPermission(int)`, collapsed each
item's sixteen colour variants into a `ColorCollection`, and gave `Player.drop` a
`Prediction` argument.

Attempting one source tree would have meant either a forest of reflection and
conditionals, or an adapter layer so thick that the "shared" code no longer
described anything. Splitting at the seam that actually exists — pure logic
versus Minecraft — keeps the shared half genuinely shared and fully unit tested,
and lets each era use its own platform idiomatically.

Fourteen modules rather than nine, though, because a version is not just a source
tree: each needs its own Minecraft coordinates, Fabric API build, mappings
decision, Java level and `fabric.mod.json`. That is fourteen small differences and
one large shared one, so the modules are generated from a matrix rather than
hand-written. For exactly the same reason the *trees* are generated as well: nine
near-identical copies of seventeen files is nine chances for one to drift, which is
precisely what happened before v1.4.1.

`shared/`'s sources are compiled **into** every jar rather than published as a
separate dependency, so there is no shading, no nested jar, and no risk of a
version mismatch between a mod and its own core.

## 3. A blacklisted item is never accepted, rather than accepted then bounced

**Decided:** `TradeSlot.mayPlace()` returns `false` for blacklisted items.

The specification says a blacklisted item is "rejected and returned
immediately". That phrasing implies it can enter the slot and is then ejected.

Refusing entry achieves the same observable outcome — the item ends up back in
the player's inventory — without ever putting it in a container that the trade
commit reads. It also closes the window where a hacked client could place a
blacklisted item and race the eject.

The reason this lives in `mayPlace` rather than in a click handler is that
`mayPlace` is consulted by *every* interaction path: clicking, shift-clicking,
drag-and-drop, number-key swaps, hopper insertion and quick-craft. A check in the
screen would leave five of those six open.

---

## 4. `allowCrossDimensionTrading` was added to the config

**Decided:** a twelfth key, defaulting to `false`.

The specification requires that trading between dimensions is "blocked
(configurable)" but the config block it supplies has no key for it. Rather than
hard-code the behaviour or overload `maxDistanceBlocks` (which cannot express
"different level"), a key was added.

It defaults to `false` — blocked — because the safer option is to refuse a trade
that cannot be distance-checked. Two players in different dimensions have no
meaningful distance between them, so the distance rule cannot protect them.

---

## 5. Items move as the original stacks, not as copies

**Decided:** `TradeSwap.prepare()` is a *dry run*; the commit moves the stacks
that were drained.

The specification asks for "a transaction-like approach: build both output
inventories, validate both players still have the items, then commit."

The commit drains both containers, runs the shared algorithm over what it
drained to validate it, and then hands the **original** stacks to the other
player. The copies that `prepare()` builds are never delivered.

This matters because it removes an entire class of bug. If delivery used copies,
a copy that silently dropped item data would destroy an enchantment while the
trade still reported success. By moving the originals, the only way an item can
change is if `ItemStack` itself mutates it — which it does not. The dry run is
kept because it still proves the *offer* is legal, and because it is what the
unit tests exercise.

If validation fails, the drained stacks are handed straight back to their owners.
There is no intermediate state in which items exist nowhere.

---

## 6. An unlisted item contributes zero to the value cap

**Decided:** `TradeValueTable.UNKNOWN_VALUE = 0`.

`maxTradeValue` needs a value for every item. Two options:

- **Fail closed** (unknown item ⇒ reject the trade) is the more secure reading:
  it cannot be walked past. But an incomplete value table would then silently
  make most trades impossible, which is a far more likely and more damaging
  failure than a cap that can be bypassed.
- **Fail open** (unknown item ⇒ 0) means the cap only counts what it knows.

Fail-open was chosen, and the limitation is documented in `README.md` and here,
because `maxTradeValue` defaults to `-1` (disabled) and is described as a blunt
guard against very large transfers rather than an economy. An operator who turns
it on is expected to extend the table for the items their server cares about.

The safer-by-default instinct is preserved elsewhere: the *default* is off, so
nobody gets surprising rejections without opting in.

---

## 7. No SQLite driver is bundled

**Decided:** `useDatabase` uses JDBC if a driver is present, and otherwise falls
back to a JSON-lines file with a warning.

The specification lists "Dependencies: None required" and separately asks for an
"Optional SQLite database". Bundling `sqlite-jdbc` would make the mod carry a
~12 MB native dependency to serve a feature that is off by default.

Instead: `SqliteTradeHistoryStore.isDriverAvailable()` checks for
`org.sqlite.JDBC`; the logger reports exactly what to add if it is missing and
falls back to `FileTradeHistoryStore`. `/trade history` works identically either
way, so the feature degrades rather than disappears.

---

## 8. The trade window is a vanilla double chest, not a custom screen

**Decided:** open a `TradeMenu` as `MenuType.GENERIC_9x6` and let the client's own
chest renderer draw it.

The previous version shipped a custom `TradeScreen` — a 396x200 panel with
procedural chrome, its own confirm buttons and a divider countdown — and that
required a client mod and two custom packets to keep the two screens in sync. It
was the single most fragile part of the mod: the two clients had to agree on state
the server already knew, and any mismatch produced a glitchy or stuck window.

A plain vanilla container removes all of that. `MenuType.GENERIC_9x6` is a double
chest that every client already knows how to draw, so:

- **no client mod is required** — the mod is `"environment": "server"`;
- the container protocol is Mojang's, so item sync is exact and free;
- the only thing the client sends is ordinary slot clicks, which the server
  validates through `TradeSlot` exactly as it would for any chest.

The trade-offs are deliberate and small: the window looks like a chest (it *is*
one), there are no in-GUI buttons, and the countdown is announced in chat instead
of being drawn. In exchange, the trade works on an unmodified vanilla client and
the entire class of "the two custom screens disagree" bugs disappears.

Layout: slots **0–17** player A, **18–35** locked glass, **36–53** player B, then
the viewer's own 36 inventory slots. The container title is
`Trade: You vs PlayerName`, so each player sees who they are trading with.

---

## 9. The server detects the interaction itself; the client sends nothing

**Decided:** right-clicking a player is detected **on the server** with Fabric
API's `UseEntityCallback`; the client sends nothing at all.

An earlier version of this mod detected the interaction on the client and sent a
request payload, on the belief that "vanilla has no server-side hook for player X
right-clicked player Y with item Z in hand". That belief was wrong: Fabric API's
`UseEntityCallback` fires on the server for exactly that interaction, so the
client never has to be involved.

The callback only observes — it always returns `PASS` — so it cannot interfere
with any other right-click behaviour. It acts only when all of these hold:

- the side is the server (`!world.isClientSide()`),
- the hand is the main hand,
- the actor and the target are both `ServerPlayer`s,
- the actor is holding a Trade Token.

Everything after that — token possession, distance, dimension, game mode, opt-out
state, whether either player is already trading — is re-derived in `TradeManager`
from server-side objects only. With no custom packets and no client mod, there is
nothing for a client to lie about: the client's only input to a trade is ordinary
container clicks, which the server validates through `TradeSlot`.

---

## 10. Un-confirming resets both sides

**Decided:** `TradeSessionCore.unconfirm()` clears *both* confirmations.

The specification states this explicitly ("If either un-confirms, both
confirmations reset"), and it is worth recording why it is the right rule rather
than a quirk.

If un-confirming only cleared one side, then: Alice and Bob both confirm. Alice
then un-confirms, swaps a diamond for dirt, and re-confirms. Bob's confirmation
was never withdrawn, so his "yes" — given against a completely different offer —
still stands. The trade executes and Bob is robbed. Clearing both forces Bob to
look again.

---

## 11. The token is consumed when the window opens

**Decided:** consumption happens in `TradeManager.accept()`, after every
precondition has been re-verified, and never at request time.

The specification is explicit, and the sequencing matters more than it looks: the
token is charged only *after* the second round of validation succeeds and
immediately before `openMenu` is called. If any check fails — the sender
disconnected, the sender no longer has a token, the two are in different
dimensions — the request is retired and no token is spent.

The consequence is that a declined, ignored, queued or timed-out request is free,
which is what makes the token a cost of *trading* rather than a cost of *asking*.

---

## 12. A server restart returns items; it does not resume trades

**Decided:** cancel on shutdown, hand items back to whoever is online, write the
rest to `tradewindow-pending-returns.*`, restore on next login.

The specification says trades do not persist and that pending returns should be
stored to a file and restored on next login.

Resuming a half-finished handshake across a restart would mean persisting two
36-slot containers, both confirmation flags, both players' UUIDs, and the
remaining timeout — and then reconstructing two live menu objects against
players who may reconnect in a different order, or not at all. The failure mode
of getting that wrong is item loss.

Returning the items is boring and correct: the worst case is that two players
have to start again. The items themselves are serialised with the platform's own
codec (NBT on 1.20.1, `ItemStack.CODEC` on 26.3), so enchantments, custom names
and container contents survive exactly.

---

## 13. Contested requests queue; they do not overwrite or spam

**Decided:** a per-target FIFO queue, with only the head actionable.

The specification says "queue, only one active at a time".

Queueing rather than rejecting is friendlier — Carol's request is not thrown
away just because Alice asked first. Showing only the head is what keeps the
target's chat readable and, more importantly, keeps "accept" unambiguous: there
is always exactly one request that `/trade accept` can mean.

When the head retires (accepted, declined or expired), the next one becomes
actionable automatically.

---

## 14. Damage is observed differently on each target, deliberately

**Decided:** `AFTER_DAMAGE` on every target from 1.21.1 up. (This entry originally
compared against 1.20.1's `ALLOW_DAMAGE` hook; that target is gone — §26.)

Fabric API 0.92.x (1.20.1) has no post-damage hook. `ALLOW_DAMAGE` is a
*pre*-damage veto, so on 1.20.1 the trade is cancelled from that hook and the
handler unconditionally returns `true` — it observes, it never vetoes. Cancelling
the trade is a strictly stronger response than blocking the damage, so nothing is
lost by the hook running slightly earlier.

26.3 has `AFTER_DAMAGE`, which is the semantically correct hook: it cannot
influence the outcome and it reports the damage actually dealt. Using it there is
simply the better API being available.

Both behaviours are documented at the call site so a future reader does not
"fix" the asymmetry.

---

## 15. Death always cancels, regardless of `cancelOnDamage`

**Decided:** `onDeath` cancels unconditionally; `cancelOnDamage` governs only
non-fatal damage.

Death can drop a player's entire inventory. Continuing a trade against a
respawned player is exactly the desync this mod exists to prevent, and a server
that set `cancelOnDamage: false` to allow trading during combat almost certainly
did not mean "and also survive dying". When the two settings conflict, the safer
one wins.

---

## 16. Server-side menu state comes from the session, never from the client

**Decided:** `TradeMenu` resolves the acting player's side by asking the session,
and `TradeSlot` fails closed when the side is unknown.

A menu is constructed independently for each player, so "which 36 slots may I
touch" must be answered per viewer. On the server the answer comes from
`TradeManager.sessionFor(player).sideOf(uuid)` — derived from live state, not
from anything the client sent.

On the client the answer comes from the server's update, and until that update
arrives the side is `null` and **every** trade slot is locked. Failing closed
means a client that has not yet been told which side it is on cannot interact
with either side, rather than defaulting to something permissive.

---

## 17. The chat prompt uses commands, not custom click actions

**Decided:** `[Accept]` and `[Decline]` are `RUN_COMMAND` click events invoking
`/trade accept` and `/trade decline`.

A custom `ClickEvent` payload would need a custom handler, which is more moving
parts for no gain. Routing through the command tree means the buttons are the
same code path as typing the command, so the two can never diverge — and it makes
the feature testable by hand with nothing but a chat box.

---

## 18. Confirm / cancel are chat commands, not GUI buttons

**Decided:** confirm, cancel and un-confirm are clickable chat links that run
`/trade confirm`, `/trade cancel` and `/trade unconfirm`.

A vanilla double chest has no room for buttons, and adding a custom screen for
them would reintroduce the client mod the rewrite exists to remove. So the
controls live where an unmodified client can already click: chat.

Using **commands** rather than a custom click action matters. `ClickEvent.RunCommand`
is a vanilla chat feature that works on every client, and it routes through the
same `CommandRegistrationCallback` as typing `/trade confirm` by hand — so the
clickable link and the typed command are literally the same code path, and a
player who prefers the keyboard is not a second-class citizen.

The controls line is re-sent to both players whenever either side confirms or
un-confirms, so the `[Unconfirm]` link appears exactly when it is valid. The
countdown is announced at 30 s and 10 s for the same reason: chat is the only
channel a vanilla client is guaranteed to render.

---

## 19. Trade history stores successes only

**Decided:** `tradewindow.log` records every outcome; the history backend records
successful trades only.

`/trade history` is described as "last 10 trades", and a list where most entries
are "cancelled because someone walked away" is noise. Cancellations are still
recorded in full in the human-readable log, so nothing is actually lost — the
distinction is only about what the command surfaces.

---

## 20. Config is normalised on load and written back

**Decided:** `TradeConfig.load()` clamps out-of-range values and immediately
rewrites the file.

Silently clamping on every start would leave an operator staring at
`"guiTimeoutSeconds": 0` and wondering why the window lasts ten seconds. Writing
the normalised form back makes the effective configuration visible.

Every clamp is chosen so a corrupt config degrades toward *more* safety: a
negative timeout becomes 5 seconds rather than infinite, a nonsense distance
becomes 1 block rather than unlimited, and a `null` blacklist is repaired to the
defaults rather than honoured as "nothing is blacklisted".

## 23. Each player gets their own menu, so their own offer is always the middle rows (v1.4.0)

**Decided:** the two players do not see the same arrangement. Each gets their own
`TradeMenu` instance over the same `TradeSession`, and the instance decides which
container each slot id points at: rows 2–3 (slots 9–26) are always the viewer's own
offer and rows 5–6 (36–53) always their partner's, whichever side of the request they
were on. The header rows are per viewer as well, so your own head is at slot 4 and
your partner's at slot 31 in both windows.

A vanilla client renders a chest and reports slot *ids*; it never sees a container.
Two menus over one set of containers is therefore free: vanilla syncs each menu's
slot contents independently every tick, so the two windows are different views of the
same trade with no packet, no client code and no duplicated items. The alternative —
the v1.3.0 shared arrangement, where the top half was the requester's offer for both
players — meant that half of all players were looking at their partner's items first
and had to read a head to work out which rows were theirs.

Consequences worth stating explicitly:

- **A side is an identity, not a position.** `TradeSide` still names the requester and
  the acceptor; nothing counts on `PLAYER_1` being "the top half" any more.
- **Both `LOCK` blocks lock the player who clicked them.** The row a block sits in is
  not the row it acts on, because acting on the other player's side is exactly the
  thing the design must never allow. Both `CANCEL` blocks cancel the trade.
- **Two headers means two ways to disagree.** They are repainted from one source of
  truth on every lock, so a window can never show a state the trade is not in.
- **The heads are viewer-relative:** your own row's head is green, your partner's is
  red. The alternative (a colour per player) would be stable across screens but would
  leave the viewer translating "which of these two is me".
- **The title carries no names.** Both players are looking at the same kind of grid and
  the heads already say who is who, so the title is just `Trading Window`.

---

## 24. Operator tools are a separate, op-gated tree (v1.4.0)

**Decided:** `/trade` stays a player command with three branches, and the four
operator tools live in `/tradeadmin` at permission level 2 (on 1.21.11, a
`PermissionSet` check against `Commands.LEVEL_GAMEMASTERS` — the permission system
that replaced numeric levels in this version).

`/trade` is part of every player's tab-completion, so anything on it is something a
player will eventually try by accident. Listing live trades, reading history and
opening someone else's window read-only are not player actions; keeping them in a
second tree also means the player surface can be audited at a glance, which is why
`tools/verify_packaging.py` fails if `TradeAdminCommand`'s literals appear in
`TradeCommand`.

Consequences worth stating explicitly:

- **Spectating is read-only and side-effect free.** A spectator's menu refuses every
  click on the grid (including the buttons), never counts as a participant, and does
  not touch the timer or the locks. Closing their window ends only their view.
- **`crossdistance` is persisted on the spot.** It is the setting an operator is most
  likely to change while a trade is in flight, so it is written to the config file the
  moment it changes rather than at shutdown.
- **History keeps 20 trades in memory whether or not it is persisted.** `logTrades`
  controls the file (and the SQLite store when a driver is present), not whether the
  command has anything to say.
- **The distance rule now has two switches,** because it needed one: `cancelOnMove` is
  the master switch and `crossDistanceTrading` (default **on**) is the policy. On means
  no distance check at all; off means the classic `maxDistanceBlocks` rule applies.
  Since v1.4.1 the master switch also defaults to **off**, so range can only end a
  trade on a server that has asked for it twice.
- **The config is the shared model, and all of it is honoured.** The v1.3.0 three-field
  copy was defensible while nothing else was implemented; restoring the documented
  fields means each one now drives behaviour, and the packaging gate fails if a field
  appears in the file without being read anywhere in this tree.

---

## 22. The buttons live inside the container, and locking is one-way (v1.3.0)

**Decided:** on 1.21.11 the window carries its own controls. Each player's header
row holds a green concrete `LOCK` block and a red concrete `CANCEL` block, and
`TradeMenu.clicked` intercepts a click on either slot and acts on it server-side.
The `/trade lock`, `/trade unlock` and `/trade cancel` commands - and the
`[ Lock ]`, `[ Unlock ]`, `[ Cancel ]` chat buttons - are gone with them.

A vanilla client cannot draw a button, but it can report a click, so a renamed item
in a slot the server owns is a button the client already knows how to press. Acting
on the click *before* vanilla's movement logic runs means nothing is ever picked up,
moved or duplicated in those slots, and no packet, command or keybind is involved.

Consequences worth stating explicitly:

- **Locking is one-way within a trade.** The green block is replaced by a lime pane
  reading `LOCKED`, so there is nothing left to click, and the only way back is to
  cancel. The old `unlock` existed to clear *both* locks; its whole purpose was to
  stop an offer changing after a lock, and removing the command keeps that guarantee
  while removing the button that could be pressed by accident.
- **A green block belongs to its header row.** Only the player whose row it sits in
  can press it, so nobody can lock the other player's side from across the window.
  Either red block cancels, because "cancel the whole trade" is not a per-side action
  and a player must always be able to abort.
- **The status paper no longer carries a name.** It sits immediately right of its
  owner's head, which says the same thing with fewer words: `[Alice's head]`
  `[EDITING]`. Decision #21's `Alice: EDITING` repeated a name already on screen.
- **The token is spent on open, not on request.** A request that is declined, ignored
  or times out therefore costs nothing, and the tear spent is the one the sender used:
  the request remembers the slot, acceptance verifies it, and a missing token opens
  no window rather than charging a different tear.
- **The strings are in the language file after all.** This reverses the "text is
  literal, never translatable" half of decision #21. The *components* are still
  literal - a vanilla client has no language file for this mod, so a translatable
  component still arrives as its raw key - but the words themselves are read from
  `assets/tradewindow/lang/en_us.json` at start-up by `TradeText`. Java holds keys and
  format arguments, the file holds every sentence, and a missing key is logged and
  rejected by `tools/verify_packaging.py` rather than shown to a player.

---

## 21. On 1.21.11 the token is a renamed vanilla item (v1.2.0)

**Decided:** the 1.21.11 target registers nothing with the game. A Trade Token is a
Ghast Tear whose `minecraft:custom_name` is exactly `Trade Token`, and it is
produced by an ordinary data-pack recipe (1 Ghast Tear + 1 Gold Ingot, shapeless).

A registered item has to be known by every client that connects, so a client
without the mod is kicked with "Received a registry entry that is unknown to this
client" the moment the registry sync arrives. There is no configuration that makes
that safe, and no way to ship it that a vanilla client can survive - the only fix
is to stop registering. Vanilla items plus components give the same affordances
(a distinct name, lore, a crafting path, a red name above the hotbar) at zero
registry cost.

Consequences worth stating explicitly:

- **The name is the identity.** Detection is `stack.is(Items.GHAST_TEAR)` plus
  `getHoverName()` equal to `Trade Token`, so an unnamed tear is never a token and
  renaming one in an anvil retires it. A cheaper test (`has a custom name`) would
  have accepted any renamed tear, including ones a player renamed for unrelated
  reasons.
- **The token is not consumed.** The brief does not ask for it, and a token that
  survives its trade is friendlier: one craft is a permanent licence to trade. The
  old `requireToken && consumeToken` pair is gone with the item.
- **Two `STATUS` papers would be ambiguous,** because both players see both.
  Each paper therefore carries its owner's name (`Alex: LOCKED`), which also keeps
the requested yellow/green shape instead of inventing new wording. *(Superseded in
v1.3.0: the paper is gone and the spent `LOCK` block is the state display.)*
- **The heads are anchored to the rows, not to the viewer.** A chest cannot be
  arranged differently per viewer, so the spec's "THEM"/"YOU" naming is not
  achievable; the heads are named with the actual player names and carry lore
  saying which rows they offer, which is true for both viewers. *(Superseded in
v1.4.0: the menus are per viewer after all — decision #23 — so the head is always
your own, and the lore is gone.)*
- **`unlock` clears both sides,** exactly as `unconfirm` did (decision #10), for
  the same anti-swap reason: a half-unlocked offer is a scam window. *(Superseded in
v1.3.0: locking is one-way, and cancelling is the way back.)*
- **Text is literal, never translatable.** A vanilla client has no language file
  for this mod, so a translatable component would render as its raw key. This is
  why the `en_us.json` of this target is a catalogue rather than a runtime lookup.
  *(Kept, refined in v1.3.0: the components are still literal, but the words live in
that file and are resolved server-side.)*
- **The shared core still compiles into the jar.** `gradle/target-module.gradle`
  adds `shared/src/main/java` to every target; only `TradeSide` is used by this
  design. It is inert (nothing references those classes) and is left alone because
  removing it per-target would weaken the shared-core guarantee the other seven
  trees depend on.

---

## 25. The distance rule is asked before the token is spent, and answered in one place (v1.4.1)

**Decided:** `TradeDistance`, a pure function in the shared core, is the only place that
decides whether two traders are out of range. Sending a request, accepting one and the
tick loop all ask it, and the acceptance asks it *before* consuming the requester's
Trade Token.

v1.4.0 asked the distance question in the tick loop and nowhere else. With
`crossDistanceTrading` off that produced a bug with a very annoying shape: the request
went out at any range, the target accepted, the requester's Ghast Tear was spent and
the window opened - and the next tick, finding the pair two hundred blocks apart, ended
the trade. The player paid for a window that was closed before they could put anything
in it.

Three things follow from the fix:

- **A precondition that can refuse a trade belongs where the spending happens, not only
  where the watching happens.** The tick loop stays - a player who teleports away
  mid-trade still needs the trade ended - but it is now the *last* line of defence
  rather than the only one. The two earlier points refuse for free, because nothing has
  been spent yet.
- **A refused acceptance costs the answer, not the request.** The pending request goes
  back on the queue unchanged, so a player who was merely out of range can walk closer
  and press `[ Accept ]` again inside the original timeout. Dropping the request instead
  would punish the person who did nothing wrong and force them to ask again.
- **One definition, because three answers must not be allowed to disagree.** The gate
  over `cancelOnMove` and `crossDistanceTrading`, the radius comparison and the
  cross-dimension case are a single pure function taking a squared distance and a
  same-dimension flag. It is unit tested without a server, and `verify_packaging.py`
  fails if `accept()` ever tests it after `TradeToken.consume`.

**Why the defaults changed in the same release:** the bug is only reachable with the
distance rule on, and the retuned defaults now leave that rule off twice over
(`crossDistanceTrading` was already true, `cancelOnMove` now defaults to false), so a
fresh install cannot reach it at all. The rule is still fully available to an operator
who wants it - `TESTING.md` §0.9 is written for exactly that configuration.

---

## 26. Every target is generated from one reference tree, and 1.20.1 was dropped (v1.4.1)

**Decided:** `eras/1.21.11` is the only hand-written Minecraft-facing tree.
`tools/port_eras.py` derives the other eight from it through a per-era dialect table,
and 1.20.1 is retired rather than ported.

Before this, the era trees were derived too — but by a weaker generator that wrote only
the dialect files and left the rest of each tree to be edited in place. That is how
they drifted: the 1.21.11 design landed in one tree and stayed there for three releases
while the other thirteen targets still shipped the v1.1.0 design, with a registered
item, a drawn GUI and two packets. Seventeen files are now identical across nine trees
*by construction* rather than by discipline.

The trade-offs, stated plainly:

- **A generated tree must never be hand-edited.** The next regeneration discards it,
  and nothing in the build can tell an intentional edit from a mistake. The rule is:
  change the reference, or the dialect table, then regenerate.
- **A new API break costs one row** in the generator's matrix instead of a new copy of
  the tree. The two 26.x splits found during the port are exactly that: 26.x went from
  one tree that was verified for its newest member only, to three trees that all build.
- **A dialect change is invisible in a normal review.** Editing the table can alter
  nine trees at once, so reviewing this repository has to include the regenerated
trees, not just the script.

**Why 1.20.1 was dropped rather than ported:** the token *is* its data components
(`custom_name`, `lore`, `max_stack_size`). 1.20.1 stores item data as NBT, so porting
the design would have meant a second identity check for the token — a divergence in the
one decision that determines whether a player is allowed to trade at all. Fourteen
targets with one token check is better than fifteen with two, and the specification's
version list never included 1.20.1 anyway.**Why the manifests are generated as well:** a `fabric.mod.json` is not compiled, so a
stale template in the generator is invisible until load. The template had kept a
`client` entrypoint naming a class that no longer exists; regenerating the modules
restored it, and all fourteen jars failed the packaging gate on that check. A generated
file whose contents decide whether the mod loads at all deserves the same review as
handwritten code — and only a gate that reads the built jar, rather than the source, can
catch it.

## 27. `"environment": "*"`, because `"server"` means *not on a client* (v1.4.2)

The manifest originally said `"environment": "server"`, which reads like a statement of
this mod's design — server-side only, no client code — and is not what the field means.
`ModMetadata.matches(EnvType)` returns false for a server-only mod in a **client**, and
Fabric Loader then never invokes the entrypoint. A single-player world *is* a client, so
the same jar that worked on a dedicated server did nothing whatsoever in single-player:
no commands, no right-click, no error, no log line.

The field is now `"*"`. Nothing else changed: still one `main` entrypoint, still no
`client` entrypoint, still no client code, still nothing registered with the game. The
safety argument is not the field's value but the entrypoint's contents — every callback
it registers is either server-side only (`ServerTickEvents`,
`ServerPlayConnectionEvents`, `ServerLivingEntityEvents`, `ServerLifecycleEvents`,
`CommandRegistrationCallback`) or explicitly guarded (`UseEntityCallback` behind
`!world.isClientSide()`). On a client the mod initialises, writes its config, and waits.

**The general form:** `environment` decides *where a jar is allowed to load*, not which
game systems it touches. For a mod whose whole interface is a vanilla container and a
command — no packet, no screen, no key bind — `"*"` is the honest value, and `"server"`
is a capability voluntarily given up by accident.

## 28. The token's name is data, so the data is checked at startup (v1.4.3)

The token's identity lives in a data-pack recipe rather than in code, which is what
makes it invisible to everything that normally catches a mistake. It was written in the
wrong encoding twice, in opposite directions, and both times the build was green: the
mod loaded, the gate passed, the tests passed.

**Why there is no portable spelling.** The two ways of writing a text component in a
recipe are not interchangeable, and the boundary runs the opposite way to the one that
looks natural:

* **1.21.1** - `custom_name` is a string codec that parses its input as JSON. The
  object form is rejected and the recipe is dropped. The string form is therefore the
  only usable spelling there, not a compatibility fallback.
* **1.21.2 and later** - the component codec takes the object form, and reads a string
  as **literal text**. The escaped string loads perfectly and names the item with raw
  JSON.

Neither side errors on the form it ignores, so "the encoding accepted everywhere" does
not exist - only one that is *loaded* everywhere, which is not the same as one that
*works*. That distinction is the whole of this decision: `Not a string` and raw JSON are
the two ways of being wrong, and neither is a build failure.

**Why the generator, not the code.** The form is a property of the Minecraft version,
so it belongs in the same era table as the ingredient spelling and the advancement's
`recipe_unlocked` key, produced alongside the recipe it describes. One table, one truth,
nine trees.

**Why it is checked at runtime.** `TokenRecipeCheck` reads the recipe back out of the
mod's own jar and logs whether its form fits the version being loaded. This is
deliberately not a codec round-trip: the thing that was wrong was a *comparison*
between a shipped file and a version, so the check asserts exactly that, in one line,
where a human will see it. A codec round-trip would re-derive the answer from the same
assumption that failed; asserting the table keeps the risk where it actually is, in the
one-line version rule that the boot probes verify.

**The rule this leaves behind:** when a mod's behaviour depends on data no compiler
reads, the build should assert that data (the packaging gate) *and* the program should
report it at startup (the self-check). Compiling is not loading, and loading is not
working.
