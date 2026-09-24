#!/usr/bin/env python3
"""Verify every shipped artifact, and the era trees they are built from.

This is the gate that runs after ``./gradlew buildAll``. It checks the two things
a compiler cannot:

  1. the *source* trees — that no era registers anything with the game, that every
     language key a tree asks for exists in that tree's own language file, and that
     no era has drifted away from the reference's file set;
  2. the *jars* — that each of the 14 targets was compiled for the Java version its
     Minecraft version requires, that each one declares ``"environment": "*"`` with a
     single ``main`` entrypoint, that none of them ships a client/item/network class,
     and that each one's recipe and advancement are valid for that Minecraft version.

The recipe checks are the ones a build cannot make for you: a data-pack JSON that
names the wrong folder, spells an ingredient the old way or omits the token's
components loads as *nothing*, with no error anywhere. The per-version rules here
were read out of the vanilla jars (see PORTING_NOTES.md).

One of those rules is an *encoding* rather than a shape, and it shipped as a real bug
in both directions. The token's ``custom_name`` and ``lore`` can be spelled two ways,
and each side of the 1.21.1/1.21.2 line accepts exactly one of them while rejecting
the other *silently*:

* **1.21.1** needs the *flat* form — a JSON string containing JSON. Its``custom_name``
  codec parses the string as JSON; the tree form is rejected outright and the whole
  recipe is then dropped, so no token can ever be crafted.
* **1.21.2 and later** need the *tree* form. Their component codec takes the object
  and reads a string as **literal text**, so the flat form loads happily and the
  token's name becomes the raw JSON, braces and all.

Neither failure is visible to a compiler or a unit test: one is a data file read at
runtime, the other renders wrong only on a client. See PORTING_NOTES.md §14.

Usage:
    python tools/verify_packaging.py
"""

import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MATRIX = os.path.join(ROOT, "gradle", "versions.gradle")
REF = "1.21.11"


def read_mod_version():
    """The version every jar must carry, read from gradle.properties rather than
    duplicated here - a bump should not need two edits, and a stale copy here would
    fail every target for a reason that has nothing to do with the jar."""
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("mod_version="):
                return line.split("=", 1)[1].strip()
    raise SystemExit("mod_version is missing from gradle.properties")


MOD_VERSION = read_mod_version()

# Grep'd for by name in every era tree: writing to any of these is a registry
# access, and a registry entry a vanilla client does not know is a kick.
REGISTRY_MARKERS = [
    "Registry.register",
    "BuiltInRegistries.",
    "Registries.ITEM",
    "Registry.registerForHolder",
]

FORBIDDEN_PACKAGES = {
    "item": "a custom item",
    "client": "client-side code",
    "screen": "a custom screen",
    "network": "a custom packet",
    "platform": "the retired per-era item adapter",
}

# Java class-file major version per release level.
CLASS_MAJOR = {17: 61, 21: 65, 25: 69}

ENTRY = re.compile(
    r"'(?P<name>v[\w]+)'\s*:\s*\[\s*"
    r"mc:\s*'(?P<mc>[^']+)'\s*,\s*"
    r"era:\s*'(?P<era>[^']+)'\s*,\s*"
    r"java:\s*(?P<java>\d+)\s*,\s*"
    r"api:\s*'(?P<api>[^']+)'\s*,\s*"
    r"loader:\s*'(?P<loader>[^']+)'\s*,\s*"
    r"remap:\s*(?P<remap>true|false)\s*\]"
)


# The one target whose component codec parses an escaped JSON string. Everything
# else takes a component object and reads a string as literal text.
STRING_FORM_TARGETS = {"1.21.1"}


