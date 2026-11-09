# ItemSize

**An item scale editor for Minecraft Java Edition.**

ItemSize is a client-side Fabric mod that lets you configure three independent render scales for each registered item:

- **Ground** — dropped item rendering
- **Hand** — first- and third-person hand rendering
- **Inventory** — GUI item rendering

The complete Gradle source project is provided in `ItemSize-project.zip.b64` (Base64-encoded ZIP). The GitHub Actions workflow decodes it, builds the mod and publishes the compiled JAR as a downloadable workflow artifact.

## Build

Every push to `main` runs the build workflow. Open **Actions → ItemSize build → latest run → Artifacts** to download the JAR once the build succeeds.

## Project details

- Minecraft Java target: 26.3
- Fabric
- Java 25
- Default menu key: **I**
- Per-item configuration in `.minecraft/config/itemsize.json`

## Important

The build artifact is produced by GitHub Actions. A successful workflow is required before the JAR should be considered built.