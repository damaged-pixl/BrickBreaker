# AGENTS.md — BrickBreaker

Brick-breaker game for Android: 100 levels, powerups, a coin-shop every 10 levels, 6 themes.

## Stack

- Kotlin, native Android `View` (custom `GameView`) — **no Compose, no WebView, no fragments**.
- Gradle Kotlin DSL. Only dependency is `androidx.core:core-ktx`. Package `com.bee.brickbreaker`, minSdk 26, target/compileSdk 36.

## Build / install / test

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- Requires `local.properties` with `sdk.dir=/home/bee/Android/Sdk` (already present); `ANDROID_HOME` also works.
- **No test suite** — verify by building and playing on a device/emulator.

## Architecture (read before editing)

Two-file engine, single activity:

- `app/src/main/java/com/bee/brickbreaker/MainActivity.kt` — thin wrapper: fullscreen flags, instantiates `GameView`.
- `app/src/main/java/com/bee/brickbreaker/GameView.kt` — everything: engine, renderer, audio, input.

Key invariants inside `GameView.kt`:

- **Logical coordinates** `LW=390f × LH=700f`, scaled to the real screen each frame via `canvas.scale(scaleX, scaleY)`; touch events are divided by the same scale.
- **Screen state machine:** `Screen` enum (`MENU, GAME, PAUSE, GAMEOVER, WIN, SCORES, SETTINGS, SHOP`); `render()` dispatches to per-screen draw functions.
- **Audio:** `SoundEngine` generates PCM via `AudioTrack` (sine/square/saw/sweep/chord) — no audio assets.
- **Persistence:** `SharedPreferences` key `"bb"` (theme, difficulty, toggles, volume, high scores) plus `"coins"` for shop currency.

## Do-not-break rules (critical)

- **HUD must be drawn AFTER `canvas.restore()` ends the screen-shake block.** Drawing HUD inside `canvas.save/translate(shakeOx, shakeOy)` shifts visual positions but not `btnRects` hit-test coordinates — taps miss during/after brick hits.
- **Pause button stays top-right** (`cx=LW-20f`, w=38, h=32); preserve the ~38-unit gap after the lives text (`LW-58f`). Users have muscle memory for this position.
- **`pText.textAlign` must be set explicitly before every text draw.** `textBtn()` sets `CENTER` internally; `drawSettings()` sets `LEFT` — later callers must reset.
- **Cutout/insets:** `onApplyWindowInsets` uses a max-only update for `topInsetPx` to avoid jitter during the bar-hide animation — don't "fix" it to take the latest value.
- **Aim system:** pre-launch, dragging moves `st.aimX` only (paddle fixed); the launch angle derives from `aimX`. Don't make pre-launch drag move the paddle.
- **Shop:** `Screen.SHOP` after every 10th level, blocks advancing. Unaffordable upgrades are never added to `btnRects` (so they're untappable) — keep it that way. `startNextLevel()` carries score, lives, ballSpeed, and active `fx` into the next `GameState`.
- **Fullscreen:** `WindowInsetsController.hide(...)` (API 30+) is the primary path; deprecated `SYSTEM_UI_FLAG_*` flags are kept deliberately as API < 30 fallback — don't remove them.

`CLAUDE.md` in this directory is the authoritative design doc — consult it for HUD layout coordinates, level/theme tables, and the multi-HP brick visual rules.
