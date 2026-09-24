# Testing Trade Window

Two layers: an automated JUnit suite over the shared core, and a manual
checklist for the parts that need two real clients.

Run the automated suite with:

```bash
./gradlew :shared:test
```

The HTML report lands in `shared/build/reports/tests/test/index.html`.

---

## 0. The vanilla-client checklist (v1.4.3) — every target

Everything in this section applies to **all fourteen jars**, because they all
implement the same design: a renamed Ghast Tear instead of a custom item, a
**mirrored** window in which your own offer is always the middle rows, LOCK and
CANCEL clicked inside the window, no status paper, the distance rule refused before a
token is spent, and `/tradeadmin` for operators. The earlier split — one design on
1.21.11 and the 1.1.0 design everywhere else — is gone; every target now compiles the
same generated sources (`PORTING_NOTES.md` §13). What still differs per target is the
build: different jar, mappings, Loom pipeline, Java level and loader.

Build and install the jar for the target under test:

```bash
./gradlew buildAll             # all fourteen jars
./gradlew :v1_21_11:build      # one target
# -> versions/1.21.11/build/libs/tradewindow-1.4.3-1.21.11.jar
```

Put that jar and **Fabric API** in the server's `mods/` folder. No client mod, at
all, on any client used below.

### 0.0 Boot-test status, and the three lines to grep

Every one of the fourteen jars has already been started as a real server (v1.4.3) and
its log read. All fourteen start clean:

| Checked in the log | Expected |
|---|---|
| recipe parse | **no** `Parsing error loading recipe tradewindow:trade_token` |
| registries | **no** `Registry loading errors` |
| datapacks | **no** `Failed to load datapacks, can't proceed with server load` |
| mod | `Trade Window loaded for Minecraft <version>` |
| token self-check | `Trade Token recipe check: <form> text components, correct for Minecraft <version>` |
| recipe count | one higher than the same jar without the mod (1.21.1: 1291) |

Three faults were found this way. Each is fixed, and each is worth re-checking when a
new target is added, because all three are invisible to the compiler:

1. **The token's text components must use the object form on every target except
   1.21.1, which needs the escaped-string form.** The rule flips at 1.21.2 and each
   side mishandles the other's spelling *silently*: on 1.21.1 an object makes the
   recipe vanish, and on 1.21.2+ a string makes the token's name the raw JSON. The
   startup self-check reports the form and whether it fits the running version, so a
   wrong jar says so in the log instead of looking fine.
2. **The advancement's `recipe_unlocked` condition uses `recipe` on every target
   except 26.3, which uses `recipes`.** The wrong key makes 26.3 refuse to finish
   loading.
3. **The manifest is `"environment": "*"`, not `"server"`.** Fabric Loader *skips* a
   `"server"` mod on a client, so the same jar does nothing in single-player — no
   commands, no right-click, no error. Test single-player as well as a dedicated server:
   the jar must work in both.

**One end-to-end trade has now been run by hand (2026-09-24, 1.21.11).** Two
unmodified clients on a live server: the token was crafted in a crafting table, the
request was accepted from the chat buttons, the window opened with both offers in it,
one side was locked, the other side was locked, the swap was delivered and the window
closed - and every chat line in 0.3 to 0.6 above matched what appeared. The captures are
the five pictures in `docs/images/` (see the README). This is real evidence for **1.21.11
only**, and it did not touch cancel, timeout, disconnect, death, a full inventory or
`/tradeadmin`; those stay on the checklist below.

### 0.1 Server boots

- [ ] Start the server. No `Item id not set` crash, no `ExceptionInInitializerError`,
      and `Trade Window loaded for Minecraft 1.21.11` in the log.
- [ ] Start a **single-player world** with the same jar in the client's `mods/` folder.
      `/trade` must exist and right-clicking a villager with a Trade Token must do
      nothing (no crash): the mod is loaded, not skipped.
- [ ] The log also reports `Loaded N message(s) from /assets/tradewindow/lang/en_us.json`.
      If it instead reports that the language file is missing, every message below
      appears as a key (`tradewindow.chat.complete`) and the window is titled
      `container.tradewindow.title` - that is the one packaging failure the jar
      cannot work around at runtime.
- [ ] The log reports a `Trade history backend:` line naming
      `tradewindow-history.jsonl`.
