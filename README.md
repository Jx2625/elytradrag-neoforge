# Elytra Drag (NeoForge 1.21.1)

Hold **sneak** while flying with an elytra to apply air drag and slow down.

A NeoForge 1.21.1 port of [bl4sterino's Elytra Drag](https://github.com/bl4sterino/Elytra-Drag), extended with configurable Do a Barrel Roll integration, server-side fall protection and kinetic damage immunity.

## Features

- Hold sneak while gliding to apply drag (configurable strength and minimum speed).
- Caps the fall distance taken while braking (server side).
- Cancels `FLY_INTO_WALL` kinetic damage while braking (server side, configurable).
- Cancels firework rockets used while braking.
- Do a Barrel Roll integration: by default braking only applies drag and leaves DaBR untouched; a config switch restores the older behaviour that suppresses DaBR and levels the view.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.235 or newer |
| Java | 21 |
| Do a Barrel Roll (NeoForge) | **3.8.2 or newer, required on the client** |

Do a Barrel Roll is a hard dependency: the mod hooks DaBR's roll group, so the game refuses to start without it.

### Client / server

- **Client is required.** Drag, DaBR handling and firework suppression are client-side. Installing the mod only on the server does nothing, because the server never receives a braking state.
- **Server is optional.** With the mod on the server it additionally applies the fall-distance cap and the `FLY_INTO_WALL` immunity while a player is braking. Without it those two protections are simply absent.

## Configuration

`config/elytradrag-common.toml`

| Option | Default | Description |
|---|---|---|
| `dragMultiplier` | 2.0 | Drag strength; the per-tick factor is `1 - 0.05 * multiplier` |
| `minimumSpeed` | 2.0 | Blocks per second below which drag stops applying |
| `maxFallDistance` | 15.0 | Fall distance cap while braking (server side) |
| `removeKineticDamage` | true | Cancel `FLY_INTO_WALL` damage while braking (server side) |
| `keepDaBREnabledWhileBraking` | true | Only slow down and leave DaBR untouched; set to `false` for the older behaviour that suppresses DaBR and levels the view |

## Building

The Do a Barrel Roll jar is `compileOnly` and is not vendored here (it is GPL-3.0). Put it in `libs/` first:

```
libs/DoAnotherBarrelRoll-3.8.2+1.21.1.jar
```

Then run:

```powershell
./gradlew build
```

The result is `build/libs/elytradrag-1.0.0.jar`. Requires JDK 21; use the bundled Gradle wrapper.

## 中文简要

滑翔时按住潜行键施加空气阻力减速；服务端装本模组时会额外提供刹车期间的摔落距离上限与撞墙动能免疫。客户端必须安装，并且必须装 Do a Barrel Roll 3.8.2+。构建前请把 DaBR 的 jar 放进 `libs/`。

## License

MIT
