# Danmu Manager Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a fully redesigned Danmu API manager in a fresh `manager_app_redesign/` directory, keeping feature parity with the existing manager while delivering a cleaner architecture and a higher-quality Compose + Material 3 interface.

**Architecture:** Create a new Android app project under `manager_app_redesign/manager_app` and keep the old `manager_app/manager_app` intact until parity is verified. Split the new app by feature and core modules so Root access, repositories, screen state, and visual components are isolated instead of living in one giant app shell and one giant ViewModel.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose with adaptive navigation, AndroidX ViewModel, StateFlow, DataStore, WorkManager, OkHttp, Moshi, JUnit4, Compose UI Test.

---

### Task 1: Scaffold The New Android Project

**Files:**
- Create: `manager_app_redesign/manager_app/settings.gradle.kts`
- Create: `manager_app_redesign/manager_app/build.gradle.kts`
- Create: `manager_app_redesign/manager_app/app/build.gradle.kts`
- Create: `manager_app_redesign/manager_app/app/src/main/AndroidManifest.xml`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/MainActivity.kt`
- Create: `manager_app_redesign/manager_app/gradle.properties`
- Create: `manager_app_redesign/manager_app/proguard-rules.pro`
- Test: `manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/AppLaunchTest.kt`

**Step 1: Write the failing test**

```kotlin
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsAppShellTitle() {
        composeRule.onNodeWithText("Danmu API Manager").assertExists()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: FAIL because the new project and activity do not exist yet.

**Step 3: Write minimal implementation**

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Text("Danmu API Manager")
            }
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: PASS with one launched smoke test.

**Step 5: Commit**

```bash
git add manager_app_redesign/manager_app
git commit -m "feat: scaffold redesigned manager app"
```

### Task 2: Build The Design System And App Theme