- [ ] `config/tradewindow.json` exists with `tradeTimeoutSeconds: 15`,
      `guiTimeoutSeconds: 120`, `maxDistanceBlocks: 20`, `crossDistanceTrading: true`,
      `cancelOnDamage: true`, `cancelOnMove: false`, `requireToken: true`,
      `logTrades: true`, `useDatabase: false`, `allowCreativeTrading: false`,
      `allowCrossDimensionTrading: false`, a two-entry `blacklistedItems` and
      `maxTradeValue: -1`.
- [ ] With those defaults, a trade can sit open for two minutes and neither player
      needs to stay near the other: no timeout before ~120 s and no distance
      cancellation at all.

### 0.2 The token is a vanilla item that stacks to 16

- [ ] `/give @s minecraft:ghast_tear` gives an ordinary **Ghast Tear**, stacking to
      64; a plain tear is **not** a token.
- [ ] Craft 1 Ghast Tear + 1 Gold Ingot (shapeless) → one Ghast Tear whose name
      above the hotbar is **Trade Token** in **red**, with gray italic lore
      "Right-click a player to trade".
- [ ] **Craft the recipe twice into the same slot: the two tokens stack**, and the
      stack counts up to 16 rather than 64.
- [ ] Pick up a Ghast Tear and confirm the recipe appears in the recipe book
      (that is the advancement in `data/tradewindow/advancement/recipes/misc/`).
- [ ] **The name is styled, not raw JSON.** The item above the hotbar reads
      `Trade Token` in red — if it reads
      `{"text":"Trade Token","color":"red","italic":false}` instead, this jar was
      built with the flat string form for a version that needs the object form. The
      startup log says so: look for the `Trade Token recipe check` line, which reports
      the form and whether it fits this Minecraft version.
- [ ] Hovering the token shows `Right-click a player to trade` in gray italic, not
      raw JSON.
- [ ] With only plain tears, `/trade <player>` is refused with
      `You need a Trade Token to trade.`
- [ ] Renaming a token in an anvil (so the name is no longer exactly
      `Trade Token`) stops it working.

### 0.3 Two vanilla clients

- [ ] Connect with two **unmodified** clients. Neither is kicked, and the server log
      reports no unknown registry entry and no unknown payload.
- [ ] `/trade ` followed by tab lists exactly `accept`, `decline`, then player names
      — no `lock`, `unlock`, `cancel`, `confirm`, `unconfirm`, `toggle` or `history`.
- [ ] `/tradeadmin ` from an op tab-completes `crossdistance`, `list`, `spectate`
      and `history`; from a non-op the command is refused.

### 0.4 Request, and the token is spent once

- [ ] Alice holds the red token and **right-clicks** Bob. Bob receives exactly one
      message: `[Trade] Alice wants to trade. [ Accept ] [ Decline ]` — one space
      inside each bracket and one before each bracket: no `[Accept]`, no
      `Alice  wants`, no `Alicewants`.
- [ ] Alice receives **no** message at this point (by design: the window is the
      answer), and her token is still in her inventory.
- [ ] Bob clicks `[ Decline ]`: Alice gets exactly one line
      `[Trade] Bob declined your trade request.` and nothing opens. Re-send the
      request for the rest of this section.
- [ ] Bob clicks `[ Accept ]`: both get `[Trade] Trade started.` — with no player
      name in it — and a double chest titled exactly **`Trading Window`** opens for
      both, centred, with nothing else in the title bar.
- [ ] **The token is spent exactly once, here.** Alice's stack went down by one (or,
      if she had only one, the slot is empty) and Bob's inventory is untouched.
- [ ] Before accepting, move the token out of the slot it was in (or drop it) and
      then accept: no window opens, Alice is told she no longer has a token, and Bob
      is told Alice no longer has one.

### 0.5 The layout, from each player's own point of view

- [ ] Row 1: black panes at 0, 1 and 2, the green `LOCK` block at 3, a player head at
      4, the red `CANCEL` block at 5, panes at 6, 7 and 8.
- [ ] Row 4: the same shape at 27–35, with `LOCK` at 30, the head at 31 and `CANCEL`
      at 32.
- [ ] **Alice sees Alice's head at slot 4 and Bob's at slot 31; Bob sees Bob's head at
      slot 4 and Alice's at slot 31.** Both heads show the real skin and are named
      with the player's real name.
- [ ] Hover a head: the tooltip is the player's **name and nothing else** — no lore
      line, no "offers the two rows below".
