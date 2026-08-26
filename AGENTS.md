# AGENTS.md — BrickBreaker

Brick-breaker game for Android: 100 levels, powerups, a coin-shop every 10 levels, 6 themes.

## Stack

- Kotlin, native Android `View` (custom `GameView`) — **no Compose, no WebView, no fragments**.
- Gradle Kotlin DSL. Only dependency is `androidx.core:core-ktx`. Package `com.bee.brickbreaker`, minSdk 26 (Android 8.0), target/compileSdk 36.
- Portrait only, fullscreen, screen-on flag. Permission: `VIBRATE`.

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

| File | Purpose |
|---|---|
| `app/src/main/java/com/bee/brickbreaker/MainActivity.kt` | Thin wrapper — sets fullscreen flags, instantiates `GameView` |
| `app/src/main/java/com/bee/brickbreaker/GameView.kt` | Everything: game engine, renderer, audio, input |

Key invariants inside `GameView.kt`:

- **Logical coordinates** `LW=390f × LH=700f`, scaled to the real screen each frame via `canvas.scale(scaleX, scaleY)`; touch events are divided by the same scale.
- **Screen state machine:** `Screen` enum (`MENU, GAME, PAUSE, GAMEOVER, WIN, SCORES, SETTINGS, SHOP, UPGRADE`); `render()` dispatches to per-screen draw functions. `UPGRADE` is a free roguelite perk pick after every non-shop level.
- **Game loop:** `GameThread` inner class locks canvas at ~60fps, calls `update(dt)` then `render(canvas)`.
- **Audio:** `SoundEngine` generates PCM via `AudioTrack` in a background thread (sine = wall, square = brick, sawtooth = paddle, frequency sweep = level up, multi-note chord = win) — no audio assets.
- **Data models:** `Ball`, `Brick`, `Paddle`, `Particle`, `Powerup`, `Laser`, `Fx` (powerup durations), `GameState`, `DiffCfg`.
- **Levels:** 100 `LevelDef` entries in the `LEVELS` list. Each has a special-brick spawn chance `sc` and a grid of HP values.
- **Themes:** 6 `Theme` entries in the `THEMES` map — keys: `neon`, `retro`, `ice`, `fire`, `mono`, `forest`.
- **Powerups:** `wide`, `multi`, `laser`, `slow`, `fireball`, `shield` — durations tracked in `Fx`. Dropped from special bricks.
- **Persistence:** `SharedPreferences` key `"bb"` (theme, difficulty, toggles — haptic/particles/trails/sfx — volume, high score list) plus `"coins"` for shop currency.

## Do-not-break rules (critical)

