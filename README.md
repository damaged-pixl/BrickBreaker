# BrickBreaker

Classic brick-breaker for Android. 100 levels on a custom `GameView` engine —
two-file core (`MainActivity.kt` + `GameView.kt`), no game-framework dependency.

## Build

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires the Android SDK (set `sdk.dir` in `local.properties` or `ANDROID_HOME`).

## Layout

- `app/src/main/java/com/bee/brickbreaker/GameView.kt` — game engine: paddle,
  ball physics, level definitions, HUD
- `app/src/main/java/com/bee/brickbreaker/MainActivity.kt` — activity host