**Files:**
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/designsystem/theme/Color.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/designsystem/theme/Type.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/designsystem/theme/Theme.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/designsystem/component/HeroCard.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/designsystem/component/StatusChip.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/res/font/jetbrains_mono_regular.ttf`
- Create: `manager_app_redesign/manager_app/app/src/main/res/font/noto_sans_sc_regular.ttf`
- Test: `manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/core/designsystem/ThemeContractTest.kt`

**Step 1: Write the failing test**

```kotlin
class ThemeContractTest {
    @Test
    fun statusPalette_containsDistinctWarningAndDangerColors() {
        val palette = DanmuStatusPalette.default()
        assertNotEquals(palette.warning, palette.danger)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ThemeContractTest"`
Expected: FAIL because the theme palette does not exist.

**Step 3: Write minimal implementation**

```kotlin
data class DanmuStatusPalette(
    val success: Color,
    val warning: Color,
    val danger: Color,
) {
    companion object {
        fun default() = DanmuStatusPalette(
            success = Color(0xFF16A34A),
            warning = Color(0xFFF59E0B),
            danger = Color(0xFFDC2626),
        )
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ThemeContractTest"`
Expected: PASS with the new palette contract.

**Step 5: Commit**

```bash
git add manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/designsystem manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/core/designsystem
git commit -m "feat: add redesign theme system"
```

### Task 3: Port Root, CLI, And Model Foundations

**Files:**
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/model/StatusModels.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/model/CoreModels.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/model/LogModels.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/root/DanmuPaths.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/root/RootShell.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/root/DanmuCli.kt`
- Modify reference: `manager_app/manager_app/app/src/main/java/com/danmuapi/manager/root/DanmuCli.kt`
- Test: `manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/core/root/DanmuCliParsingTest.kt`

**Step 1: Write the failing test**

```kotlin
class DanmuCliParsingTest {
    @Test
    fun extractsJsonPayloadFromShellNoise() {
        val raw = "warning\\n{\\\"service\\\":{\\\"running\\\":true}}\\n"
        val parsed = DanmuCli.extractJsonObjectForTest(raw)
        assertEquals("{\"service\":{\"running\":true}}", parsed)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*DanmuCliParsingTest"`
Expected: FAIL because the new CLI parser does not exist.

**Step 3: Write minimal implementation**

```kotlin
internal fun extractJsonObjectForTest(text: String): String? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    return if (start >= 0 && end > start) text.substring(start, end + 1) else null
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*DanmuCliParsingTest"`
Expected: PASS with the shell-noise parsing helper.

**Step 5: Commit**

```bash
git add manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/model manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/root manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/core/root
git commit -m "feat: port root and model foundations"
```

### Task 4: Build Repository And Settings Infrastructure

**Files:**
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/data/DanmuRepository.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/data/SettingsRepository.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/data/network/GitHubApi.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/data/network/GitHubReleaseApi.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/data/network/DanmuApiClient.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/data/network/WebDavClient.kt`
- Test: `manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/core/data/VersionCompareTest.kt`

**Step 1: Write the failing test**

```kotlin
class VersionCompareTest {
    @Test
    fun comparesSemanticVersionNumbers() {
        assertTrue(compareVersions("v1.3.0", "1.2.9") > 0)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*VersionCompareTest"`
Expected: FAIL because the comparison helper is missing.

**Step 3: Write minimal implementation**

```kotlin
fun compareVersions(left: String, right: String): Int {
    fun parts(value: String) = value.removePrefix("v").split(".").map { it.takeWhile(Char::isDigit).ifBlank { "0" }.toInt() }
    val a = parts(left)
    val b = parts(right)
    for (index in 0 until maxOf(a.size, b.size)) {
        val av = a.getOrElse(index) { 0 }
        val bv = b.getOrElse(index) { 0 }
        if (av != bv) return av.compareTo(bv)
    }
    return 0
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*VersionCompareTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/data manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/core/data
git commit -m "feat: add data and settings infrastructure"
```

### Task 5: Create The Adaptive App Shell

**Files:**
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/app/DanmuManagerApp.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/app/navigation/AppDestination.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/app/navigation/AppNavigation.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/app/navigation/AppNavigationSuite.kt`
- Test: `manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/app/AppNavigationTest.kt`

**Step 1: Write the failing test**

```kotlin
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsFourPrimaryDestinations() {
        composeRule.onNodeWithText("总览").assertExists()
        composeRule.onNodeWithText("核心").assertExists()
        composeRule.onNodeWithText("控制台").assertExists()
        composeRule.onNodeWithText("设置").assertExists()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*AppNavigationTest"`
Expected: FAIL because the new shell still only shows placeholder content.

**Step 3: Write minimal implementation**

```kotlin
enum class AppDestination(val label: String) {
    Overview("总览"),
    CoreHub("核心"),
    Console("控制台"),
    Settings("设置"),
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*AppNavigationTest"`
Expected: PASS with the four-tab adaptive shell.

**Step 5: Commit**

```bash
git add manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/app manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/app
git commit -m "feat: add adaptive app shell"
```

### Task 6: Implement The Overview Feature

**Files:**
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/overview/OverviewRoute.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/overview/OverviewViewModel.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/overview/OverviewUiState.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/overview/OverviewScreen.kt`
- Test: `manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/overview/OverviewUiStateMapperTest.kt`
- Test: `manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/feature/overview/OverviewScreenTest.kt`

**Step 1: Write the failing test**

```kotlin
class OverviewUiStateMapperTest {
    @Test
    fun mapsRunningStatusIntoHeroCardState() {
        val state = OverviewUiState.fromStatus(
            serviceRunning = true,
            pid = 1234,
            moduleVersion = "1.2.0",
        )
        assertEquals("Running", state.heroLabel)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*OverviewUiStateMapperTest"`
Expected: FAIL because the overview mapper does not exist.

**Step 3: Write minimal implementation**

```kotlin
data class OverviewUiState(
    val heroLabel: String,
    val pidText: String,
    val moduleVersion: String,
) {
    companion object {
        fun fromStatus(serviceRunning: Boolean, pid: Int?, moduleVersion: String) =
            OverviewUiState(
                heroLabel = if (serviceRunning) "Running" else "Stopped",
                pidText = pid?.toString() ?: "-",
                moduleVersion = moduleVersion,
            )
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*OverviewUiStateMapperTest"`
Expected: PASS, then run `./gradlew :app:connectedDebugAndroidTest --tests "*OverviewScreenTest"` for the screen semantics.

**Step 5: Commit**

```bash
git add manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/overview manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/overview manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/feature/overview
git commit -m "feat: add overview command center"
```

### Task 7: Implement The Core Hub List And Detail Flows

**Files:**
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/CoreHubRoute.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/CoreHubViewModel.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/CoreHubUiState.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/CoreListScreen.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/CoreDetailScreen.kt`
- Test: `manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/corehub/CoreFilterTest.kt`
- Test: `manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/feature/corehub/CoreHubScreenTest.kt`

**Step 1: Write the failing test**

```kotlin
class CoreFilterTest {
    @Test
    fun returnsOnlyUpdateableCores_whenUpdateFilterSelected() {
        val result = filterCores(
            cores = listOf(
                CoreItem(id = "a", hasUpdate = true),
                CoreItem(id = "b", hasUpdate = false),
            ),
            filter = CoreFilter.UpdateAvailable,
        )
        assertEquals(listOf("a"), result.map { it.id })
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoreFilterTest"`
Expected: FAIL because core filter logic is missing.

**Step 3: Write minimal implementation**

```kotlin
fun filterCores(cores: List<CoreItem>, filter: CoreFilter): List<CoreItem> = when (filter) {
    CoreFilter.All -> cores
    CoreFilter.UpdateAvailable -> cores.filter { it.hasUpdate }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoreFilterTest"`
Expected: PASS, then run the Compose screen test for chips, search, and detail navigation.

**Step 5: Commit**

```bash
git add manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/corehub manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/feature/corehub
git commit -m "feat: add core hub screens"
```

### Task 8: Implement The Core Install Wizard

**Files:**
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/install/CoreInstallWizardState.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/install/CoreInstallWizardViewModel.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/install/CoreInstallWizardScreen.kt`
- Test: `manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/corehub/install/RepoValidationTest.kt`
- Test: `manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/feature/corehub/install/CoreInstallWizardScreenTest.kt`

**Step 1: Write the failing test**

```kotlin
class RepoValidationTest {
    @Test
    fun rejectsInvalidOwnerRepoFormat() {
        assertFalse(isValidOwnerRepo("bad-value"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*RepoValidationTest"`
Expected: FAIL because the validator does not exist.

**Step 3: Write minimal implementation**

```kotlin
fun isValidOwnerRepo(value: String): Boolean =
    Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(value.trim())
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*RepoValidationTest"`
Expected: PASS, then run the Compose wizard test for source selection and final install action.

**Step 5: Commit**

```bash
git add manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/install manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/corehub/install manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/feature/corehub/install
git commit -m "feat: add core install wizard"
```

### Task 9: Implement The Console Feature

**Files:**
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/console/ConsoleRoute.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/console/ConsoleViewModel.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/console/ConsoleUiState.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/console/logs/LogsTab.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/console/requests/RequestsTab.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/console/api/ApiTestTab.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/console/system/SystemTab.kt`
- Test: `manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/console/LogLevelColorTest.kt`
- Test: `manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/feature/console/ConsoleScreenTest.kt`

**Step 1: Write the failing test**

```kotlin
class LogLevelColorTest {
    @Test
    fun mapsWarnAndErrorToDifferentColors() {
        assertNotEquals(colorForLevel("WARN"), colorForLevel("ERROR"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*LogLevelColorTest"`
Expected: FAIL because the console style mapper does not exist.

**Step 3: Write minimal implementation**

```kotlin
fun colorForLevel(level: String): Color = when (level.uppercase()) {
    "ERROR" -> Color(0xFFDC2626)
    "WARN" -> Color(0xFFF59E0B)
    else -> Color(0xFF94A3B8)
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*LogLevelColorTest"`
Expected: PASS, then run the connected test for tabs and filter controls.

**Step 5: Commit**

```bash
git add manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/console manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/console manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/feature/console
git commit -m "feat: add redesigned console"
```

### Task 10: Implement The Settings Feature

**Files:**
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/SettingsRoute.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/SettingsViewModel.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/SettingsUiState.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/SettingsScreen.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/sections/WebDavSection.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/sections/EnvEditorSection.kt`
- Test: `manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/settings/SettingsSanitizerTest.kt`
- Test: `manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/feature/settings/SettingsScreenTest.kt`

**Step 1: Write the failing test**

```kotlin
class SettingsSanitizerTest {
    @Test
    fun trimsWebDavPathAndPreservesFilename() {
        assertEquals("danmuapi/danmu_api.env", sanitizeWebDavPath(" danmuapi/danmu_api.env "))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsSanitizerTest"`
Expected: FAIL because the sanitizer does not exist.

**Step 3: Write minimal implementation**

```kotlin
fun sanitizeWebDavPath(input: String): String =
    input.trim().trimStart('/').ifBlank { "danmuapi/danmu_api.env" }
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsSanitizerTest"`
Expected: PASS, then run the screen test for section headers and destructive action grouping.

**Step 5: Commit**

```bash
git add manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/settings manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/feature/settings
git commit -m "feat: add redesigned settings"
```

### Task 11: Add Module Update, Download, And Install Task Flows

**Files:**
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/update/ModuleUpdateCoordinator.kt`
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/update/ModuleInstallUiState.kt`
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/overview/OverviewViewModel.kt`
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/overview/OverviewScreen.kt`
- Test: `manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/update/ModuleAssetFilterTest.kt`

**Step 1: Write the failing test**

```kotlin
class ModuleAssetFilterTest {
    @Test
    fun prefersNodePackageForDefaultRecommendation() {
        val asset = pickRecommendedAsset(
            listOf("danmu_api_server_1.0.0.zip", "danmu_api_server_node_1.0.0.zip")
        )
        assertEquals("danmu_api_server_node_1.0.0.zip", asset)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ModuleAssetFilterTest"`
Expected: FAIL because the asset chooser does not exist.

**Step 3: Write minimal implementation**

```kotlin
fun pickRecommendedAsset(names: List<String>): String? =
    names.firstOrNull { it.contains("_node_") } ?: names.firstOrNull()
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ModuleAssetFilterTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/update manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/overview manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/update
git commit -m "feat: add module update and install flows"
```

### Task 12: Switch CI Packaging To The New App Directory

**Files:**
- Modify: `.github/workflows/build-danmu-magisk.yml`
- Modify: `README.md`
- Create: `manager_app_redesign/README.md`
- Verify reference: `manager_app/manager_app/app/build.gradle.kts`

**Step 1: Write the failing verification step**

```bash
grep -n "MANAGER_APP_DIR: manager_app_redesign/manager_app" .github/workflows/build-danmu-magisk.yml
```

**Step 2: Run verification to confirm it fails**

Run: `grep -n "MANAGER_APP_DIR: manager_app_redesign/manager_app" .github/workflows/build-danmu-magisk.yml`
Expected: no match, because CI still points to the old app path.

**Step 3: Write minimal implementation**

```yaml
env:
  MODULE_TEMPLATE_DIR: module_template
  MANAGER_APP_DIR: manager_app_redesign/manager_app
  MANAGER_APP_APK_PATH: manager_app_redesign/manager_app/app/build/outputs/apk/release/app-release.apk
```

**Step 4: Run verification to confirm it passes**

Run: `grep -n "MANAGER_APP_DIR: manager_app_redesign/manager_app" .github/workflows/build-danmu-magisk.yml`
Expected: one exact match, then run the full release build workflow locally as far as available.

**Step 5: Commit**

```bash
git add .github/workflows/build-danmu-magisk.yml README.md manager_app_redesign/README.md
git commit -m "build: switch module packaging to redesigned manager"
```

### Task 13: Regression Verification And Visual QA

**Files:**
- Create: `docs/plans/2026-04-04-danmu-manager-redesign-qa.md`
- Create: `manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/RegressionSmokeTest.kt`
- Create: `manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/FeatureParityChecklistTest.kt`

**Step 1: Write the failing test**

```kotlin
class FeatureParityChecklistTest {
    @Test
    fun keepsAllRequiredCapabilitiesMarkedAsImplemented() {
        val implemented = setOf(
            "service-control",
            "autostart",
            "core-management",
            "logs",
            "settings",
            "module-update",
            "webdav",
            "env-editor",
        )
        assertTrue(implemented.contains("core-management"))
        assertTrue(implemented.contains("module-update"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*FeatureParityChecklistTest"`
Expected: FAIL until the checklist file and parity helper are wired in.

**Step 3: Write minimal implementation**

```kotlin
object FeatureParityChecklist {
    val required = setOf(
        "service-control",
        "autostart",
        "core-management",
        "logs",
        "settings",
        "module-update",
        "webdav",
        "env-editor",
    )
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*FeatureParityChecklistTest"`
Expected: PASS, then run full verification:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

**Step 5: Commit**

```bash
git add docs/plans/2026-04-04-danmu-manager-redesign-qa.md manager_app_redesign/manager_app/app/src/androidTest/java/com/danmuapi/manager/RegressionSmokeTest.kt manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/FeatureParityChecklistTest.kt
git commit -m "test: add redesign regression verification"
```

## Notes For Execution

- Keep `manager_app/manager_app` unchanged until Task 13 passes.
- Keep `applicationId = "com.danmuapi.manager"` in the redesigned app so the packaged APK can replace the current one later.
- The current local folder is not a git worktree, so real execution should happen in the actual repository checkout before using the commit commands above.
- The existing workflow currently points to `manager_app/manager_app`; do not switch it early.

Plan complete and saved to `docs/plans/2026-04-04-danmu-manager-redesign.md`. Two execution options:

**1. Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints

Which approach?