def check_component_encoding(gate, label, wants_string, name_value, lore_value):
    """Asserts the recipe's text components use the form this target's codec takes.

    Both spellings load somewhere and fail somewhere else, and the failure is silent
    in both directions - so this is asserted for every target *and* every era tree.
    See the module docstring and PORTING_NOTES.md §14.
    """
    wanted = "flat string" if wants_string else "component object"
    consequence = ("the recipe will not load and no token can be crafted"
                   if wants_string else "the token's name renders as raw JSON")

    gate.check(isinstance(name_value, str) == wants_string,
               f"{label}: custom_name is a {type(name_value).__name__} but needs a"
               f" {wanted} here - {consequence}")
    gate.check(isinstance(lore_value, list)
               and all(isinstance(entry, str) == wants_string for entry in lore_value),
               f"{label}: lore is not a {wanted} here - {consequence}")


def text_component(value):
    """Decode a text component from a result's components, in either encoding.

    The *flat* form is a JSON string that contains JSON
    (``"{\"text\":\"Trade Token\",...}"``); the *tree* form is that same JSON as an
    object. Both decode to the same component; which one a given Minecraft version
    accepts is decided by check_component_encoding() above.
    """
    if isinstance(value, str):
        try:
            decoded = json.loads(value)
        except ValueError:
            return {"text": value}
        return decoded if isinstance(decoded, dict) else {"text": str(decoded)}
    return value or {}


class Gate:
    def __init__(self):
        self.checks = 0
        self.failures = []

    def check(self, condition, message):
        self.checks += 1
        if not condition:
            self.failures.append(message)
        return bool(condition)

    def note(self, message):
        self.checks += 1


def read_matrix():
    with open(MATRIX, encoding="utf-8") as handle:
        return [m.groupdict() for m in ENTRY.finditer(handle.read())]


def java_files(base):
    for dirpath, _dirnames, filenames in os.walk(base):
        for name in filenames:
            if name.endswith(".java"):
                yield os.path.join(dirpath, name)


