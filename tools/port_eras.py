#!/usr/bin/env python3
"""Generate every non-reference era tree from the 1.21.11 reference tree.

The reference (``eras/1.21.11``) is the v1.4.1 design: nothing is registered with
the game, the token is an ordinary Ghast Tear carrying ``custom_name`` and
``max_stack_size`` components, the window is the vanilla double chest, and the two
players' menus mirror each other so each sees their own offer in the middle rows.

Every other era is this tree plus a small, fully enumerated dialect transform. The
dialect matrix below was established by reading the real Minecraft jars for each
version (see PORTING_NOTES.md), not by guessing:

  era       targets              click   hover   getServer  sound   id     perm   profile  slot        colours
  --------  -------------------  ------  ------  ---------  ------  -----  -----  -------  ----------  -----------
  1.21      1.21.1               ctor    action  direct     notify  rl     lambda ctor     field       per-colour
  1.21.2    1.21.2 - 1.21.4      ctor    action  direct     notify  rl     lambda ctor     field       per-colour
  1.21.5    1.21.5               record  record  direct     notify  rl     lambda ctor     accessor    per-colour
  1.21.6    1.21.6 - 1.21.8      record  record  direct     notify  rl     gate   ctor     accessor    per-colour
  1.21.9    1.21.9 - 1.21.10     record  record  level()    notify  rl     gate   resolved accessor    per-colour
  1.21.11   1.21.11              record  record  level()    level   ident  gate   resolved accessor    per-colour  (reference)
  26.1      26.1                 record  record  level()    level   ident  gate   resolved accessor    per-colour
  26.2      26.2                 record  record  level()    level   ident  gate   resolved accessor    collection
  26.3      26.3                 record  record  level()    level   ident  gate   resolved accessor    collection

The 26.x column continues:

  era       click type                  item holder
  --------  --------------------------  -------------------
  1.21      1.21.1 - 1.21.11: ClickType  getItemHolder()  player.drop(stack, bool)
  26.1      26.1 - 26.3: ContainerInput   typeHolder()     player.drop(stack, bool, Prediction)

26.3 gets its own tree as well, for one signature: ``Player.drop(ItemStack, boolean)``
became ``drop(ItemStack, boolean, Prediction)``. It is passed ``Prediction.SERVER_ONLY``,
which is what vanilla's own authoritative server-side callers use (``AdvancementRewards``,
``ServerPlaceRecipe``, the menus); ``PREDICTED`` belongs to client-predicted actions.

Evidence for the less obvious columns:

* ``id``     1.21.11 renamed ``ResourceLocation`` to ``net.minecraft.resources.Identifier``;
             26.x keeps that name. Everything from 1.21 to 1.21.10 uses
             ``ResourceLocation.fromNamespaceAndPath`` and ``ResourceKey.location()``.
* ``perm``   ``Commands.hasPermission(int)`` first appears in 1.21.6. Before that the
             idiom is ``.requires(source -> source.hasPermission(LEVEL_GAMEMASTERS))``
             (``LEVEL_GAMEMASTERS`` is an ``int`` in every version, not a predicate).
* ``profile`` ``ResolvableProfile.createResolved(GameProfile)`` exists from 1.21.9;
             before that the record's ``ResolvableProfile(GameProfile)`` constructor is
             the way to build a head that already carries its skin.
* ``slot``   ``Inventory.getSelectedSlot()`` arrives with the 1.21.5 attribute rework.
             1.21.1 - 1.21.4 expose the index as the public ``selected`` field instead;
             their ``getSelected()`` returns the selected *stack*, not its index.
* ``colours`` 1.21.1 - 26.1 have one ``Items`` constant per colour. 26.2 collapsed them
             into ``ColorCollection`` records (``Items.CONCRETE.green()``,
             ``Items.STAINED_GLASS_PANE.black()``) while keeping the registry ids -
             ``block.minecraft.green_concrete`` is still there in 26.3.
* ``clicktype`` 26.x renamed the click enum: ``ClickType`` became ``ContainerInput``.
             The constants are unchanged, so only the type name moves, and
             ``AbstractContainerMenu.clicked`` still takes it as its third argument.
* ``holder``  26.x renamed ``ItemStack.getItemHolder()`` to ``typeHolder()``; both
             return the ``Holder<Item>`` whose key gives the item's id.

Recipe and advancement JSON are per version, not per era, and were read out of the
vanilla jars instead of being assumed:

* every target from 1.21.1 up uses the *singular* ``data/<ns>/recipe/`` and
  ``data/<ns>/advancement/`` folders. (The original brief for this port said 1.21.1 -
  1.21.8 use the plural form and that the rename happened in 1.21.9; the vanilla jars
  say otherwise - ``data/minecraft/recipe/`` is already singular in 1.21.1.)
* 1.21.1 spells an ingredient as ``{"item": "minecraft:x"}``; 1.21.2 and later use the
  bare string ``"minecraft:x"``.
* the result carries ``components`` on *every* target: 1.21.1's ``ItemStack`` codec
  has the same ``id``/``count``/``components`` keys as 1.21.11's, it simply has no
  vanilla recipe using them.
* the token's text components are written in the *flat* form - a JSON string that
  contains JSON - because 1.21.1's component codec rejects the equivalent tree form
  (an object) and then drops the whole recipe. It loads with a parse error and no
  token, which is how a build that compiled perfectly still shipped broken. 1.21.11
  accepts either form, so the flat one is used everywhere.
* the ``minecraft:recipe_unlocked`` trigger's condition is called ``recipe`` on every
  target up to 26.2 and ``recipes`` on 26.3, where the wrong name does not merely drop
  the advancement but stops the server from loading its data packs at all.

Usage:
    python tools/port_eras.py            # write every era
    python tools/port_eras.py 1.21.6     # write one era
"""

