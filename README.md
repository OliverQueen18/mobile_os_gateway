# OS Gateway — Applications Android

Projet Gradle multi-modules contenant :

| Module | Package | Rôle |
|--------|---------|------|
| `gateway-app` | `com.osgateway.gateway` | Téléphone Gateway (USSD + SMS + heartbeat) |
| `distributor-app` | `com.osgateway.distributor` | Application distributeur (transactions) |
| `shared` | `com.osgateway.shared` | Modèles API + client Retrofit/OkHttp |

- **minSdk** 26 · **targetSdk** 34 · **Kotlin** · **Jetpack Compose Material3**
- URL API par défaut (émulateur) : `http://10.0.2.2:8080/api/v1/`

## Ouvrir dans Android Studio

1. Installer **Android Studio Hedgehog+** (ou plus récent) avec SDK 34.
2. **File → Open** → sélectionner le dossier `mobile_os_gateway`.
3. Laisser Gradle synchroniser (Android Studio téléchargera le wrapper Gradle 8.7 si besoin).
4. Choisir la configuration d’exécution `gateway-app` ou `distributor-app`.
5. Lancer sur un émulateur ou un appareil physique.

> Si `gradle-wrapper.jar` est absent, Android Studio le régénère via **File → Settings → Build → Gradle** ou la commande ci-dessous.

```bash
# Depuis mobile_os_gateway (si Gradle est installé localement)
gradle wrapper --gradle-version 8.7
```

## Configurer l’URL API

1. À l’écran de **connexion**, renseigner l’URL de base, par exemple :
   - Émulateur → `http://10.0.2.2:8080/api/v1/`
   - Appareil physique (même Wi‑Fi) → `http://<IP-LAN-PC>:8080/api/v1/`
2. Ou après connexion : **Réglages / Profil → URL API**.
3. Les jetons JWT sont stockés via **EncryptedSharedPreferences**.

Le trafic HTTP clair est autorisé en debug via `network_security_config.xml` (à restreindre en production).

## Permissions Gateway (critique)

Sur `gateway-app`, accorder :

1. **SMS** (envoyer / recevoir / lire)
2. **Téléphone** (`CALL_PHONE`, `READ_PHONE_STATE`)
3. **Localisation** (GPS pour le heartbeat)
4. **Notifications** (Android 13+)
5. **Accessibilité** (obligatoire pour le moteur USSD) :
   - Paramètres Android → **Accessibilité** → **OS Gateway USSD Engine** → Activer
   - Ou depuis l’app → Sync → *Activer le service d’accessibilité USSD*
6. Recommandé : ignorer l’optimisation batterie pour le heartbeat 30 s.

> L’automatisation USSD via `AccessibilityService` est **best-effort** : les OEM et versions Android récentes limitent l’accès aux dialogues système.

## Build APK

Les sorties Gradle sont hors du dossier OneDrive (voir `build.gradle.kts`) :

- Windows : `%USERPROFILE%\.os-gateway-android-builds\os-gateway-mobile\`
- CI Jenkins : `${WORKSPACE}/.android-build/` (`OSG_ANDROID_BUILD_DIR`)

```bash
# Windows
.\gradlew.bat :gateway-app:assembleDebug
.\gradlew.bat :distributor-app:assembleDebug

# APK (exemple)
# .../gateway-app/build/outputs/apk/debug/gateway-app-debug.apk
# .../distributor-app/build/outputs/apk/debug/distributor-app-debug.apk
```

### Jenkins

Pipeline dédié : `Jenkinsfile` — voir [deploy/jenkins/README.md](deploy/jenkins/README.md).

Release (nécessite un keystore) :

```bash
.\gradlew.bat :gateway-app:assembleRelease
.\gradlew.bat :distributor-app:assembleRelease
```

## FCM (optionnel)

Des fichiers stub sont fournis :

- `gateway-app/google-services.json.stub`
- `distributor-app/google-services.json.stub`

Pour activer Firebase Cloud Messaging :

1. Créer un projet Firebase et ajouter les apps Android.
2. Renommer le stub en `google-services.json` (valeurs réelles).
3. Décommenter le plugin `com.google.gms.google-services` et les dépendances Firebase dans les `build.gradle.kts`.

## Architecture Gateway (aperçu)

- `UssdAccessibilityService` — détecte les dialogues USSD, lit le texte, auto-réponses
- `UssdScenarioExecutor` / `UssdSessionController` — scénarios `COMPOSE|READ|REPLY|WAIT|VALIDATE|EXTRACT|WAIT_SMS`
- `GatewayForegroundService` — heartbeat toutes les **30 s**
- `HeartbeatWorker` / `TaskPollingWorker` — WorkManager (secours + polling tâches)
- `SmsEngine` + `SmsReceivedReceiver` — envoi `SmsManager`, réception, report API
- `DeviceMetricsCollector` — batterie, réseau, GPS, mémoire, stockage, température

## API consommée (via API Gateway :8080)

- `POST /api/v1/auth/login`
- `POST /api/v1/gateways/{id}/heartbeat`
- `GET  /api/v1/gateways/{id}/tasks`
- `POST /api/v1/gateways/{id}/tasks/{taskId}/result`
- `POST /api/v1/sms/report`
- `POST /api/v1/transactions` (+ historique, recherche, stats)

## Licence

Propriétaire — projet OS Gateway.