def check_sources(gate, targets):
    era_root = os.path.join(ROOT, "eras")
    eras = sorted(os.listdir(era_root))
    targets_by_era = {}
    for target in targets:
        targets_by_era.setdefault(target["era"], []).append(target["mc"])

    gate.check(REF in eras, f"the reference tree eras/{REF} is missing")
    reference_files = {os.path.relpath(p, os.path.join(era_root, REF))
                       for p in java_files(os.path.join(era_root, REF))}

    # Language keys are read out of Java and looked up in the same era's file, so a
    # typo shows up here rather than as "tradewindow.chat.lock" in someone's chat.
    key_pattern = re.compile(r'TradeText\.(?:get|format|has)\("([a-z0-9._]+)"\)')

    for era in eras:
        base = os.path.join(era_root, era)
        tree_files = {os.path.relpath(p, base) for p in java_files(base)}

        # 1. Same shape as the reference: nothing dropped, nothing extra.
        gate.check(tree_files == reference_files,
                   f"eras/{era} file set differs from the reference "
                   f"(extra={sorted(tree_files - reference_files)}, "
                   f"missing={sorted(reference_files - tree_files)})")

        # 2. Nothing registered, on any era.
        for path in java_files(base):
            with open(path, encoding="utf-8") as handle:
                text = handle.read()
            for marker in REGISTRY_MARKERS:
                gate.check(marker not in text,
                           f"eras/{era}/{os.path.basename(path)} touches {marker}: "
                           f"a registry entry would kick vanilla clients")

        # 3. No client-side or item-side package anywhere.
        for package, what in FORBIDDEN_PACKAGES.items():
            gate.check(not os.path.isdir(os.path.join(base, "java", "com",
                                                      "tradewindow", package)),
                       f"eras/{era} still has {what} (com/tradewindow/{package})")

        # 4. No assets for content that no longer exists.
        gate.check(not os.path.isdir(os.path.join(base, "resources", "assets",
                                                  "tradewindow", "models")),
                   f"eras/{era} ships an item model for content that is not registered")
        gate.check(not os.path.isdir(os.path.join(base, "resources", "assets",
                                                  "tradewindow", "textures")),
                   f"eras/{era} ships item textures for content that is not registered")

        # 5. Every key asked for exists in this era's language file.
        with open(os.path.join(base, "resources", "assets", "tradewindow", "lang",
                              "en_us.json"), encoding="utf-8") as handle:
            lang = json.load(handle)
        used = set()
        for path in java_files(base):
            with open(path, encoding="utf-8") as handle:
                used.update(key_pattern.findall(handle.read()))
        missing = sorted(used - set(lang))
        gate.check(not missing, f"eras/{era} uses language keys that are not defined: {missing}")
        gate.check("container.tradewindow.title" in lang,
                   f"eras/{era} does not define the window title key")
        gate.check(lang.get("container.tradewindow.title") == "Trading Window",
                   f"eras/{era} window title is not exactly 'Trading Window'")

        # 6. The data-pack folders are the singular ones in every target this build
        #    ships for (evidence: data/minecraft/recipe/ is already singular in 1.21.1).
        gate.check(os.path.isdir(os.path.join(base, "resources", "data", "tradewindow",
                                              "recipe")),
                   f"eras/{era} has no data/tradewindow/recipe/ folder")
        gate.check(not os.path.isdir(os.path.join(base, "resources", "data", "tradewindow",
                                                  "recipes")),
                   f"eras/{era} uses the retired plural recipes/ folder")

        # 7. The red token name is one string in three places, so it is checked in all
        #    three: the language file's mention, the constant, and the recipe. The
        #    recipe is checked for its *encoding* too - see text_component().
        token = os.path.join(base, "resources", "data", "tradewindow", "recipe",
                             "trade_token.json")
        with open(token, encoding="utf-8") as handle:
            recipe = json.load(handle)
        components = recipe["result"]["components"]
        raw_name = components["minecraft:custom_name"]
        lore = components.get("minecraft:lore")
        # A tree can serve several targets, and they must agree about the encoding -
        # if they ever do not, the tree has to split the way eras/1.21 already does.
        served = sorted(targets_by_era.get(era, []))
        wants_string_set = {mc in STRING_FORM_TARGETS for mc in served}
        gate.check(len(wants_string_set) <= 1,
                   f"eras/{era} serves targets needing different component forms: {served}")
        check_component_encoding(gate, f"eras/{era} (for {', '.join(served)})",
                                 bool(wants_string_set) and wants_string_set.pop(),
                                 raw_name, lore)
        name = text_component(raw_name)
        gate.check(name.get("text") == "Trade Token",
                   f"eras/{era} recipe names the token {name.get('text')!r}, not 'Trade Token'")
        gate.check(name.get("color") == "red" and name.get("italic") is False,
                   f"eras/{era} token name is not red and non-italic")
        gate.check(isinstance(lore, list) and lore,
                   f"eras/{era} lore is missing")