import json
import os
import re
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# The reference. Never written by this tool: it is the source of truth every other
# tree is derived from, so regenerating it would make the generator its own input.
REF = "1.21.11"

# eras/1.20.1 is retired (see PORTING_NOTES.md §13): the design this port carries
# needs data components, which 1.20.1 does not have, and the target is outside the
# version list this build ships for.
RETIRED = {"1.20.1"}

ERAS = {
    # era       click   hover   server   sound   id      perm    profile  slot              colours
    # era       click   hover   server   sound   id      perm    profile  slot              colours
    "1.21":    dict(click="ctor",   hover="action", server="direct", sound="notify", id="rl",    perm="lambda", profile="ctor",     slot="field",     colours="per-colour", clicktype="ClickType", holder="getItemHolder"),
    "1.21.2":  dict(click="ctor",   hover="action", server="direct", sound="notify", id="rl",    perm="lambda", profile="ctor",     slot="field",     colours="per-colour", clicktype="ClickType", holder="getItemHolder"),
    "1.21.5":  dict(click="record", hover="record", server="direct", sound="notify", id="rl",    perm="lambda", profile="ctor",     slot="accessor",  colours="per-colour", clicktype="ClickType", holder="getItemHolder"),
    "1.21.6":  dict(click="record", hover="record", server="direct", sound="notify", id="rl",    perm="gate",   profile="ctor",     slot="accessor",  colours="per-colour", clicktype="ClickType", holder="getItemHolder"),
    "1.21.9":  dict(click="record", hover="record", server="level",  sound="notify", id="rl",    perm="gate",   profile="resolved", slot="accessor",  colours="per-colour", clicktype="ClickType", holder="getItemHolder"),
    "26.1":    dict(click="record", hover="record", server="level",  sound="level",  id="ident", perm="gate",   profile="resolved", slot="accessor",  colours="per-colour", clicktype="ContainerInput", holder="typeHolder"),
    "26.2":    dict(click="record", hover="record", server="level",  sound="level",  id="ident", perm="gate",   profile="resolved", slot="accessor",  colours="collection", clicktype="ContainerInput", holder="typeHolder"),
    "26.3":    dict(click="record", hover="record", server="level",  sound="level",  id="ident", perm="gate",   profile="resolved", slot="accessor",  colours="collection", clicktype="ContainerInput", holder="typeHolder", drop="prediction"),
}

