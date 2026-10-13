# replier-android

Android (Kotlin) SDK for the [Replier.net](https://www.replier.net) WhatsApp API.
minSdk 21, coroutine-based `suspend` functions, with `kotlinx-coroutines` as the only dependency.

## Install

```kotlin
dependencies {
    implementation("net.replier:replier-android:1.0.0")
}
```

## ⚠️ Keep credentials out of the APK

APKs can be decompiled. **Do not embed your instance `token` or account `apiKey` in a
public app.** Run a small backend proxy that forwards `POST /replier/{method}` to
`https://api.replier.net/{method}` and adds `instanceid` + `token` to the JSON body,
then point the SDK at it:

```kotlin
val replier = Replier(baseUrl = "https://your-backend.example.com/replier")
```

Pass `instanceId` / `token` directly only in internal or enterprise apps.

## Usage

```kotlin
import net.replier.android.Replier
import net.replier.android.ReplierException

val replier = Replier(instanceId = "12345", token = "your-instance-token")

lifecycleScope.launch {
    try {
        val sent = replier.sendMessage("15551234567", "Hello!")
        Log.d("Replier", "sent ${sent.optString("id")}")

        replier.sendLocation("15551234567", 37.422, -122.084, "Googleplex")
        replier.createGroup("Team", listOf("15551234567", "15557654321"))
    } catch (e: ReplierException) {
        Log.e("Replier", "${e.errorCode} ${e.message}")
    }
}
```

Responses are `org.json.JSONObject`. Call an endpoint that is not wrapped yet with
`replier.call("someNewEndpoint", mapOf("param" to "value"))`.
See the [method reference](../README.md#method-reference).

## Build & publish

Open this folder in Android Studio (or add a Gradle wrapper with `gradle wrapper`), then:

```bash
./gradlew :replier:assembleRelease
./gradlew :replier:publishToMavenLocal     # local test
# For Maven Central / JitPack, add the repository + signing config to the publishing block.
```

## License

MIT
