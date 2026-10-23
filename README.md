# GameStudio Offline

An Android-first, Vietnamese, offline-first no-code game editor. This repository is being rebuilt as a real editor foundation rather than a sample game.

## Product target

- Create and edit projects directly on Android.
- Work offline for core editing and project saving.
- Edit 2D scenes and a basic isometric 3D primitive scene.
- Add and move objects, edit properties, and save projects locally.
- Create trigger/action event blocks using a visual no-code flow.
- Play-test a simple scene inside the editor.
- Export/import project archives offline.
- Later target: export an installable APK for each created game directly on Android.

## Current foundation

This milestone includes local project creation and save/load, a 2D canvas, a basic isometric 3D primitive viewport, object property editing, saved no-code event blocks, a simple play-test view, and ZIP project export.

## Not yet implemented

This is **not yet feature-equivalent to GDevelop**. Event execution is not a full game-logic runtime, sprite/audio asset import is incomplete, the 3D view is a primitive preview, and direct APK export of user-created games is not yet wired up. These must be implemented and tested before being marked complete.

## Build

Open in Android Studio with JDK 17, Android SDK 35, and Gradle 8.9, or download the editor APK from GitHub Actions.

## Design

Keep editing and local persistence offline. Store projects as versioned JSON. Evolve toward a packaged runtime template for on-device APK export without a cloud build.
