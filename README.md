<p align="center">
  <img src="docs/icon-512.png" alt="ItemSize icon" width="128">
</p>

<h1 align="center">ItemSize</h1>

<p align="center">
  Make items bigger or smaller in your hand, on the ground and in your inventory.<br>
  Per item, with a live preview. Client-side Fabric mod for Minecraft 1.21.11.
</p>

<p align="center">
  <a href="https://github.com/Neoloni/ItemSize/actions/workflows/build.yml"><img alt="Build" src="https://github.com/Neoloni/ItemSize/actions/workflows/build.yml/badge.svg"></a>
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-1.21.11-5CF2A0">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Fabric-A970FF">
  <img alt="Environment" src="https://img.shields.io/badge/Environment-Client-3DE8FF">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-FF5CA8">
</p>

## Features

- **Three views per item:** Ground (dropped items), Hand (first and third person) and Inventory (item icons).
- **Live preview:** see the new size right away while you change it.
- **Easy to set:** slider, **-** / **+** buttons, mouse wheel, arrow keys, presets (50 / 100 / 150 / 200 %) or type a percentage.
- **All views switch:** change ground, hand and inventory size together, or set each view on its own.
- **Search and filters:** find items by name or ID, filter by Block, Tool, Armor or items you edited.
- **Global ON / OFF** switch and reset per item or for everything.
- **Automatic saving** to `config/itemsize.json`. Items from other mods show up too.
- Sizes from **10 % to 300 %**.
- Available in English and German.

## Usage

1. Press **O** in a world to open ItemSize (the key can be changed under *Options > Controls > Key Binds > ItemSize*).
2. Pick an item in the list. Just start typing to search.
3. Choose a view (Ground, Hand, Inventory) and set the size. Everything is saved automatically.

## Requirements

- Minecraft Java Edition **1.21.11**
- [Fabric Loader](https://fabricmc.net/use/) 0.18.1 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 21

## Installation

1. Install Fabric Loader and Fabric API for Minecraft 1.21.11.
2. Download `itemsize-<version>.jar` and put it into your `mods` folder.
3. Start the game.

## Notes

- **Client-side only.** Sizes are never sent to a server and other players do not see them. It works on servers that allow client-side mods.
- Weapon and ammunition items (swords, bows, tridents, arrows, ...) are intentionally left out.
- Items with unusual custom renderers may need extra compatibility work. If an item looks wrong, please open an issue.

## Building

You need JDK 21 and Gradle 9.8.2 or newer.

```bash
gradle build
```

The mod JAR is created in `build/libs/`. GitHub Actions builds every push to `main` and uploads the JAR as an artifact. Pushing a tag like `v1.0.0` creates a GitHub release with the JAR.

## Contributing

Bug reports and ideas are welcome in the [issue tracker](https://github.com/Neoloni/ItemSize/issues). Please include your Minecraft version, other installed mods and the log or crash report.

## License

ItemSize is released under the [MIT License](LICENSE).
