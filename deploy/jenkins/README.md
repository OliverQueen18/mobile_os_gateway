# Jenkins — mobile OS Gateway

Pipeline déclarative : `Jenkinsfile` à la racine du dépôt `mobile_os_gateway`.

## Créer le job

1. **New Item** → **Pipeline** → nom : `mobile-osgateway` (ou similaire).
2. **Pipeline** → **Definition** : *Pipeline script from SCM*.
3. **SCM** : Git → `https://github.com/OliverQueen18/mobile_os_gateway.git` → branche `main`.
4. **Script Path** : `Jenkinsfile`.

## Prérequis agent Jenkins

- **Java 17** (Gradle / AGP).
- **Docker** (recommandé) : si `ANDROID_HOME` n’est pas défini, le build utilise `ghcr.io/cirruslabs/android-sdk:36`.
- Ou installer le **Android SDK** (API 36, build-tools) et exporter `ANDROID_HOME`.

Credentials déjà utilisés côté backend/frontend :

| ID | Type | Usage |
|----|------|--------|
| `ssh-server-credentials` | SSH private key | Déploiement APK sur le VPS (option `DEPLOY_APK`) |

Optionnels :

| ID | Type | Usage |
|----|------|--------|
| `osgateway-google-services-gateway` | Secret file | `gateway-app/google-services.json` |
| `osgateway-google-services-distributor` | Secret file | `distributor-app/google-services.json` |
| `android-keystore` | Secret file | Fichier `.jks` / `.keystore` |
| `android-keystore-password` | Secret text | Mot de passe keystore |
| `android-key-alias` | Secret text | Alias clé |
| `android-key-password` | Secret text | Mot de passe clé |

Sans credentials Firebase, le pipeline copie les **stubs** `deploy/jenkins/google-services.*.stub.json` (build CI ; FCM prod nécessite les vrais fichiers + `USE_FIREBASE_CREDENTIALS`).

## Paramètres utiles

- **BUILD_VARIANT** : `debug` (défaut interne) ou `release`.
- **APPS** : gateway, distributor ou les deux.
- **DEPLOY_APK** : copie vers `DEPLOY_APK_DIR` sur le VPS (ex. `/home/adminubuntu/OliveApps/OSGATEWAY/apk`).

## Sorties

- APK archivés dans Jenkins : `artifacts/*-b<BUILD_NUMBER>.apk`.
- Chemins Gradle locaux : `${WORKSPACE}/.android-build/<module>/build/outputs/apk/...` (variable `OSG_ANDROID_BUILD_DIR`).

## Build manuel (sans Jenkins)

```bash
export OSG_ANDROID_BUILD_DIR="$(pwd)/.android-build"
./gradlew :gateway-app:assembleDebug :distributor-app:assembleDebug
```

Sur poste Windows / OneDrive, les sorties vont par défaut dans `%USERPROFILE%\.os-gateway-android-builds\os-gateway-mobile\`.