def check_jar(gate, target):
    name = target["name"]
    mc = target["mc"]
    java = int(target["java"])
    libs = os.path.join(ROOT, "versions", mc, "build", "libs")
    jars = [f for f in os.listdir(libs)
            if f.endswith(".jar") and "-sources" not in f] if os.path.isdir(libs) else []
    if not gate.check(len(jars) == 1, f"{name}: expected exactly one jar, found {jars}"):
        return
    path = os.path.join(libs, jars[0])
    gate.check(os.path.getsize(path) > 30000, f"{name} jar looks too small")

    with zipfile.ZipFile(path) as zf:
        names = zf.namelist()

        # -- Java level: a jar built for the wrong Java will not load at all ------
        major = CLASS_MAJOR[java]
        classes = [n for n in names if n.endswith(".class")]
        gate.check(classes, f"{name} contains no classes")
        seen = set()
        for entry in classes:
            head = zf.read(entry)[:8]
            seen.add(head[6] << 8 | head[7])
        gate.check(seen == {major},
                   f"{name}: class-file major version {sorted(seen)} != {major} (Java {java})")

        # -- manifest -------------------------------------------------------------
        meta = json.loads(zf.read("fabric.mod.json"))
        # "*", not "server": Fabric refuses to load a "server" mod on a physical
        # client (ModEnvironment.SERVER.matches(CLIENT) is false), so a server-only
        # manifest silently excludes single-player. Nothing in the jar is client
        # code, which is what this check is really protecting.
        gate.check(meta.get("environment") == "*",
                   f"{name}: environment is {meta.get('environment')!r}, not '*' — a "
                   f"'server' only manifest does not load in single-player")
        entrypoints = meta.get("entrypoints", {})
        gate.check(set(entrypoints) == {"main"},
                   f"{name}: entrypoints are {sorted(entrypoints)}, only 'main' is allowed")
        gate.check(entrypoints.get("main") == ["com.tradewindow.TradeWindow"],
                   f"{name}: main entrypoint is not exactly com.tradewindow.TradeWindow")
        depends = meta.get("depends", {})
        gate.check(depends.get("fabricloader") == ">=0.19.3",
                   f"{name}: loader bound is {depends.get('fabricloader')!r}, not '>=0.19.3'")
        gate.check(depends.get("minecraft") == mc,
                   f"{name}: minecraft bound is {depends.get('minecraft')!r}, not {mc!r}")
        gate.check(depends.get("java") == f">={java}",
                   f"{name}: java bound is {depends.get('java')!r}, not '>={java}'")
        gate.check(meta.get("version") == MOD_VERSION,
                   f"{name}: version is {meta.get('version')!r}, expected {MOD_VERSION!r}")

        # -- nothing that could be a custom item, packet or client class ----------
        for package, what in FORBIDDEN_PACKAGES.items():
            prefix = f"com/tradewindow/{package}/"
            gate.check(not [n for n in names if n.startswith(prefix)],
                       f"{name} ships {what} ({prefix})")

        # -- the classes that must be there --------------------------------------
        for required in ("com/tradewindow/TradeWindow.class",
                         "com/tradewindow/menu/TradeMenu.class",
                         "com/tradewindow/menu/TradeInventory.class",
                         "com/tradewindow/trade/TradeManager.class",
                         "com/tradewindow/trade/TradeToken.class",
                         "com/tradewindow/util/TradeText.class",
                         "com/tradewindow/command/TradeAdminCommand.class"):
            gate.check(required in names, f"{name} is missing {required}")

        # -- the language file, at the exact path TradeText reads ------------------
        lang_path = "assets/tradewindow/lang/en_us.json"
        if gate.check(lang_path in names,
                      f"{name} has no {lang_path}; every message would show its key"):
            lang = json.loads(zf.read(lang_path))
            gate.check(lang.get("container.tradewindow.title") == "Trading Window",
                       f"{name}: window title is not exactly 'Trading Window'")
            gate.check(lang.get("tradewindow.chat.start") == "Trade started.",
                       f"{name}: chat wording has drifted from 'Trade started.'")
            doubled = [k for k, v in lang.items() if isinstance(v, str) and "  " in v]
            gate.check(not doubled, f"{name}: language entries contain a double space: {doubled}")

        # -- recipe and advancement, in the singular folders ----------------------
        recipe_path = "data/tradewindow/recipe/trade_token.json"
        adv_path = "data/tradewindow/advancement/recipes/misc/trade_token.json"
        gate.check(recipe_path in names, f"{name} is missing {recipe_path}")
        gate.check(adv_path in names, f"{name} is missing {adv_path}")
        gate.check(not [n for n in names if n.startswith("data/tradewindow/recipes/")],
                   f"{name} ships the retired plural recipes/ folder")

        if recipe_path in names:
            recipe = json.loads(zf.read(recipe_path))
            result = recipe["result"]
            gate.check(recipe["type"] == "minecraft:crafting_shapeless",
                       f"{name}: token recipe is not shapeless")
            gate.check(result["id"] == "minecraft:ghast_tear",
                       f"{name}: token recipe does not produce a Ghast Tear")
            gate.check(result.get("count", 1) == 1, f"{name}: token recipe yields more than one")
            components = result.get("components", {})
            gate.check(components.get("minecraft:max_stack_size") == 16,
                       f"{name}: token does not stack to 16")
            name_component = components.get("minecraft:custom_name")
            lore = components.get("minecraft:lore")
            # The encoding matters as much as the content, and each side of the
            # 1.21.1/1.21.2 line needs the opposite one. See
            # check_component_encoding() and PORTING_NOTES.md §14.
            check_component_encoding(gate, name, mc in STRING_FORM_TARGETS,
                                     name_component, lore)
            decoded_name = text_component(name_component)
            gate.check(decoded_name.get("text") == "Trade Token",
                       f"{name}: token name is {decoded_name.get('text')!r}")
            gate.check(decoded_name.get("color") == "red"
                       and decoded_name.get("italic") is False,
                       f"{name}: token name is not red and non-italic")
            gate.check(isinstance(lore, list) and lore,
                       f"{name}: token has no lore")
            ingredients = recipe["ingredients"]
            # 1.21.1 is the odd one out: an ingredient is {"item": "minecraft:x"}.
            if mc == "1.21.1":
                gate.check(all(isinstance(i, dict) and "item" in i for i in ingredients),
                           f"{name}: 1.21.1 needs object ingredients, got {ingredients}")
            else:
                gate.check(all(isinstance(i, str) for i in ingredients),
                           f"{name}: needs bare-string ingredients, got {ingredients}")
            gate.check(sorted(ingredients, key=str) == sorted(
                ["minecraft:ghast_tear", "minecraft:gold_ingot"], key=str)
                or sorted(str(i.get("item") if isinstance(i, dict) else i) for i in ingredients)
                == ["minecraft:ghast_tear", "minecraft:gold_ingot"],
                f"{name}: token recipe is not a tear plus a gold ingot")

        if adv_path in names:
            adv = json.loads(zf.read(adv_path))
            gate.check(adv.get("rewards", {}).get("recipes") == ["tradewindow:trade_token"],
                       f"{name}: advancement does not unlock the token recipe")
            gate.check("has_ghast_tear" in adv.get("criteria", {}),
                       f"{name}: advancement has no ghast-tear criterion")
            # The recipe_unlocked condition key: "recipe" everywhere up to 26.2,
            # "recipes" from 26.3. A wrong name here is fatal rather than cosmetic on
            # 26.3 - the advancement fails to parse and the server refuses to load its
            # data packs at all.
            expected_key = "recipes" if mc == "26.3" else "recipe"
            unlocked = adv.get("criteria", {}).get("has_the_recipe", {}).get(
                "conditions", {})
            gate.check(list(unlocked) == [expected_key],
                       f"{name}: recipe_unlocked condition is {sorted(unlocked)}, "
                       f"expected [{expected_key!r}]")


def main():
    gate = Gate()
    targets = read_matrix()
    # 1.21.1 – 1.21.11 (eleven) plus 26.1 – 26.3 (three). The count is asserted
    # rather than derived, because "all fourteen jars exist" is the deliverable.
    if not gate.check(len(targets) == 14, f"expected 14 targets, matrix has {len(targets)}"):
        return 1

    check_sources(gate, targets)
    for target in targets:
        check_jar(gate, target)

    print(f"targets: {', '.join(t['mc'] for t in targets)}")
    if gate.failures:
        print(f"\nFAILED: {len(gate.failures)} of {gate.checks} checks\n")
        for failure in gate.failures:
            print(f"  - {failure}")
        return 1
    print(f"\nOK: all {gate.checks} packaging checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
