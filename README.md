# GAMA Core

A bare-bones Vulkan / OpenGL renderer switcher for Android, for low-end devices and minimalists. No animations, blur, themes or extra settings. For the full-featured app, use [GAMA](https://github.com/palincat/gama).

## What it does

- Switches the HWUI renderer between Vulkan and OpenGL and checks that the change really applied.
- Works with root or [Shizuku](https://shizuku.rikka.app/).
- Re-applies your Vulkan choice after a reboot.
- Quick Settings tile, home-screen widget and Tasker support.
- Settings backup / restore and a copyable diagnostics report.

## Setup

1. Start Shizuku (or use a rooted device) and install GAMA Core.
2. Open it and allow the Shizuku permission prompt. For root, tap **Refresh** and approve the prompt.
3. Tap **Use Vulkan** or **Use OpenGL**.

If a device freezes under Vulkan, restart it (Volume Down + Power). Android clears the setting on reboot.

GAMA Core uses its own application ID, `com.popovicialinc.gama.core`, so it installs next to the full GAMA.

## Tasker

Copy the token from **Integrations**, then send a broadcast with action `com.popovicialinc.gama.core.ACTION_SET_RENDERER`, package `com.popovicialinc.gama.core`, class `com.popovicialinc.gama.TaskerReceiver`, and extras `renderer:vulkan|opengl` and `token:<token>`.

## Building

```
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Release builds need signing credentials: see `keystore.properties.example`.