# Which data-pack folders to write, and how an ingredient is spelled. Every target
# this build ships for is singular; only the ingredient shape splits.
RECIPE_INGREDIENT_STYLE = {
    "1.21": "object",
}
DEFAULT_INGREDIENT_STYLE = "string"

# How the result's *text components* are spelled (``custom_name``, each ``lore``
# line). This one is not cosmetic and it does not follow the same boundary as the
# ingredients above - it is the exact opposite boundary, which is what made it worth
# measuring rather than assuming:
#
#   1.21.1   ``custom_name`` is a *string* codec that JSON-parses its input. An
#            object is rejected outright - "Not a string: {...}" - and the recipe
#            is dropped, so no token can ever be crafted. An escaped-JSON string is
#            parsed as JSON and renders as a styled name.
#
#   1.21.2+  the component codec accepts a JSON object (the real component form) and
#   and all  also accepts a string - but treats that string as LITERAL text. So an
#   26.x     escaped-JSON string loads perfectly and renders the raw JSON as the
#            item's name and lore.
#
# Both directions were verified by booting real servers per target: an *invalid* JSON
# string is a parse error on 1.21.1 (``EOFException ... path $.text``) and is accepted
# in silence on 1.21.2+, and an object is rejected on 1.21.1 and accepted everywhere
# else. Each side therefore has exactly one usable spelling.
RECIPE_COMPONENT_STYLE = {
    "1.21": "string",
}
DEFAULT_COMPONENT_STYLE = "object"

# The field name inside the ``minecraft:recipe_unlocked`` trigger's conditions.
# Every target up to and including 26.2 spells it ``recipe``; 26.3 renamed it to
# ``recipes``. This one is not cosmetic like the others: on 26.3 the advancement
# fails to parse, and a failed *advancement* is fatal to the whole data pack load -
# "Failed to load datapacks, can't proceed with server load" - so the server never
# starts at all. Verified against each version's own
# ``data/minecraft/advancement/recipes/brewing/blaze_powder.json``.
ADVANCEMENT_RECIPE_KEY = {
    "26.3": "recipes",
}
DEFAULT_ADVANCEMENT_RECIPE_KEY = "recipe"


def java_files(era_dir):
    """Every .java file in an era tree, as (absolute path, path relative to java/)."""
    base = os.path.join(era_dir, "java")
    for dirpath, _dirnames, filenames in os.walk(base):
        for name in sorted(filenames):
            if name.endswith(".java"):
                full = os.path.join(dirpath, name)
                yield full, os.path.relpath(full, base)


