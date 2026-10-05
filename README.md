# Candle Codex

Candle Codex is a new direction for the Candle client, inspired by the useful parts of Candle Client but designed as its own UI system.

## Direction

- Aurora Grid visual language: dark panels, restrained accent colours, strong spacing.
- Overview dashboard instead of a crowded lobby.
- Cosmetics Lab designed for future real 3D previews.
- HUD Composer for compact, configurable modules.
- Performance page with frame-time visualization.
- Settings page prepared for adaptive animation and interface controls.
- Right Shift opens the client menu.
- Minecraft 1.21.11 / Fabric / Java 21.

## Build

Install Java 21 and a Gradle 8.x installation, then:

    gradle build

The jar is produced in:

    build/libs/

## Next implementation layer

1. Persist settings and themes.
2. Replace cosmetic placeholders with the existing Candle geometry system, redesigned for Codex.
3. Add a real FPS sampler and frame-time history.
4. Add draggable HUD modules.
5. Add the Codex loading screen and client lobby.
6. Add lightweight Modrinth/store integration without blocking the render thread.
7. Add adaptive animation quality so cosmetic previews never become a source of stutter.
