# AI Travis Wilson Client (Phase 3 - all requested modules)

Fabric client mod for Minecraft Java 1.21.11 (Java 21, Yarn mappings, Loom 1.14).

## Build via GitHub Actions
1. Upload the contents of this folder to the root of your GitHub repository.
2. Open the **Actions** tab; the "Build AI Travis Wilson Client" workflow runs on push.
3. Download the `ai-travis-wilson-client` artifact, unzip it and put the main jar
   (not `-sources`) in `%APPDATA%\.minecraft\mods` with Fabric Loader and Fabric API installed.

Gradle wrapper jar is not included (it is a binary that could not be generated here). CI installs
Gradle 9.3.0 directly. To use `gradlew` locally run `gradle wrapper --gradle-version 9.3.0` once.

## Modules
Combat: Fast Crystal, Auto Anchor, Auto Safe Anchor, Auto Totem, Auto Double Hand, Auto Shield Breaker (axe swap),
Spear Lunge (spear swap), TriggerBot, Auto Jump Reset, Auto Health Pot (+ inventory-open refill).
Render: NameTags, Tracers, Player Outline (RGB 3D box / 2D box / corners), World Visuals (RGB block, crystal and anchor outlines + screen glow), HUD. Player: Hover Totem. Every module has working settings shown in the ClickGUI.
Activation keys are KeySettings (click, press a key, Backspace clears). Friends: add names to
`config/aitraviswilson/friends.txt` (one per line); teammates are also ignored.

Auto Anchor fills with glowstone and can detonate (not in the Nether, where anchors only set spawn).
GUI: animated open/page/hover/scroll, click sounds, both toggleable on the Appearance page.

Known limits: crystal damage is an estimate (no toughness/enchant/effect/totem handling); NameTags and Tracers are
drawn in screen space (first-person projection, constant size, no depth test); Spear Lunge only selects the spear
and times the attack, it does not alter movement; Mouse Tweaks-style drag-clicking is not replicated.
None of the modules is "undetectable"; use only where the server allows such tools.

## Phase 2 additions
Native ClickGUI (open with Right Shift, rebindable on the Keybinds page): sidebar with Combat/Render/Player/World/Misc and
Appearance/Configs/Keybinds, search, animated toggles, sliders, dropdowns, keybind selectors, tooltips, scrolling,
Up/Down/Tab page navigation, branding with your profile picture. Appearance editor: HSV picker, RGB sliders, HEX input,
opacity, corner radius, gradient/rainbow/blur, presets, saved/loaded/deleted themes, reset; theme auto-restored on start.
The layout follows the written spec because no reference screenshot has been supplied yet.

## Phase 1 contents
Build system, CI, module/setting framework, JSON config persistence (corrupt files quarantined),
keybind toggling, **Auto Totem**, **HUD** (active modules, FPS, ping, coordinates, totem/potion count).

Config file: `.minecraft/config/aitraviswilson/config.json`.


## Verification status
Nothing here has been compiled or run against Minecraft. Version numbers come from Fabric's
published 1.21.11 information; the first CI run may surface API mismatches to fix.