def transform(source, cfg):
    """Applies one era's dialect to a Java source file."""
    text = source

    # -- chat events: records from 1.21.5, the Action constructors before that -----
    if cfg["click"] == "ctor":
        text = text.replace(
            "new ClickEvent.RunCommand(command)",
            "new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)")
    if cfg["hover"] == "action":
        text = text.replace(
            "new HoverEvent.ShowText(",
            "new HoverEvent(HoverEvent.Action.SHOW_TEXT,")

    # -- server lookup: Entity.getServer() was removed in 1.21.9 -------------------
    if cfg["server"] == "direct":
        text = text.replace(".level().getServer()", ".getServer()")

    # -- the confirmation sound: playNotifySound() was removed in 1.21.11 ----------
    if cfg["sound"] == "notify":
        text = re.sub(
            r"player\.level\(\)\.playSound\(null, player\.getX\(\), player\.getY\(\), player\.getZ\(\),\s*"
            r"SoundEvents\.EXPERIENCE_ORB_PICKUP, SoundSource\.PLAYERS, 1\.0F, 1\.2F\);",
            "player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.2F);",
            text)

    # -- /tradeadmin's permission gate --------------------------------------------
    if cfg["perm"] == "lambda":
        text = text.replace(
            ".requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))",
            ".requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))")

    # -- the held slot -------------------------------------------------------------
    # Before 1.21.5 the selected slot is the public `selected` field; `getSelected()`
    # exists there too but returns the selected *ItemStack*, not its index. The
    # accessor `getSelectedSlot()` arrives with the 1.21.5 attribute rework.
    if cfg["slot"] == "field":
        text = text.replace(".getInventory().getSelectedSlot()",
                            ".getInventory().selected")

    # -- player heads --------------------------------------------------------------
    if cfg["profile"] == "ctor":
        text = text.replace("ResolvableProfile.createResolved(owner.getGameProfile())",
                            "new ResolvableProfile(owner.getGameProfile())")

    # -- coloured glass panes and concrete ----------------------------------------
    if cfg["colours"] == "collection":
        # 26.2 merged the sixteen colour variants of each item into one
        # ColorCollection record. The registry ids are unchanged.
        text = text.replace("Items.BLACK_STAINED_GLASS_PANE",
                            "Items.STAINED_GLASS_PANE.black()")
        text = text.replace("Items.GREEN_STAINED_GLASS_PANE",
                            "Items.STAINED_GLASS_PANE.green()")
        text = text.replace("Items.GREEN_CONCRETE", "Items.CONCRETE.green()")
        text = text.replace("Items.RED_CONCRETE", "Items.CONCRETE.red()")

    # -- dropping what will not fit ------------------------------------------------
    # 26.3 added a Prediction argument to LivingEntity.drop, so the authoritative
    # server-side drop has to say so explicitly. Only the file that drops changes,
    # which is why the import is added next to the call rather than per era.
    if cfg.get("drop") == "prediction" and "player.drop(toInsert, false)" in text:
        text = text.replace("player.drop(toInsert, false)",
                            "player.drop(toInsert, false, Prediction.SERVER_ONLY)")
        if "import net.minecraft.util.Prediction;" not in text:
            text = text.replace(
                "import net.minecraft.world.entity.player.Player;",
                "import net.minecraft.util.Prediction;\nimport net.minecraft.world.entity.player.Player;",
                1)

    # -- ItemStack's item holder: getItemHolder() became typeHolder() in 26.x ------
    if cfg["holder"] == "typeHolder":
        text = text.replace("stack.getItemHolder()", "stack.typeHolder()")

    # -- the container click enum: ClickType became ContainerInput in 26.x ---------
    if cfg["clicktype"] == "ContainerInput":
        text = text.replace("net.minecraft.world.inventory.ClickType",
                            "net.minecraft.world.inventory.ContainerInput")
        # Any remaining mention is a usage or a doc comment; both want the new name,
        # and the constants (PICKUP, QUICK_MOVE, ...) are identical.
        text = text.replace("ClickType", "ContainerInput")

    # -- ResourceLocation / Identifier --------------------------------------------
    if cfg["id"] == "rl":
        text = text.replace("key.identifier()", "key.location()")
        text = text.replace("net.minecraft.resources.Identifier",
                            "net.minecraft.resources.ResourceLocation")
        text = text.replace("Identifier.fromNamespaceAndPath",
                            "ResourceLocation.fromNamespaceAndPath")
        # Any remaining mention is prose in a doc comment; rewording it here keeps
        # each era's documentation true for the class the era actually uses.
        text = text.replace("Identifier", "ResourceLocation")

    return text


def write_recipe(dst_dir, style, component_style=DEFAULT_COMPONENT_STYLE):
    """Writes data/tradewindow/recipe/trade_token.json in the era's dialect.

    ``style`` is the ingredient spelling (``string`` or ``object``);
    ``component_style`` is the text-component spelling (``object`` for the reference
    tree, ``string`` for 1.21.1). See ``RECIPE_COMPONENT_STYLE`` for why both exist.
    """
    with open(os.path.join(ROOT, "eras", REF, "resources", "data", "tradewindow",
                           "recipe", "trade_token.json"), "r", encoding="utf-8") as fh:
        recipe = json.load(fh)

    if style == "object":
        recipe["ingredients"] = [{"item": item} for item in recipe["ingredients"]]

    if component_style == "string":
        components = recipe["result"]["components"]
        # One line each, no spaces: the exact shape the string codec expects.
        components["minecraft:custom_name"] = json.dumps(
            components["minecraft:custom_name"], separators=(",", ":"))
        components["minecraft:lore"] = [
            json.dumps(line, separators=(",", ":")) for line in components["minecraft:lore"]]

    out_dir = os.path.join(dst_dir, "resources", "data", "tradewindow", "recipe")
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "trade_token.json"), "w", encoding="utf-8") as fh:
        json.dump(recipe, fh, indent="\t", ensure_ascii=False)
        fh.write("\n")
    return style