- [ ] **There is no status paper anywhere.** Slot 1 and slot 28 hold panes, not paper.
- [ ] Both players can put items in slots 9–26 and nowhere else. Check with clicking,
      shift-clicking, dragging and number-key swaps against slots 36–53 and both
      header rows.
- [ ] **Your items are always in rows 2–3 and your partner's always in rows 5–6.**
      Ask Alice to put a named item in her offer and Bob to put a different one in
      his: Alice must see hers at 9–26 and Bob's at 36–53, and Bob must see the
      mirror image.
- [ ] No header item can be taken, dropped onto, double-clicked or shift-clicked out,
      by either player.

### 0.6 Locking with the blocks

- [ ] Alice clicks the **green concrete at slot 3 or 30**: she gets exactly one line
      `[Trade] You locked your side.`, **both** of her `LOCK` blocks (slots 3 and 30)
      become green panes reading `LOCKED` **for both players**, and her slots 9–26
      stop accepting changes.
- [ ] Alice clicks a `LOCK` block again: nothing happens — no second chat line, no
      change.
- [ ] **Neither player can lock the other's side.** Bob's click on a `LOCK` block
      always locks Bob's own offer, so a window that shows Bob's offer as locked can
      only be that way because Bob locked it.
- [ ] Bob clicks his green concrete: both windows close, both get
      `[Trade] Trade complete.`, and each player now holds what the other had offered.
- [ ] Count the chat: request (1, to Bob), start (1 each), own lock (1 each),
      complete (1 each). No line appears twice and none is missing.
- [ ] `/tradeadmin history` lists the trade that just completed, with both names, the
      timestamp and an abbreviated item list.

### 0.7 Cancel, and the terminal paths

- [ ] Click the **red concrete at slot 5 or 32**: both get exactly one
      `[Trade] Trade cancelled.` and every item is back with its owner.
- [ ] Placing and removing items produces **no** chat at all.
- [ ] Your partner locking produces no chat for you — only their blocks change.
- [ ] Leave the window open: nothing is said on any countdown, and at
      `guiTimeoutSeconds` (120 s by default) both players get `[Trade] Trade timed out.`
      with every item returned.
- [ ] With `crossDistanceTrading` on (the default), walk more than 20 blocks apart:
      **nothing happens** and the trade stays open.
- [ ] Set `cancelOnMove: true` by hand in the config (the master switch ships off) and
      restart. `/tradeadmin crossdistance off`, then start another trade and walk more
      than `maxDistanceBlocks` apart: both players get
      `[Trade] Trade cancelled — you moved too far away.` and their items back. Restart
      the server and confirm `/tradeadmin crossdistance on` is still needed — the
      setting was saved to the config file.
- [ ] An unanswered request expires quietly: send one and wait `tradeTimeoutSeconds`
      (15 s) — no warning, no message, and the `[ Accept ]` link stops working.
- [ ] Put an item that is not blacklisted in the offer with `cancelOnDamage` on, then
      hurt one player: the trade ends with `Trade cancelled.` and items return. Set
      `cancelOnDamage: false`, restart, repeat: the trade survives the hit.
- [ ] Try to place a blacklisted item (`minecraft:bedrock`): it is refused silently,
      with no chat line.
- [ ] Disconnect one player mid-trade: the other sees
      `[Trade] Other player disconnected. Trade cancelled.` and keeps their items;
      the one who left gets theirs back on rejoin if they could not be handed over.
- [ ] Kill one player mid-trade: both get `[Trade] Trade cancelled.`, and the trade
      items must not be in the death drop.
- [ ] Close the window with Esc: the trade cancels, `Trade cancelled.` is printed
      for both, and the items return.
- [ ] **Open chat mid-trade (press T, then Esc): the trade does NOT cancel and the
      window is still open and usable afterwards.** Same for a message from another
      player, and for `/say` from the console.
- [ ] Fill one player's inventory completely, then complete a trade: nothing is
      deleted — the overflow lands at the receiver's feet.

### 0.8 Operator tools

- [ ] `/tradeadmin list` with two trades open prints one line per trade: both names,
      elapsed seconds, and each side's `EDITING` / `LOCKED` state. With none open it
      prints `No trades are active.`
- [ ] `/tradeadmin spectate <player>` on a trading player opens the same window for
      the admin, showing player one's offer at 9–26 and player two's at 36–53.
- [ ] **The spectator cannot move anything**: try clicking, shift-clicking and
      dragging items in both offers and in the header rows, and try pressing the
      concrete blocks. Nothing moves, nothing locks, nothing cancels.
