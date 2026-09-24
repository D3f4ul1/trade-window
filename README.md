<div align="center">

<img src="docs/images/banner.png" alt="Trade Window — a safe trade window for your Minecraft server" width="100%">

# 🪟 Trade Window

**A safe trade window for your Minecraft server — and your players install nothing.**

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1_%E2%86%92_26.3-3fb950?style=for-the-badge&logo=minecraft&logoColor=white)
![Version](https://img.shields.io/badge/version-1.4.3-58a6ff?style=for-the-badge)
![Loader](https://img.shields.io/badge/Fabric_Loader-0.19.3+-dbb44a?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21_%7C_25-e06c75?style=for-the-badge)
![License](https://img.shields.io/badge/license-MIT-8957e5?style=for-the-badge)

</div>

Two players open one window, each drops in what they are offering, each presses **LOCK**,
and the items swap — **all at once or not at all**. It looks like an ordinary double
chest, because it *is* one. That single decision is why nothing has to be installed on
the people trading: the game already knows how to draw a chest.

## 🕹️ How it works

1. 🪙 Craft a **Trade Token** — a red Ghast Tear.
2. 👤 Hold it and **right-click** another player (or type `/trade <name>`).
3. 💬 They click **[ Accept ]** in chat, and the window opens.

<div align="center">

<img src="docs/images/request.png" alt="[Trade] PeacefulNull wants to trade. [ Accept ] [ Decline ]" width="50%">

*One line, two buttons. Nothing has been spent yet.*

</div>

4. 📦 Each side drops items into **their own rows** and clicks the 🟩 **green block**.
5. ✅ Both locked → the items swap and the window closes.

That is the whole mod. Chat carries only what matters — a request, a cancellation, a
timeout. It never narrates item by item, and lock/cancel are clicks **inside the window**,
not commands.

## 🪙 The Trade Token

<div align="center">

<img src="docs/images/recipe.png" alt="One ghast tear plus one gold ingot makes a Ghast Tear named Trade Token" width="56%">

*Ghast Tear + Gold Ingot, shapeless — any two grid slots.*

</div>

The result is a **normal Ghast Tear** carrying a name, lore and a stack size of 16. No
mod item is registered anywhere, so no client can ever be kicked for not knowing it.

## 🪟 The window

<div align="center">

<img src="docs/images/window.png" alt="The trading window as one player sees it" width="44%">
<img src="docs/images/window-mirrored.png" alt="The same trade as the other player sees it" width="44%">

*The same trade, same instant, on both players' screens.*

</div>

- **Your rows are 2–3, theirs are 5–6** — always, for both players. The two pictures
  above are proof: the same armour sits in the middle rows on the left and in the bottom
  rows on the right, because each player's own offer is the middle rows.
- 🟩 **Green block = LOCK** your side. It turns into a green pane saying `LOCKED`, and
  both players see it.
- 🟥 **Red block = CANCEL.** Either player may cancel at any time.
- **Nobody can touch the other side's items** — enforced on the server for every click.

**Good to know:** closing the window cancels the trade and returns every item, and so do
a disconnect, a death, a timeout, or (if you switch it on) walking out of range. Items
always go back to their owner — nothing is dropped on the floor and nothing is lost.

## 💬 Commands

**Players** — permission level 0:

| Command | What it does |
|---|---|
| `/trade <player>` | Sends a trade request (needs a Trade Token in your inventory). |
| `/trade accept` | Accepts the request shown to you. |
| `/trade decline` | Declines it. |

**Operators** — permission level 2:

| Command | What it does |
|---|---|
| `/tradeadmin crossdistance <on\|off>` | Trade from any distance, or enforce the block limit. Saved to the config immediately. |
| `/tradeadmin list` | Every live trade: both players, elapsed time, lock state. |
| `/tradeadmin spectate <player>` | Watch a trade read-only. Nothing changes for the players. |
| `/tradeadmin history [player]` | The last 20 completed trades. |

There is deliberately no `/trade lock`, `/trade unlock` or `/trade cancel`: six ways to
say two things is how trading mods get confusing.

## ⚙️ Configuration

`config/tradewindow.json` is written on first run, and **every field is optional** — a
missing line keeps its default. Delete a line, or the whole file, to go back.

```json
{
  "tradeTimeoutSeconds": 15,
  "guiTimeoutSeconds": 120,
  "maxDistanceBlocks": 20,
  "crossDistanceTrading": true,
  "cancelOnDamage": true,
  "cancelOnMove": false,
  "requireToken": true,
  "logTrades": true,
  "blacklistedItems": ["minecraft:bedrock", "minecraft:command_block"],
  "maxTradeValue": -1
}
```

| Key | Default | Meaning |
|---|---|---|
| `tradeTimeoutSeconds` | `15` | How long an invitation stays pending. |
| `guiTimeoutSeconds` | `120` | How long the window may stay open. |
| `crossDistanceTrading` | `true` | Trades work at any distance. |
| `maxDistanceBlocks` | `20` | The distance limit, used only when `crossDistanceTrading` is off. |
| `cancelOnMove` | `false` | Master switch for the distance rule. |
| `cancelOnDamage` | `true` | End the trade if either player is hit. |
| `requireToken` | `true` | Require a Trade Token, and spend it when the window opens. |
| `logTrades` | `true` | One line per finished trade in `tradewindow.log`. |
| `blacklistedItems` | bedrock, command block | Items that can never enter a window. |
| `maxTradeValue` | `-1` | Cap on the combined offer value; `-1` disables it. |

The file also carries `allowCreativeTrading`, `allowCrossDimensionTrading` and
`useDatabase`. Sane limits are applied on load — a negative timeout becomes 5 seconds —
and anything odd is written back so you can see what was used. Every value has a matching
line in the file itself, so nothing is hidden in code.

## 🔒 Server-side only

Install Trade Window **where the world runs** — a dedicated server, or your own
single-player world (Fabric runs that as an internal server, so the same jar goes in your
`mods/` folder). Connecting players install **nothing at all**.

No item, no block, no entity, no packet, no screen, no key bind: the mod adds zero
entries to any registry, which is exactly why a vanilla client cannot be kicked and why
nothing has to be kept in sync. There is no client half to install, because there is no
client half.

## 📦 Install

1. Install **Fabric Loader 0.19.3 or newer** where the game runs.
2. Put **Fabric API** and the **jar for your Minecraft version** in that `mods/` folder.
3. Start it. Players connect with no mods.

**Jars & versions:** [Releases](../../releases) holds one jar per game version —
**1.21.1 → 1.21.11** (Java 21) and **26.1 → 26.3** (Java 25). Each jar declares the
exact Minecraft version it is for, so a mismatch refuses to load instead of misbehaving.

## 🗺️ Roadmap

- [x] Vanilla-client trading window, in-window LOCK / CANCEL, operator tools
- [x] 14 Minecraft targets from one design
- [ ] 🚧 **A companion client mod with a proper GUI** — drawn buttons, a countdown,
      drag-and-drop. This server-side build stays the compatible fallback: the same
      server keeps serving vanilla clients, and the client mod adds to it rather than
      replacing it.

## 📚 More

| Document | What is in it |
|---|---|
| [`TESTING.md`](TESTING.md) | What has been tested on each version, and what has not |
| [`PORTING_NOTES.md`](PORTING_NOTES.md) | The 14 targets and every quiet API difference between them |
| [`DESIGN_DECISIONS.md`](DESIGN_DECISIONS.md) | Why each choice was made, including the ones that were wrong first |
| [`CHANGELOG.md`](CHANGELOG.md) | What changed in each release |

**Building from source:** `./gradlew buildAll` (needs JDK 21, plus JDK 25 for the 26.x
targets), `./gradlew :shared:test` for the pure-Java core, and
`python tools/verify_packaging.py` for the checks a compiler cannot make. The pictures in
this README are produced by `tools/make_screenshots.py` and `tools/make_assets.py`.

## 📜 License

MIT — see [`LICENSE`](LICENSE). Use it, fork it, ship it on your server.