def write_advancement(src, dst, recipe_key):
    """Copies the reference advancement, renaming the recipe_unlocked key if needed."""
    with open(src, "r", encoding="utf-8") as fh:
        advancement = json.load(fh)

    conditions = advancement["criteria"]["has_the_recipe"]["conditions"]
    if recipe_key not in conditions:
        conditions[recipe_key] = conditions.pop("recipe")

    with open(dst, "w", encoding="utf-8") as fh:
        json.dump(advancement, fh, indent="\t", ensure_ascii=False)
        fh.write("\n")
    return recipe_key


def build(era, cfg):
    dst = os.path.join(ROOT, "eras", era)
    ref = os.path.join(ROOT, "eras", REF)

    # Start from a clean tree: the previous revision's item/, platform/ and
    # TradeLogger.java (and every texture and model for the custom item) must be
    # gone, not merely unused, or the jar still ships a registry entry's assets.
    if os.path.isdir(dst):
        shutil.rmtree(dst)
    shutil.copytree(os.path.join(ref, "java"), os.path.join(dst, "java"))

    changed = 0
    for full, rel in java_files(dst):
        with open(full, "r", encoding="utf-8") as fh:
            source = fh.read()
        out = transform(source, cfg)
        if out != source:
            with open(full, "w", encoding="utf-8") as fh:
                fh.write(out)
            changed += 1

    # Resources: the language file and the icon are version-independent. The recipe
    # and the advancement each carry one version-dependent spelling (the ingredient
    # shape, and the recipe_unlocked condition key).
    res_dst = os.path.join(dst, "resources")
    lang_dst = os.path.join(res_dst, "assets", "tradewindow", "lang")
    os.makedirs(lang_dst, exist_ok=True)
    shutil.copy2(os.path.join(ref, "resources", "assets", "tradewindow", "lang", "en_us.json"),
                 os.path.join(lang_dst, "en_us.json"))
    shutil.copy2(os.path.join(ref, "resources", "assets", "tradewindow", "icon.png"),
                 os.path.join(res_dst, "assets", "tradewindow", "icon.png"))
    adv_src = os.path.join(ref, "resources", "data", "tradewindow", "advancement",
                           "recipes", "misc", "trade_token.json")
    adv_dst = os.path.join(res_dst, "data", "tradewindow", "advancement", "recipes",
                           "misc", "trade_token.json")
    os.makedirs(os.path.dirname(adv_dst), exist_ok=True)
    write_advancement(adv_src, adv_dst,
                      ADVANCEMENT_RECIPE_KEY.get(era, DEFAULT_ADVANCEMENT_RECIPE_KEY))

    style = RECIPE_INGREDIENT_STYLE.get(era, DEFAULT_INGREDIENT_STYLE)
    component_style = RECIPE_COMPONENT_STYLE.get(era, DEFAULT_COMPONENT_STYLE)
    write_recipe(dst, style, component_style)

    return changed, style


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    fmt = "%-8s %-16s %-8s %-6s %-7s %-6s %-9s %-8s %-11s %s"
    print(fmt % ("ERA", "SOURCES", "CLICK", "HOVER", "SERVER", "SOUND", "ID", "PERM",
                 "PROFILE", "RECIPE/COLOURS"))
    print("-" * 118)
    for era in sorted(ERAS, key=lambda e: (len(e), e)):
        if only is not None and era != only:
            continue
        cfg = ERAS[era]
        changed, style = build(era, cfg)
        print(fmt % (era, f"{changed} rewritten", cfg["click"], cfg["hover"],
                     cfg["server"], cfg["sound"], cfg["id"], cfg["perm"],
                     cfg["profile"], f"{style} / {cfg['colours']}"))


if __name__ == "__main__":
    main()
