# Guide de Build & Déploiement - CelyaVox VoIP

## 🎯 Objectif

Compiler et déployer l'APK CelyaVox en environnement production via GitHub Actions et Play Store.

---

## 📋 Checklist pré-build

- [ ] Libs PJSIP compilées (`android/app/src/main/jniLibs/arm64-v8a/*.so`)
- [ ] Firebase `google-services.json` configuré
- [ ] Keystore Android signé (`android/app/upload-keystore.jks`)
- [ ] Variables d'environnement définies
- [ ] Version code/name à jour dans `pubspec.yaml`

---

## 🔧 Build local (avant GitHub Actions)

### 1️⃣ Préparer les dépendances

```bash
cd /opt/CelyaVox-Flutter

# Nettoyer les builds précédents
flutter clean
rm -rf build/ .dart_tool/

# Télécharger les dépendances
flutter pub get

# Obtenir les librairies natives
cd android/pjsip
chmod +x build_pjsip.sh
./build_pjsip.sh  # ⚠️ À faire une seule fois, long (~10 min)
cd ../..
```

### 2️⃣ Configurer le keystore

```bash
# Vérifier que le keystore existe
ls -la android/app/upload-keystore.jks

# Sinon, le générer (unique pour votre app)
keytool -genkey -v -keystore android/app/upload-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 36500 \
  -alias celyavox -keypass MyKeyPass -storepass MyStorePass
```

### 3️⃣ Build APK local

```bash
# Debug (rapide, pour test)
flutter build apk --debug

# Release (pour production)
flutter build apk --release \
  --dart-define-from-file=android/.env.production

# Vérifier l'APK généré
ls -la build/app/outputs/apk/release/app-release.apk
```

---

## 🚀 GitHub Actions Setup

### Étape 1 : Créer le workflow

**Fichier** : `.github/workflows/build-and-deploy.yml`

```yaml
name: Build & Deploy Android APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

env:
  FLUTTER_VERSION: '3.44.5'
  JAVA_VERSION: '17'
  ANDROID_COMPILE_SDK: '36'
  ANDROID_NDK_VERSION: '27.3.13750724'

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 45

    steps:
      # 1. Checkout code
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # Pour versionning

      # 2. Setup Flutter
      - uses: subosito/flutter-action@v2
        with:
          flutter-version: ${{ env.FLUTTER_VERSION }}

      # 3. Setup Java
      - uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: ${{ env.JAVA_VERSION }}
          cache: 'gradle'

      # 4. Setup Android NDK/SDK
      - uses: android-actions/setup-android@v3

      # 5. Télécharger PJSIP libs (Option A - à adapter)
      - name: Prepare PJSIP libraries
        run: |
          mkdir -p android/app/src/main/jniLibs/arm64-v8a
          
          # Option 1 : Télécharger depuis artifact ou storage
          # wget https://your-storage/pjsip-libs.zip
          # unzip -o pjsip-libs.zip
          
          # Option 2 : Compiler (si pas de storage)
          cd android/pjsip
          chmod +x build_pjsip.sh
          ./build_pjsip.sh
          cd ../..
          
          # Vérifier
          ls -la android/app/src/main/jniLibs/arm64-v8a/ | head -10

      # 6. Préparer Firebase config
      - name: Decode Firebase config
        env:
          GOOGLE_SERVICES_JSON: ${{ secrets.GOOGLE_SERVICES_JSON_BASE64 }}
        run: |
          echo "$GOOGLE_SERVICES_JSON" | base64 -d > android/app/google-services.json

      # 7. Préparer Keystore
      - name: Decode signing key
        env:
          ANDROID_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
          ANDROID_KEYSTORE_PATH: ${{ github.workspace }}/android/app/upload-keystore.jks
        run: |
          echo "$ANDROID_KEYSTORE_BASE64" | base64 -d > "$ANDROID_KEYSTORE_PATH"

      # 8. Obtenir dépendances Flutter
      - name: Get Flutter dependencies
        run: flutter pub get

      # 9. Build APK Release
      - name: Build APK Release
        env:
          ANDROID_KEYSTORE_PATH: ${{ github.workspace }}/android/app/upload-keystore.jks
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
          GRADLE_OPTS: -Dorg.gradle.vfs.watch=false
        run: |
          flutter build apk --release -v

      # 10. Upload artifact
      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: app-release-apk
          path: build/app/outputs/apk/release/app-release.apk
          retention-days: 30

      # 11. Uploader vers Play Store (optionnel)
      - name: Upload to Play Store (Internal Testing)
        if: success()
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.PLAY_STORE_SERVICE_ACCOUNT }}
          packageName: fr.celya.celyavox
          releaseFiles: 'build/app/outputs/apk/release/app-release.apk'
          track: internal
          inAppUpdatePriority: 2

      # 12. Notifier en cas d'erreur
      - name: Notify on failure
        if: failure()
        run: echo "Build failed! Check the logs above."
```

### Étape 2 : Configurer les secrets GitHub

Allez à **Settings → Secrets and variables → Actions**

Ajoutez ces secrets :

| Secret | Valeur |
|--------|--------|
| `GOOGLE_SERVICES_JSON_BASE64` | `cat android/app/google-services.json \| base64 -w 0` |
| `ANDROID_KEYSTORE_BASE64` | `cat android/app/upload-keystore.jks \| base64 -w 0` |
| `ANDROID_KEYSTORE_PASSWORD` | Votre password du keystore |
| `ANDROID_KEY_ALIAS` | Votre alias (ex: celyavox) |
| `ANDROID_KEY_PASSWORD` | Votre password de clé |
| `PLAY_STORE_SERVICE_ACCOUNT` | JSON de Play Store (optionnel) |

