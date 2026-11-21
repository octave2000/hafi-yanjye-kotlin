# Hafi Yanjye for Android: how this project is built

Native Android client (Kotlin, Jetpack Compose, Material3) for the Hafi Yanjye
engine. It must reach full parity with the web app in `../startket-kit-007/web`.
The web app is the reference for every screen: its flows, states, copy and
rules. The engine (`../startket-kit-007/engine`) is shared and already speaks
Bearer tokens.

Paths below: `SRC` = `android/app/src/main/java/com/hafiyanjye/app`,
`TEST` = `android/app/src/test/java/com/hafiyanjye/app`,
`WEB` = `startket-kit-007/web/src`, `ENGINE` = `startket-kit-007/engine/src`.

## Product rules (hard)

- **White and green only. Light theme only.** No dark mode, no purple/indigo/violet, no gradients.
- **Square corners everywhere** (true circles such as dots are fine). Flat: 1dp borders, no card shadows.
- **Money role names.** The engine and web code say `seeker` / `provider`; people must read the opposite:
  - engine `seeker` (app `Role.Hirer`) = the **job provider**: has the money, posts requests, hires.
  - engine `provider` (app `Role.Worker`) = the **job seeker**: wants work, gets pings, has a work profile and a CV.
  Never show the word "provider" for the worker or "seeker" for the hirer. Prefer "job provider" / "job seeker", or plain words ("the person you hired", "people nearby"). Before writing copy ask "who has the money here?". Kotlin names for DTO fields should say what they mean (`hirerName` with `@SerialName("seekerName")`).
