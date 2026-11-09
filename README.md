# ItemSize

**The ultimate item size editor for Minecraft Java Edition.**

A client-side Fabric mod with three independent render scales per registered item:

- World / Ground — dropped-item view.
- Hand — first- and third-person hand views.
- Inventory — GUI view.

Items are searched by translated name or registry ID. Each item's values are stored separately in `.minecraft/config/itemsize.json`, so modded items can have their own settings too.

## Target versions

- Minecraft Java 26.3
- Fabric Loader 0.20.1
- Fabric API 0.165.0+26.3
- Java 25
- Gradle 9.6

## Controls

Press **I** to open the editor. Search for any item, select it, then use the three sliders (10%–500%). Settings are saved when you press **SAVE** or close the screen.

## Build

The GitHub Actions workflow builds the project on every push to `main`. Download the `ItemSize JAR` artifact from the successful run under **Actions**.

For a local build, install JDK 25 and Gradle 9.6, then run:

```bash
gradle build
```

The built mod is at `build/libs/itemsize-1.0.0.jar`.

Install the JAR in your Fabric 26.3 `mods` folder together with Fabric API 0.165.0+26.3.

## Notes

The mod is client-side: it changes rendering on the local client, not server-side item physics, hitboxes, or other players' views.