#### Comment récupérer les valeurs :

```bash
# 1. Firebase config (base64)
cat android/app/google-services.json | base64 -w 0 | pbcopy

# 2. Keystore (base64)
cat android/app/upload-keystore.jks | base64 -w 0 | pbcopy

# 3. Obtenir Play Store Service Account
# Aller à Google Cloud Console → Service Accounts
# Créer une clé JSON
cat service-account.json | base64 -w 0 | pbcopy
```

### Étape 3 : Tester le workflow

```bash
# Push vers main déclenche automatiquement le build
git add .github/workflows/
git commit -m "feat: add GitHub Actions CI/CD pipeline"
git push origin main

# Ou déclencher manuellement dans GitHub UI
# Actions → Build & Deploy → Run workflow
```

---

## 📊 Monitoring du build

### Voir l'exécution
1. Allez à **Actions** sur GitHub
2. Cliquez sur le workflow en cours
3. Observez les étapes

### Logs détaillés
```bash
# Si le build échoue, vérifiez :
# 1. Que les secrets sont bien définis
# 2. Que les libs PJSIP existent
# 3. Que firebase-config existe
```

### Récupérer l'APK généré
```bash
# Après un build réussi
# Allez à Actions → Build job → Artifacts
# Téléchargez "app-release-apk"
```

---

## 🐛 Dépannage

### Erreur : "PJSIP libs directory not found"
```bash
# Solution : Compiler les libs localement d'abord
cd android/pjsip
chmod +x build_pjsip.sh
./build_pjsip.sh
```

### Erreur : "Missing google-services.json"
```bash
# Vérifier que le secret GOOGLE_SERVICES_JSON_BASE64 est défini
# Ou décoder le secret :
echo "$GOOGLE_SERVICES_JSON" | base64 -d
```

### Erreur : "Invalid keystore"
```bash
# Vérifier le keystore
keytool -list -v -keystore android/app/upload-keystore.jks

# Régénérer si nécessaire
rm android/app/upload-keystore.jks
keytool -genkey -v -keystore android/app/upload-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 36500 \
  -alias celyavox
```

### Build timeout
```yaml
# Dans le workflow, augmenter le timeout
timeout-minutes: 60  # Au lieu de 45
```

---

## 📝 Version management

### Mise à jour de la version

**Fichier** : `pubspec.yaml`
```yaml
version: 1.0.0+1  # major.minor.patch+build_number
```

Pour chaque release :
```bash
# Augmenter le build number
version: 1.0.0+2

# Ou pour une release majeure/mineure
version: 1.1.0+1
```

### Versionning automatisé (optionnel)

```yaml
# Ajouter dans le workflow
- name: Auto-increment version
  run: |
    # Récupérer le dernier build number depuis Play Store
    # Et l'incrémenter dans pubspec.yaml
    sed -i 's/version: .*/version: 1.0.0+'"$GITHUB_RUN_NUMBER"'/' pubspec.yaml
```

---

## 📦 Déploiement Play Store

### Options de déploiement

```yaml
# 1. Internal testing (testers Google Play)
track: internal

# 2. Closed testing (bêta)
track: beta

# 3. Production
track: production
```

### Prioriser les mises à jour in-app

```yaml
inAppUpdatePriority: 2  # 1-5, priorité de mise à jour
```

---

## 🔒 Sécurité

### Ne jamais commiter ces fichiers

```bash
# .gitignore
android/app/upload-keystore.jks
android/app/google-services.json
.env
.env.local
android/.env.production
```

### Rotation des secrets

Tous les 6-12 mois :
```bash
# 1. Générer un nouveau keystore
# 2. Mettre à jour le Play Store
# 3. Mettre à jour les secrets GitHub
```

---

## 📈 Optimisations

### Cache gradle
```yaml
- uses: gradle/gradle-build-action@v2
```

### Build multivariant
```bash
# Debug + Release en parallèle
flutter build apk --debug &
flutter build apk --release
```

### Approuver les APK avant Play Store
```yaml
# Ajouter une approbation manuelle
- name: Wait for approval
  if: ${{ github.event_name == 'workflow_dispatch' }}
  run: echo "APK ready for manual deployment"
```

---

## ✅ Checklist de déploiement production

- [ ] Version augmentée dans `pubspec.yaml`
- [ ] Changelog mis à jour
- [ ] Tests manuels effectués en debug
- [ ] Secrets GitHub configurés
- [ ] Libs PJSIP compilées et vérifiées
- [ ] Workflow GitHub Actions en place
- [ ] Build réussi sur main branch
- [ ] APK signé et vérifiable
- [ ] Play Store en version interne testée
- [ ] Utilisateurs bêta acceptent l'appli
- [ ] Déploiement en production approuvé
- [ ] Monitoring en place (Crashlytics)

---

## 📞 Support

### Commandes utiles

```bash
# Vérifier les permissions APK
aapt dump permissions build/app/outputs/apk/release/app-release.apk

# Vérifier la signature
jarsigner -verify -verbose build/app/outputs/apk/release/app-release.apk

# Extraire infos
apksigner verify -v build/app/outputs/apk/release/app-release.apk

# Size du build
du -sh build/app/outputs/apk/release/app-release.apk
```

### Resources

- [Flutter APK Build](https://docs.flutter.dev/deployment/android)
- [Play Store Setup](https://support.google.com/googleplay/android-developer/)
- [GitHub Actions](https://docs.github.com/en/actions)