- **Stay in the app.** Never hand off to Google Maps or another app for maps, routes or navigation; draw them on our own map. Phone calls (`tel:`) and sharing a file are fine.
- **Honest design.** No fake urgency, no invented activity or numbers. Show what the engine says.
- Copy is plain English (the web's wording is the source; fix role words as above). Real, plausible content; design for long text, empty lists, small screens, large font sizes.

## What exists (read before writing)

- `SRC/HafiApp.kt`: `AppContainer` with `http` (OkHttp), `session`, `client` (ApiClient), `api` (account endpoints), `hub` (live events), `badges`, `appScope`, `app`.
- `SRC/data/api/ApiClient.kt`: `get/post/put/delete<T>(path, …)` with kotlinx.serialization, `upload<T>(path, bytes, contentType, query)`, `download(path)`, `newRequest(path, query)` + `execute(request, serializer)` for anything else. Throws `ApiException(status, code, message)`; show `error.userMessage()`.
- `SRC/data/api/Models.kt`: `Me`, `Role`, `JobStatus`, `SessionState`, `JobListItem`, `Ping`, `PingMatch`, `Urgency`, `PingStatus`, `JobRequirement`, `Timestamp` (an `Instant` that reads the engine's ISO dates), `HafiJson`.
- `SRC/data/realtime/RealtimeHub.kt`: `hub.events` (`RealtimeEvent(type, data)` with `int("jobId")`, `decode<T>("message")`, type constants), `hub.resyncs` (reload now), `hub.connected`.
- `SRC/data/Badges.kt`: `chatUnread`, `notificationsUnread` (MutableStateFlow<Int>).
- `SRC/core/Format.kt` (`Format.rwf`, `budget`, `km`, `timeAgo`, `secondsUntil`, `duration`, `phone`, `fullPhone`), `core/JobStatusView.kt` (`Tone`, `jobStatusView`), `core/Media.kt` (`mediaUrl(path)`).
- `SRC/ui/Loader.kt`: `LocalContainer`, `containerViewModel { MyViewModel(it.client, it.hub) }`, `Load<T>` (`Loading` / `Ready(value, refreshing, staleError)` / `Failed(message)`), `Loader(scope) { fetch() }` with `refresh()`, `mutate { }`, `refreshOn(flow)`.
- `SRC/ui/nav/Routes.kt`: every route, `LocalMe` (the signed-in `Me`), `Routes.fromWebPath(url)`. `SRC/ui/nav/ComingSoon.kt`: the placeholder your screens replace.
- `SRC/ui/theme`: `Hafi` colours, `HafiType` text styles, `rememberReducedMotion()`.
- `SRC/ui/kit`: `HafiButton` (kinds Accent, Secondary, Outline, Ghost, Destructive; sizes Medium, Large; `loading`), `HafiField`, `FormAlert`, `ErrorPanel`, `Badge`, `PulseDot`, `Skeleton`, `EmptyState`, `FullScreenLoading`, `PageHeader`, `HIcon`, `categoryIcon(name)`, plus (kit item) `Avatar`, `RemoteImage`, `HafiChip`, `Tag`, `HafiSheet`, `HafiDialog`, `DetailScaffold`, `HafiIconButton`, `HafiTextArea`, `HafiSwitchRow`, `SegmentedControl`, `HafiSelect`, `HafiSlider`, `StarRating`, `StarInput`, `SectionCard`, `ListRow`, `StatTile`, `ProgressBar`, `Divider`, `Toaster` / `LocalToaster`, `TabStrip`, `InfoBanner`. Read the files for exact signatures.
- `SRC/ui/jobs/JobsScreen.kt`, `ui/pings/PingsScreen.kt`, `ui/profile/ProfileScreen.kt`, `ui/auth/*`: finished screens; copy their structure (ViewModel + Loader + states).
- Tests: `TEST/data/ApiClientTest.kt` shows MockWebServer tests; `TEST/core/FormatTest.kt` plain ones.

## How a feature is laid out

Each feature owns its folders and nothing else:

```
SRC/ui/<feature>/<Feature>Graph.kt   fun NavGraphBuilder.<feature>Graph(nav: NavHostController)   (exists as a stub: replace its body, keep name and signature)
SRC/ui/<feature>/…Screen.kt, …ViewModel.kt, parts
SRC/data/<feature>/<Feature>Api.kt     class <Feature>Api(private val client: ApiClient) { suspend fun … }
SRC/data/<feature>/<Feature>Models.kt  @Serializable DTOs for this feature
TEST/<feature>/…Test.kt
```

- **Navigation.** Register your routes in your graph function with `composable(Routes.X)` / `composable(Routes.XPattern, arguments = listOf(navArgument("id") { type = NavType.IntType }))`. Go elsewhere with `nav.navigate(Routes.request(jobId))`; another feature's screens are reached only through `Routes`. Tab-root screens are drawn between the shell's brand bar and bottom tab bar: no own top bar, start with `PageHeader`, no system-bar padding. Pushed screens (every `…Pattern` route and non-tab route) use `DetailScaffold(title, onBack = { nav.popBackStack() })`.
- **Data.** DTOs come from `ENGINE/shared/contracts.ts` and the endpoint files in `ENGINE/api/v1/**/index.ts` (the function's input type is the request, its return type the response; method = name prefix: `get`/`list` GET with query, `create` POST, `update` PUT, `delete` DELETE with query; path = folder, `[id]` = path segment). `startket-kit-007/openapi.json` has every schema. Use `@Serializable` data classes with defaults for optional fields, `Timestamp` for dates, enums with `@SerialName` for string unions, `Int?`/`String?` for nullable. If a DTO you need is already in `data/api/Models.kt`, import it; do not copy it. If two features need the same new DTO, the one named in your task as its owner defines it.
- **State.** One ViewModel per screen, made with `containerViewModel { … }` (pass `key = "request-$jobId"` when the screen takes an id). Lists and details use `Loader`; forms keep a `MutableStateFlow` UI state. Every screen handles: loading (skeletons, not a blank), empty (EmptyState with one next step), error with retry (ErrorPanel), stale-while-refreshing, and for actions: in progress (button `loading`), failure (FormAlert or toast), success. Pull to refresh on lists (`PullToRefreshBox`).
- **Live.** Reload on the events that concern the screen and on resync: `Loader(viewModelScope) { … }.refreshOn(hub.resyncs).refreshOn(hub.events.filter { it.type == RealtimeEvent.SessionUpdated && it.int("jobId") == jobId })`.
- **Uploads.** Photos: resize to JPEG on the phone (longest side 512 for avatars, 1600 otherwise; at most 1.5 MB), then `client.upload("/api/media", bytes, "image/jpeg", mapOf("kind" to "work", "w" to w, "h" to h))` which returns `{ id, url }` (see `WEB/lib/upload-image.ts`). Pick with the system photo picker (`ActivityResultContracts.PickVisualMedia`), no storage permission.
- **Accessibility.** WCAG AA contrast (use the theme colours as they are), 48dp touch targets, `contentDescription` on icon-only controls and null on decorative icons, headings marked with `semantics { heading() }`, state not by colour alone, focus order follows reading order, respects font scaling (no fixed heights around text), `rememberReducedMotion()` for anything that loops or moves far.
- **Code.** Kotlin official style, 4 spaces. Comments say why, not what. No `!!`. No hard-coded colours or text sizes outside the theme. No TODOs left behind: finish it or leave it out and say so in your final message.

## Maps and places (item `location`, folder `SRC/ui/location` and `SRC/data/location`)

Built at the same time as the features. Build everything else of your feature first; when you reach a map or a place picker and `SRC/ui/location/HafiMap.kt` is not there yet, `bloom_wait {"for":{"item":"location"}}`. The contract (package `com.hafiyanjye.app.ui.location`):

```kotlin
@Serializable data class GeoPoint(val lat: Double, val lng: Double)
@Serializable enum class LocationSource { Gps, Places, Typed, Pin }          // wire: "gps" | "places" | "typed" | "pin"
@Serializable data class PickedLocation(val lat: Double, val lng: Double, val label: String? = null,
    val source: LocationSource = LocationSource.Pin, val approximate: Boolean = false) { val point: GeoPoint }
object Kigali { val Center: GeoPoint }
fun distanceKm(a: GeoPoint, b: GeoPoint): Double

enum class MarkerKind { Pin, Me, Person, Car, Home, Destination }
data class MapMarker(val id: String, val position: GeoPoint, val kind: MarkerKind = MarkerKind.Pin,
    val title: String? = null, val heading: Float? = null, val selected: Boolean = false)
data class MapLine(val points: List<GeoPoint>, val dashed: Boolean = false)
data class MapCircle(val center: GeoPoint, val radiusKm: Double)

@Composable fun HafiMap(modifier: Modifier = Modifier, center: GeoPoint = Kigali.Center, zoom: Float = 14f,
    markers: List<MapMarker> = emptyList(), lines: List<MapLine> = emptyList(), circles: List<MapCircle> = emptyList(),
    fitToContent: Boolean = false, interactive: Boolean = true,
    onMarkerClick: ((String) -> Unit)? = null, onMapClick: ((GeoPoint) -> Unit)? = null)

/** A form field: shows the chosen place; offers "Use my location", typing a place, and moving a pin on a full-screen map. */
@Composable fun LocationPicker(value: PickedLocation?, onChange: (PickedLocation) -> Unit, modifier: Modifier = Modifier,
    label: String = "Where", hint: String? = null, error: String? = null, enabled: Boolean = true)

enum class LocationStatus { Idle, Asking, Locating, Ready, Denied, Unavailable }
@Stable class CurrentLocationState { val location: PickedLocation?; val status: LocationStatus; fun request() }
@Composable fun rememberCurrentLocation(auto: Boolean = false): CurrentLocationState   // asks permission when needed

// package com.hafiyanjye.app.data.location
data class Route(val points: List<GeoPoint>, val distanceMeters: Int, val durationSeconds: Int)
class Routing(http: OkHttpClient) { suspend fun route(from: GeoPoint, to: GeoPoint): Route? }   // null when no route service answers
```

## Code style

- Readable over compact. At most 120 characters per line. One statement per line; no `;` chains.
- Compose: put each modifier of a long chain on its own line; one parameter per line when a call does not fit on one line; extract a private composable when a block passes ~40 lines or is nested more than four levels.
- Name things for what they mean in the product (`hirer`, `jobSeeker`, `expiresAt`), not `p`, `x`, `vm2`.
- No wildcard imports (`import x.y.*`), no unused code, no commented-out code, no `@Suppress` to hide a real problem.
- Every public class and every non-obvious function gets a one-line KDoc saying why it exists.

## Shared files: do not edit

Everything outside your feature folders is someone else's: `ui/kit`, `ui/theme`, `ui/nav`, `ui/shell`, `ui/Loader.kt`, `ui/AppRoot.kt`, `data/api/*`, `data/realtime/RealtimeHub.kt`, `data/session`, `HafiApp.kt`, `MainActivity.kt`, `core/*`, Gradle files, the manifest, `tools/`.

- Need a new icon: `bloom_claim` `android/tools/icons.txt` and `android/app/src/main/res/drawable/`, add the Lucide name (kebab-case) to `icons.txt`, run `node android/tools/icons.mjs startket-kit-007/node_modules/.bun/lucide-react@1.53.0+0ac650c021fb1b59/node_modules/lucide-react/dist/esm/icons`, then `bloom_done` both paths at once. Icons are `R.drawable.ic_<name_with_underscores>`.
- Need a small change to the manifest, a Gradle file or `gradle/libs.versions.toml`: `bloom_claim` that one file, make the smallest change, `bloom_done` it straight away.
- Need something in a shared Kotlin file (a missing kit component, a route, a field on a shared DTO): build it inside your own folder if you can; otherwise `bloom_note` to the lead saying exactly what you need. Do not edit it yourself.
- A screen of yours that needs another feature's piece (a map, a location picker): use the contract named in your task; if that file is not there yet, `bloom_wait` for its item.

## Checking your work

- `android/tools/check.sh <your folders…>` from the folder root, e.g. `android/tools/check.sh ui/chat data/chat`. It compiles the app and passes when no error is in your folders (errors in other folders are listed as not yours; do not fix them). With tests: `android/tools/check.sh --test 'com.hafiyanjye.app.chat.*' ui/chat data/chat` (tests only run once the whole module compiles; if others' errors block that, say so and move on).
- **Never run `./gradlew` yourself and never start an emulator**: the computer has little memory, and `check.sh` queues builds one at a time. A check can take a few minutes while others build.
- Unit-test what is logic: DTO decoding against real-looking JSON, request bodies and paths (MockWebServer), state machines, formatting. Put tests under `TEST/<feature>/`.
- The local engine runs at `http://localhost:3100` with demo data. To see real responses: sign in with `curl -s -X POST localhost:3100/api/v1/auth/token/login -H 'content-type: application/json' -d '{"email":"demo.seeker@hafiyanjye.test","password":"demo-pass-123"}'` (the demo job provider; job seekers are `demo.provider1@hafiyanjye.test` … `demo.provider41@…`, same password), then send `Authorization: Bearer <token>`. GET freely. Keep writes few and only on demo accounts; never change a password or delete an account.
- Report each check with `bloom_check`. When you will not edit your files again, `bloom_done`.

## Final message of a worker

1. What now works (screens, routes), in a few lines.
2. Public pieces other features may use (composables, classes) with signatures.
3. Checks you ran and their results.
4. What you could not do or verify, plainly (e.g. "not run on a device", "X needs a route that does not exist").
