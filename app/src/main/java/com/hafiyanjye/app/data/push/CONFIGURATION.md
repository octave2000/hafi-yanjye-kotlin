# Push notifications (Firebase Cloud Messaging)

The app builds and runs without Firebase. When any of the four Gradle
properties below is empty, nothing of Firebase starts (its init provider is
removed from the manifest) and the profile's switch says push is not set up
in this build. The in-app inbox works either way.

## What to create in Firebase

1. Firebase console → create a project (or use the one the engine will use).
2. In that project, add an **Android app** with package name
   `com.hafiyanjye.app`. Add a second Android app `com.hafiyanjye.app.debug`
   if debug builds should receive push too (debug builds use that suffix).
3. Download that app's `google-services.json` only to read four values from it
   (the app does not use the file or the Google Services plugin).
4. Project settings → Service accounts → **Generate new private key**. This
   JSON file is for the engine only; it never goes into the app.

## Where each value goes

App: Gradle properties, e.g. in `~/.gradle/gradle.properties` (not committed):

```
FIREBASE_APP_ID=<client[].client_info.mobilesdk_app_id, e.g. 1:1234567890:android:abc123>
FIREBASE_API_KEY=<client[].api_key[].current_key>
FIREBASE_PROJECT_ID=<project_info.project_id>
FIREBASE_SENDER_ID=<project_info.project_number>
```

A debug build needs the `mobilesdk_app_id` of the `.debug` app; the other
three are the same for both.

Engine (its environment), from the service-account key file of the same project:

```
FCM_PROJECT_ID=<project_id>
FCM_CLIENT_EMAIL=<client_email>
FCM_PRIVATE_KEY=<private_key, the PEM; literal \n is fine>
```

All three or none; without them the engine skips FCM.

## How it behaves

- Signed in, Android allows notifications and the switch is on (the default):
  the FCM token is registered (`POST /api/v1/push/devices/register`,
  `{ token, platform: "android" }`), again on every sign-in and new token.
  On Android 13+ the switch asks for the permission first.
- Sign-out: `Session.beforeLogout` unregisters the token
  (`POST /api/v1/push/devices/unregister`, `{ token }`) and revokes it in
  Firebase. An expired session or a deleted account revokes it locally.
- Turning the switch off does the same as sign-out for push.
- Messages arrive on the `default` channel with the brand mark as the status-bar
  icon (`res/drawable/ic_notification.xml`). A tap opens `MainActivity` with the
  string extra `url`, which the shell routes through `Routes.fromWebPath`.