- [ ] Locking and completing the trade while an admin watches changes nothing about
      how it completes, and the admin's window closes when the trade ends.
- [ ] `/tradeadmin spectate <player>` for a player who is not trading prints
      `[Trade] <player> is not currently trading.`
- [ ] `/tradeadmin history <name>` filters to that player; an unknown name prints
      `No trades recorded yet.`

### 0.9 The distance rule refuses without spending a token

Everything here needs the rule switched on: set `crossDistanceTrading: false` **and**
`cancelOnMove: true` in `config/tradewindow.json`, then restart. With the shipped
defaults the same steps must instead all succeed, at any range.

- [ ] Alice stands 200 blocks from Bob and runs `/trade Bob`: the request is refused
      with `[Trade] You are too far away to trade — move within 20 blocks of the other
      player.` Bob receives nothing at all, and Alice still has her token.
- [ ] Walk into range, send the request, then walk out of range **before** Bob clicks
      `[ Accept ]`. Bob's click is refused: he gets the message above, Alice gets `Bob
      is too far away to trade — move within 20 blocks of them.`, **no window opens**,
      and — the point of this test — **Alice's Trade Token is still in her inventory**.
- [ ] Walk back into range within the request's fifteen seconds and press
      `[ Accept ]` again: the window opens normally and exactly **one** token is
      consumed.
- [ ] Let the re-armed request expire instead: one token is still all Alice ever had.
- [ ] Start a trade in range, then walk past 20 blocks with the window open: both get
      `[Trade] Trade cancelled — you moved too far away.` and every item comes back.
      No token is refunded for that trade — it opened and was paid for.

### 0.10 Restart safety and item data

- [ ] Leave a window open, `/stop` the server, restart and rejoin: the items that
      were in the window are returned on login with one message
      `[Trade] Returned N stack(s) held from an interrupted trade.`
- [ ] `/tradeadmin history` still lists the trades from before the restart (they were
      reloaded from `tradewindow-history.jsonl`).
- [ ] Offer a renamed, enchanted, damaged item, complete the trade, and check every
      component survived (name, enchantments, durability, container contents).
- [ ] Spam shift-clicks, drags and number-key swaps across both offers for a few
      seconds, then lock and complete: the totals must be exactly what was placed.
- [ ] Try to take a head, a pane or a concrete block by any means (click,
      shift-click, drag, double-click, number-key swap): every attempt is rejected
      server-side.

### Notes on what the automated suite does not reach

- There is **no** automated test target for the era code itself: `shared/src/test`
  covers the Minecraft-free core that all fourteen jars compile, config model included,
  and the era code is deliberately thin (one manager, one session, one menu, one layout
  class, one string loader). It is checked by the list above plus
  `tools/verify_packaging.py`, which fails when a language key used in Java is missing
  from `en_us.json`, when a status paper or a translatable component reappears, or when
  the two windows stop mirroring each other.
- No jar registers an item, so a script, shop or datapack that expects
  `tradewindow:trade_token` must use a crafted Ghast Tear named "Trade Token" instead.
  Since v1.4.1 that applies to **every** target, not only 1.21.11.
- `max_stack_size` was verified against the 1.21.11 game code rather than assumed:
  the component is registered as persistent with the range 1–99 and synchronised over
  the network, and `ItemStack.getMaxStackSize()` reads it, so 16 is honoured. See
  `PORTING_NOTES.md` §11.

---

## 1. Automated tests

`shared/src/test/java/com/tradewindow/TradeLogicTest.java` — **73 tests**, all
green. They exercise `com.tradewindow.core`, which has no Minecraft on its
classpath, so they run in about a second and cover the logic that is otherwise
painful to reach: timeouts, disconnects and concurrency.

Because every target compiles these same core sources into its jar, a
green run here means the algorithm is correct on **every** target.

### Item swap logic (10 tests)

| Test | Asserts |
|---|---|
| both offers empty yields a valid, empty plan | Empty trade is legal and delivers nothing. |
| one-sided trade still validates and delivers | A gift works; the receiving side gets the items. |
| partial stacks keep their exact counts | 7 iron stays 7 iron, not 7 or 8. |
| full stacks keep their exact counts | A 64-stack survives as a 64-stack. |
| non-stackable items with a max size of 1 survive | Tools and armour move one at a time. |
| item data (NBT / data components) is preserved exactly | The data fingerprint of an enchanted sword is unchanged after the swap. |
| deliveries are independent copies, not live references | Mutating the source after planning cannot alter the delivery. |
| empty slots are stripped before delivery | Empty slots do not consume delivery space. |
| a copy that lost its item data is detected by `matches()` | The post-commit check catches a stripped enchantment. |
| `matches()` detects a count mismatch | 10 diamonds versus 9 is caught. |

### Blacklist and value cap (9 tests)

Blacklisted items reject the trade on either side; matching is case-insensitive;
an over-stacked entry (999 diamonds in one slot — impossible from a vanilla
client) is refused rather than silently clamped; the value cap is off by
default, rejects an over-cap trade, allows an under-cap one, and totals one offer
correctly; unlisted items contribute the documented unknown value.

### Confirm state machine (9 tests)

Confirm/unconfirm is the shared core's own vocabulary — the model records
*confirmations*, and the window renders a confirmed side as `LOCKED`. Only the
locking half is reachable from a game now: unlocking a side is not offered, so the
un-confirm branch remains in the model and tested, but no shipped jar can call it.

A single confirmation is not enough; both confirmations request an execute;
un-confirming resets **both** sides; double-confirm and un-confirm-with-nothing
are no-ops; a late confirm after cancellation is ignored; an executed session
cannot then be cancelled; `sideOf` resolves participants and rejects outsiders.

### Slot layout and ownership (7 tests)

The trade window's slot layout and the rule that decides who may touch a slot —
the mod's main anti-scam boundary — live in `com.tradewindow.core.TradeLayout` so
they can be unit tested rather than only exercised by hand. The suite asserts the
shape (two 18-slot offers around an 18-slot divider: 54 trade slots, then the
viewer's own inventory), that player A owns 0–17 and player B owns 36–53, that the
divider belongs to neither side, that every offer slot has exactly one owner, that
glass and out-of-range indices are never usable, and that **a null side owns
nothing** — the old inline rule returned `true` for every index at or past
`SECOND_OFFER_START` when the side was null, because the comparison against
`PLAYER_1` degenerated.

### Timeout logic (6 tests)

The GUI timeout fires exactly at the configured second and not before; a finished
session never times out; execution beats the timeout when both are due; a request
expires after its timeout and not before; a request can only be resolved once; the
registry drops expired requests and frees the sender.

### Concurrency and disconnect handling (12 tests)

Self-trade is blocked; a player already trading cannot start another; a target
cannot be double-booked; a sender cannot have two outstanding requests; two
players requesting the same third player queue FIFO with only one actionable
prompt; declining promotes the next in line; disconnecting drops pending requests
**in both directions** and clears the session flag on both sides; opting out
blocks requests; `clear()` resets everything.

### Configuration (7 tests)

Defaults match the documented file; JSON round-trips; absent keys keep their
defaults; hostile values are clamped toward safety; the blacklist is
de-duplicated and normalised; an empty object yields pure defaults.

### Logging and history (9 tests)

The log line names both players and both offers and records the cancellation
reason; an empty offer renders as `nothing`; records survive a JSON round-trip;
the file store returns newest first; a corrupt line is skipped instead of
throwing; an absent file returns nothing; snapshots normalise their fields; a
zero or negative limit returns nothing.

### A bug this suite caught

`TradeRegistry.forget()` originally removed a departing player's *incoming*
requests from the target queue but left the matching entry in
`requestBySender`. The effect: if Alice disconnected while Carol had a request
out to her, Carol would be permanently unable to send another request, because
the registry still believed she had one outstanding.

`disconnecting drops pending requests in both directions` failed on exactly that
assertion. The fix releases each dropped request's sender as well. This is the
kind of bug that is invisible in play until one specific player is quietly
locked out for the rest of the session.

---

## 2. Manual checklist

**A server with the mod, and two vanilla clients with no mods at all.** This is
the point of the vanilla-container design: the trade window is a plain double
chest and every action is a `/trade` command, so an unmodified client can trade.

Testing is **per target, not per era**. Two versions that share a source tree still
ship different jars, built against different mappings by different loaders — and the
one failure this project has actually shipped was a manifest that named a client
entrypoint which no longer existed. It compiled perfectly and failed only at load.
So every one of the fourteen jars gets booted and run through §0.

| Target | Jar | Java | Loaders to test | Era tree |
|---|---|---|---|---|
| 1.21.1 | `tradewindow-1.4.3-1.21.1.jar` | 21 | 0.19.3, 0.19.4, 0.19.5 | `eras/1.21` |
| 1.21.2 | `tradewindow-1.4.3-1.21.2.jar` | 21 | 0.19.3 | `eras/1.21.2` |
| 1.21.3 | `tradewindow-1.4.3-1.21.3.jar` | 21 | 0.19.3 | `eras/1.21.2` |
| 1.21.4 | `tradewindow-1.4.3-1.21.4.jar` | 21 | 0.19.3 | `eras/1.21.2` |
| 1.21.5 | `tradewindow-1.4.3-1.21.5.jar` | 21 | 0.19.3 | `eras/1.21.5` |
| 1.21.6 | `tradewindow-1.4.3-1.21.6.jar` | 21 | 0.19.3 | `eras/1.21.6` |
| 1.21.7 | `tradewindow-1.4.3-1.21.7.jar` | 21 | 0.19.3 | `eras/1.21.6` |
| 1.21.8 | `tradewindow-1.4.3-1.21.8.jar` | 21 | 0.19.3, 0.19.4, 0.19.5 | `eras/1.21.6` |
| 1.21.9 | `tradewindow-1.4.3-1.21.9.jar` | 21 | 0.19.3 | `eras/1.21.9` |
| 1.21.10 | `tradewindow-1.4.3-1.21.10.jar` | 21 | 0.19.3 | `eras/1.21.9` |
| 1.21.11 | `tradewindow-1.4.3-1.21.11.jar` | 21 | 0.19.3, 0.19.4, 0.19.5 | `eras/1.21.11` |
| 26.1 | `tradewindow-1.4.3-26.1.jar` | 25 | 0.19.3, 0.19.4, 0.19.5 | `eras/26.1` |
| 26.2 | `tradewindow-1.4.3-26.2.jar` | 25 | 0.19.3 | `eras/26.2` |
| 26.3 | `tradewindow-1.4.3-26.3.jar` | 25 | 0.19.3, 0.19.4, 0.19.5 | `eras/26.3` |

The four mandatory targets — **1.21.1, 1.21.8, 26.1 and 26.3** — each need all three
loaders (0.19.3, 0.19.4, 0.19.5). Between them that covers both Java levels (21 and
25), both Loom pipelines (remapping and not), and one target from each side of the two
26.x API splits. The remaining ten only need 0.19.3: loader behaviour is
version-independent, so the loader matrix is exhausted by those four rather than
repeated fourteen times.

Throughout, the two test players are **Alice** (initiator) and **Bob** (target).

### 2.0 Boot the jar, once per target

A start-up failure is invisible to the compiler, so this check comes first — see
`PORTING_NOTES.md` §4b for the crash that motivated the rewrite, and §13.3 for the
manifest bug that compiled cleanly and was caught only by the packaging gate.

- [ ] Launch the server with that target's jar. It reaches "Done" and the mod is
      listed as loaded.
- [ ] The log contains no `tradewindow` warning, and **no registry-sync or
      unknown-registry-entry error**.
- [ ] Connect a vanilla client with **no mods**: it is not kicked, and the mod does
      not appear in its mod list — correct, because this is a server-only mod.
- [ ] Craft the token (1 Ghast Tear + 1 Gold Ingot → 1 Ghast Tear named
      "Trade Token" in red, stacking to 16) to confirm the recipe parses on that
      version.
- [ ] There is nothing to `/give`: the mod registers no item, so the only token
      that exists is a crafted one.

### 2.1 Happy path — diamond for iron

- [ ] Alice crafts a Trade Token (1 Ghast Tear + 1 Gold Ingot → 1 token).
- [ ] Alice right-clicks Bob while holding the token.
- [ ] Bob receives a chat prompt with `[ Accept ]` and `[ Decline ]`.
- [ ] Bob clicks `[ Accept ]`. Both windows open as a **vanilla double chest**.
- [ ] The container title reads `Trading Window` on each side — no names, no "vs".
- [ ] The layout is: header row, **your own offer in rows 2-3**, the other header
      row, **their offer in rows 5-6**, then the viewer's own inventory.
- [ ] **Mirroring:** Alice sees her own head at slot 4 and Bob's at slot 31; Bob
      sees the opposite. Both are named with the real player names, and hovering one
      shows the name only — no lore.
- [ ] There is **no status paper** in either header row, and no chat line about
      controls.
- [ ] Alice places 3 diamonds in **rows 2-3**. They appear in Bob's window within
      a tick, in **rows 5-6** — her offer is on the bottom half from his side.
- [ ] Bob places 12 iron ingots in **rows 2-3**. They appear in Alice's window in
      rows 5-6.
- [ ] Alice clicks the green `LOCK` block in the **top** header row. It becomes a
      green pane reading `LOCKED`, in **both** windows, and Alice can no longer
      remove her diamonds.
- [ ] Bob clicks the green `LOCK` block in **his** top row. The trade completes and
      both windows close.
- [ ] Alice has 12 iron and no diamonds; Bob has 3 diamonds and no iron.
- [ ] Both saw `[Trade] Trade complete.`
- [ ] The history file has one entry naming both players and both items.

### 2.2 Token is consumed on open, not on request

- [ ] Alice has exactly 1 Trade Token. She right-clicks Bob.
- [ ] While the prompt is pending, check Alice's inventory: **the token is still
      there**.
- [ ] Bob declines. Alice still has the token.
- [ ] Alice right-clicks Bob again and Bob accepts. **Now** the token is gone.
- [ ] With `requireToken` on and no token, `/trade Bob` is refused with a clear
      message and nothing is consumed.

### 2.3 Request times out after 15 seconds

- [ ] Alice right-clicks Bob and neither accepts nor declines.
- [ ] **Nothing is said to either player.** A request that nobody answers expires
      silently; there are no countdown warnings anywhere in this design, and the
      sender is deliberately not told "waiting…".
- [ ] After ~15 s, clicking `[ Accept ]` in the stale prompt reports
      "You have no trade request to accept."
- [ ] Alice's token was **not** consumed.
- [ ] Set `tradeTimeoutSeconds` to 5, restart, and confirm the window is now 5 s.

### 2.4 Window times out after two minutes

- [ ] Open a trade, place items on both sides, and touch nothing.
- [ ] **No chat arrives during the two minutes** — no 30 s warning, no 10 s
      warning, no "waiting for…" line. The window's own state is the only status.
- [ ] At ~120 s **both** players see `[Trade] Trade timed out.` and the window
      closes on both clients.
- [ ] Every placed item is back in its owner's inventory, in its original stack
      sizes and with its data intact.
- [ ] Confirm the same behaviour with `guiTimeoutSeconds` set to 10, restarting
      first.
- [ ] **Also verify:** if both players lock just before 0:00, the trade completes
      rather than timing out.

### 2.5 One disconnects mid-trade

- [ ] Open a trade and have Alice place items.
- [ ] Bob quits to title (or disconnects).
- [ ] Alice's window closes.
- [ ] Alice's items are back in her inventory.
- [ ] Bob's items (if any) are back in his when he rejoins.
- [ ] Alice can immediately start a new trade — she is not left "busy".

### 2.6 One walks away mid-trade

The distance rule ships **off**: `crossDistanceTrading` is true by default, so this
case applies only once an operator turns it off. With it off, the radius is
`maxDistanceBlocks` (20 by default) **and** `cancelOnMove` must be on for the tick loop
to end a running trade — see `PORTING_NOTES.md` §12.

- [ ] `/tradeadmin crossdistance off`, then open a trade and place items on both
      sides.
- [ ] Alice walks more than 20 blocks from Bob (or teleports).
- [ ] The trade cancels on both clients and every item is returned.
- [ ] `/tradeadmin crossdistance on`: the same walk no longer cancels anything.
- [ ] With it off and `cancelOnMove` set to `false`, walking away no longer cancels
      either — both switches have to agree.

### 2.7 One takes damage mid-trade

- [ ] Open a trade with items placed.
- [ ] Damage Alice (fall, mob, `/damage`).
- [ ] The trade cancels and items are returned.
- [ ] Set `cancelOnDamage` to `false` and confirm damage no longer cancels.
- [ ] **Also verify death:** kill Alice outright. The trade must cancel and
      items must be returned, regardless of `cancelOnDamage`.

### 2.8 Blacklisted item trade

- [ ] Alice tries to place `minecraft:bedrock` (give it with `/give`).
- [ ] The item **cannot** be placed — it stays in her inventory and never enters
      the trade grid.
- [ ] Try shift-clicking it in. It still refuses.
- [ ] Add `minecraft:diamond` to `blacklistedItems`, restart, and confirm
      diamonds are now equally refused.
- [ ] Confirm a blacklisted item placed *before* the config change does not
      sneak through on an existing open trade.

### 2.9 Full inventory on completion

- [ ] Fill Alice's inventory completely with 64-stacks of a single item.
- [ ] Trade so that Alice is due to receive items.
- [ ] Complete the trade.
- [ ] The received items are **dropped at Alice's feet**, not deleted.
- [ ] Alice picks them up and has exactly what she should.
- [ ] Verify the same for Bob.

### 2.10 Creative trades with survival (if disabled)

- [ ] With `allowCreativeTrading` at `false`, put Alice in creative and Bob in
      survival.
- [ ] Alice right-clicks Bob: refused with the game-mode message.
- [ ] Bob tries `/trade Alice`: also refused.
- [ ] Set `allowCreativeTrading` to `true`, restart, and confirm it now works.

### 2.11 Slot ownership

- [ ] Open a trade. As Alice, click Bob's side.
- [ ] Nothing can be placed there and nothing can be taken from it.
- [ ] Try shift-clicking an item from Alice's inventory. It can only land on
      Alice's side.
- [ ] Try dragging a carried stack across onto Bob's side. It is refused.
- [ ] With items on both sides, click a side that has been locked: nothing can be
      added to it or taken from it, because the block and its replacement pane are
      server-owned items the menu re-places.

### 2.12 Item data preservation

- [ ] Alice places a named, enchanted, damaged diamond sword.
- [ ] Complete the trade.
- [ ] Bob's sword has the **same custom name, same enchantments, same damage**.
- [ ] Check the `tradewindow.log` line: it should show a data fingerprint in
      `[...]` next to the sword.
- [ ] Repeat with a written book and a shulker box with contents.

### 2.13 Concurrency

- [ ] Alice and Carol both request a trade with Bob.
- [ ] Bob sees only **one** prompt at a time.
- [ ] Bob accepts Alice's. Carol's request is still pending, not lost.
- [ ] When the Alice–Bob trade ends, Carol's prompt becomes actionable.
- [ ] While Alice is trading, `/trade Carol` from Alice is refused.

### 2.14 Self-trade and other refusals

- [ ] `/trade <own name>` is refused.
- [ ] `/trade <offline player>` is refused.
- [ ] `/trade toggle`, then have someone request you: refused with the opt-out
      message. `/trade toggle` again to re-enable.
- [ ] Put Alice and Bob in different dimensions, then request: refused. Set
      `allowCrossDimensionTrading` to `true` and confirm it is allowed.

### 2.15 Server restart mid-trade

- [ ] Open a trade, place items on both sides.
- [ ] Stop the server **while both are still connected**.
- [ ] Restart. Both players still have their items.
- [ ] Now repeat, but disconnect Bob **before** stopping the server.
- [ ] Restart. Alice has her items. When Bob logs in he gets a "returned items"
      message and his items are back.
- [ ] Confirm `tradewindow-pending-returns.*` is removed once everyone has been
      paid out.

### 2.16 Item duplication attempt

- [ ] Open a trade with items on both sides.
- [ ] Close the window with **Esc**. The trade ends on both clients and every
      item is returned exactly once — count them.
- [ ] Repeat, closing with the **inventory key (E)** instead. Same result.
- [ ] Repeat while holding an item on the cursor (pick it up, then Esc). The
      carried stack is returned to the player, not duplicated and not lost.
- [ ] Shift-click a stack back and forth several times, then confirm both sides.
      The totals must be unchanged.

### 2.17 Config handling

- [ ] Delete `config/tradewindow.json` and start the game: it is recreated with
      defaults.
- [ ] Put `"tradeTimeoutSeconds": -5` in the file: after load it reads `5`.
- [ ] Put `"blacklistedItems": null` in the file: the default list is restored,
      not honoured as an empty list.
- [ ] Add an unknown key: it is ignored and the mod still starts.

---

## 3. What is *not* covered

- **No automated integration test against a running game.** Everything in
  `shared/` is unit tested; the Minecraft-facing classes in
  `versions/*/src/main/java` are verified by compilation and by the manual
  checklist above. A headless Fabric server harness would be the natural next
  step for the manager's tick loop.
- **No load or soak testing.** A session count in the hundreds per tick has not
  been measured.
- **The SQLite backend is untested end to end**, because the mod deliberately
  bundles no JDBC driver. The fallback path (JSON lines) is covered by unit
  tests.
