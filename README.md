# ItemSize

**ItemSize — Item Dimension Editor** is a client-side Fabric mod for Minecraft Java Edition **1.21.11**.

It adds a custom animated editor for decorative, building and utility items. Open it with **O** (the key can be changed in Options → Controls → Key Binds → ItemSize).

## Features

- Browse registered items and search by their translated name or registry ID.
- Set three independent display scales per item:
  - **Ground** — dropped items in the world.
  - **Hand** — first- and third-person held display.
  - **Inventory** — item icons in inventory-style GUIs.
- Smooth opening animation, ambient particles, animated panel accents, hover states and Minecraft UI sound feedback.
- Automatic local config saving to `config/itemsize.json`.
- Uses registry IDs, so registered items from other mods can appear in the browser.
- Scale range: **10%–300%**.

ItemSize intentionally excludes weapon- and ammunition-type item IDs from its editor and rendering changes.

## Requirements

- Minecraft Java Edition **1.21.11**
- Java **21**
- Fabric Loader **0.18.1 or newer**
- Fabric API **0.141.6+1.21.11**

## Build

Install JDK 21 and Gradle 9.2.1 or newer, then run from this directory:

```bash
gradle build
```

The remapped mod JAR will be created in `build/libs/`.

A GitHub Actions workflow is included at `.github/workflows/build.yml`. Once this project is pushed to a GitHub repository, it builds the mod and uploads the JAR as a workflow artifact.

## Install

1. Build the project, or download the JAR from the successful **Build ItemSize** GitHub Actions run.
2. Put `itemsize-1.0.0.jar` into your Minecraft `mods` folder.
3. Start Minecraft 1.21.11 with Fabric Loader and Fabric API installed.
4. Press **O** in a world to open ItemSize.

## Multiplayer note

This is a client-side visual mod. Your local display sizes are not sent to a server and are not synchronized to other players. Other players only see the changes if they independently install and configure the mod.

## Compatibility note

Items that use unusual custom renderers may need additional compatibility work. The mod uses Minecraft's item render-state pipeline and applies its scale to the standard ground, hand and GUI display contexts.

## Development status

This source package targets the 1.21.11 mappings and toolchain. It has not been compiled in this environment because Gradle and the required dependency downloads are unavailable here. The included GitHub Actions workflow performs the actual Gradle build after the repository is uploaded.
