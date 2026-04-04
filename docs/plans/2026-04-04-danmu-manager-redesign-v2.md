# Danmu Manager Redesign V2 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rebuild the redesigned manager UI into a simpler V2 information architecture that is easier to understand on phone-sized screens while preserving the full feature set.

**Architecture:** Keep the existing single `ManagerViewModel` and data layer intact, but replace the current “command center” page composition with a clearer four-section app shell. Reorganize existing features into simpler page-specific sections, introduce a smaller set of reusable layout primitives, and demote advanced capabilities behind secondary navigation or collapsed areas.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, existing `ManagerViewModel`, existing root/network repositories

---

### Task 1: Replace App Shell Information Hierarchy

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/app/DanmuManagerApp.kt`
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/app/navigation/AppDestination.kt`
- Modify: `manager_app_redesign/manager_app/app/src/main/res/values/strings.xml`

**Steps:**
1. Rename top-level destinations to `主页 / 核心 / 监控 / 设置`.
2. Remove the current heavy top app bar behavior and replace it with a lighter page-title layout.
3. Keep bottom navigation on compact width and rail on expanded width.
4. Verify navigation still routes the four primary screens correctly.

### Task 2: Introduce Simpler Shared Surface Components

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/designsystem/component/InfoComponents.kt`
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/designsystem/component/HeroCard.kt`
- Create or modify minimal helper composables under `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/designsystem/component/`

**Steps:**
1. Reduce oversized hero treatment and convert it into restrained summary surfaces.
2. Add smaller summary cards / status row / list item surfaces needed by V2 pages.
3. Standardize section header spacing and button density.
4. Remove components that force “dashboard overload” styling from the new layout path.

### Task 3: Redesign Overview into a Focused Home Screen

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/overview/OverviewScreen.kt`

**Steps:**
1. Replace the current large command-center hero with a compact system summary.
2. Keep only one primary control cluster for start/stop/restart/refresh.
3. Reduce summary metrics to the most important 3-4 items.
4. Move access URLs and token into a collapsed section.
5. Replace long reminder text blocks with actionable alert rows.

### Task 4: Redesign CoreHub into Resource Management

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/CoreHubScreen.kt`

**Steps:**
1. Make the installed core list the default first view.
2. Move installation inputs into a secondary panel, dialog, or bottom sheet.
3. Simplify each core item to a concise row/card with clear status.
4. Centralize switch/update/delete actions in a detail area instead of full-row action sprawl.
5. Preserve filtering and search while reducing visual noise.

### Task 5: Redesign Console into Monitoring

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/console/ConsoleScreen.kt`

**Steps:**
1. Reframe the screen as `监控`.
2. Change top tab emphasis so the default view is an event/summary-oriented panel.
3. Simplify logs to file selection + content, requests to list rows, API debug to advanced section.
4. Shorten explanatory copy throughout the screen.
5. Keep all existing data sources and actions wired through `ManagerViewModel`.

### Task 6: Redesign Settings into Category-Driven Management

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/SettingsScreen.kt`

**Steps:**
1. Split the current long settings form into category sections or list-detail behavior.
2. Group token/update, theme, maintenance, WebDAV, and env tools more clearly.
3. Move `.env` editing into a dedicated full-height editor experience.
4. Consolidate dangerous actions into a single warning section at the bottom.
5. Reduce simultaneous visible fields on phone screens.

### Task 7: Refresh Theme Tokens for the New Visual Direction

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/designsystem/theme/Color.kt`
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/designsystem/theme/Theme.kt`
- Modify as needed: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/designsystem/theme/Type.kt`

**Steps:**
1. Shift backgrounds to a warmer neutral surface.
2. Keep a restrained cool primary accent.
3. Ensure warning/error/success states read clearly.
4. Keep dynamic color optional without overriding the visual hierarchy.

### Task 8: Verify Build and Packaging Integrity

**Files:**
- Verify: `manager_app_redesign/manager_app/...`
- Verify: `.github/workflows/build-danmu-magisk.yml`

**Steps:**
1. Run `./gradlew :app:compileDebugKotlin` in `manager_app_redesign/manager_app`.
2. Run `./gradlew :app:testDebugUnitTest`.
3. Run `./gradlew :app:assembleDebug`.
4. Confirm the release integration path still points to the redesigned manager project.

### Task 9: Commit and Push

**Files:**
- Commit all design, plan, and UI changes

**Steps:**
1. Review changed files.
2. Commit with a concise Chinese message.
3. Push with `/data/data/com.termux/files/home/.github_accounts/gh_git_push.sh 1 origin main`.