- **HUD must be drawn AFTER `canvas.restore()` ends the screen-shake block.** Drawing HUD inside `canvas.save/translate(shakeOx, shakeOy)` shifts visual positions but not `btnRects` hit-test coordinates — taps miss during/after brick hits.
- **Pause button stays top-right** (`cx=LW-20f`, w=38, h=32); preserve the ~38-unit gap after the lives text (`LW-58f`). Users have muscle memory for this position.
- **`pText.textAlign` must be set explicitly before every text draw.** `textBtn()` sets `CENTER` internally; `drawMenu()` sets it at the top; `drawSettings()` sets `LEFT` — later callers must reset.
- **Cutout/insets:** `onApplyWindowInsets` uses a max-only update for `topInsetPx` to avoid jitter during the bar-hide animation — don't "fix" it to take the latest value.
- **Aim system:** pre-launch, dragging moves `st.aimX` only (paddle fixed); the launch angle derives from `aimX`. Don't make pre-launch drag move the paddle.
- **Shop:** `Screen.SHOP` after every 10th level, blocks advancing. Unaffordable upgrades are never added to `btnRects` (so they're untappable) — keep it that way. `startNextLevel()` carries score, lives, ballSpeed, and active `fx` into the next `GameState`.
- **Fullscreen:** `WindowInsetsController.hide(...)` (API 30+) is the primary path; deprecated `SYSTEM_UI_FLAG_*` flags are kept deliberately as API < 30 fallback — don't remove them.

## HUD layout (logical coords, left→right)

- `x=8f` left — score `★ N`
- `LW/2f` centre — level `LVL N/100`
- `LW-58f` right-aligned — lives `♥♥♥`
- `cx=LW-20f` — pause button `❚❚` (w=38, h=32)

The ~38 logical unit gap between lives text end (~`LW-58f`) and pause button start (~`LW-39f`) must be preserved. Do not move the pause button to the left side — users have muscle memory for top-right.

## Fullscreen / display cutout (Android 15 / Pixel 9 Pro XL)

`MainActivity` uses `WindowInsetsController.hide(statusBars | navigationBars)` (API 30+) with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. Deprecated `SYSTEM_UI_FLAG_*` flags are kept as a fallback for API < 30 only. `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` is set so the game renders behind the punch-hole camera area.

`GameView.onApplyWindowInsets` captures the top inset (status bar + cutout) and stores it in `topInsetPx`. This is applied as a logical-unit offset (`topInsetPx / scaleY`) to HUD element Y positions. The override uses a max-only update pattern to avoid jitter during the bar-hide animation, which fires the callback multiple times with decreasing values.

## Shop system

`Screen.SHOP` appears after completing every 10th level (levels 10, 20, 30…). It blocks advancing until the player picks an upgrade or presses "Continue".

- **Coins** — persistent in prefs key `"coins"`. Earned 1 per brick destroyed (`killBrick`). Accumulated across games.
- **Upgrades** (all one-shot per shop visit): +1 Life (20🪙), Wide Paddle 15 s (15🪙), Shield 10 s (12🪙), Slow Ball −18% speed (10🪙), Continue (free).
- Unaffordable upgrade buttons are never added to `btnRects`, so they are not tappable.
- `startNextLevel()` creates the next `GameState` from `shopNextLi` (0-indexed), carrying over score, lives, ballSpeed, and `fx` (active powerup durations).

## Upgrade system (roguelite perks)

`Screen.UPGRADE` appears after every non-shop level (i.e. whenever `nextLi % 10 != 0`). Three choices are drawn at random from `UPGRADE_POOL` and the player picks one — it's free, and picking advances via `startNextLevel()`. There is no "skip" button; a pick is mandatory.

- Perks (`applyUpgrade`): `up_life` (+1 life), `up_wide` (paddle +16, cap +70), `up_multiball` (+1 starting ball per level, cap 4), `up_slow` (ballSpeed × 0.88), `up_fire` (start each level with ≥6 s fireball), `up_coins` (+25 coins).
- Run-scoped state lives in `runPaddleBonus` / `runExtraBalls` / `runStartFire` — applied inside `startNextLevel()`, and reset by `resetRunUpgrades()` on `startGame()` / `continueGame()`. They persist across levels within a run but not across games (and are not saved to prefs).
- Don't confuse these with the coin-shop upgrades above: shop = every 10th level, costs coins, one-shot effects; UPGRADE = every other level, free, run-scoped stacking effects.

## Aim system (pre-launch)

Before the ball is launched, dragging moves `st.aimX` only — the paddle stays fixed. The aim line uses `aimX` to compute the launch angle. On tap-to-launch, `aimX` is used to set `ball.vx/vy`. Once launched, dragging moves the paddle as normal.

## Navigation / Settings

`prevScreen` tracks the screen before entering Settings, so "← BACK" returns to PAUSE or MENU correctly. `saveProgress()` saves when `screen` is SETTINGS (as well as GAME/PAUSE), preventing data loss when the app is backgrounded from Settings.

## Backgrounding (pause-on-background)

`MainActivity.onPause` → `GameView.pause()` freezes the sim and, if a game is live, switches `screen` to `PAUSE` — so returning to the app lands on the pause menu instead of the ball resuming mid-flight. `pause()` also calls `savePrefs()`, which banks coins earned mid-run (previously coins were only written on shop purchases / settings changes / game-over, so backgrounding lost them).

## Multi-HP brick visuals

Bricks with `maxHp > 1` show:
- A thicker armour border (alpha and strokeWidth scale with maxHp)
- An HP pip bar at the bottom: filled white pips = remaining HP, dark pips = lost HP

The old diagonal crack lines are gone.
