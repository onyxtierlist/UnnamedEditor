# UnnamedEditor — Android Prototype 0.1

An independent Android-first hybrid video editor concept: a CapCut-inspired timeline UI combined with a Node Video-inspired node workspace.

## Included in this prototype
- Home screen with new project and recent-project entry points
- Timeline workspace with mock video/audio/text/effects tracks
- Node editor canvas with example processing nodes
- Effects browser with category filters
- Export settings UI

## Important status
This is the first **interactive UI prototype**, not a finished video editor. Media import, real timeline playback, GPU effects execution, node evaluation, project persistence, and video export are not implemented yet.

## Build locally
Open this folder in Android Studio, allow Gradle sync, then run the `app` configuration on an Android device or emulator.

## Build an APK using GitHub Actions
1. Push this project to a GitHub repository.
2. Open the repository's **Actions** tab.
3. Run **Build UnnamedEditor APK** (or push to `main`/`master`).
4. Download the `UnnamedEditor-debug-apk` artifact from the completed workflow.

The APK is a debug build for testing, not a Play Store release.

- Package: `com.unnamededitor.app`
- Min Android: Android 8.0 (API 26)
- Version: `0.1.0`
