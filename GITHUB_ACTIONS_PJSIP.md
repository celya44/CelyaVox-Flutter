# GitHub Actions - Configuration des libs PJSIP

## 🔴 Problème

L'erreur lors du build GitHub Actions:
```
Could not create task ':app:copyJniLibsflutterBuildDebug'.
   > Task with name 'compileFlutterBuildDebug' not found in project ':app'.
```

**Cause** : Les libs PJSIP compilées (`libpjsip.so`, etc.) ne sont pas présentes dans `android/app/src/main/jniLibs/arm64-v8a/`

---

## ✅ Solutions

### Option 1 : Télécharger les libs pré-compilées (RECOMMANDÉ - Plus rapide)

Si vous avez des libs PJSIP pré-compilées stockées quelque part (ex: dans un artifact ou un storage cloud), ajoutez cette étape à votre workflow:

```yaml
- name: Download PJSIP libraries
  run: |
    mkdir -p android/app/src/main/jniLibs/arm64-v8a
    # Télécharger depuis votre stockage (exemple avec wget/curl)
    wget -O pjsip-libs.zip "https://your-storage.example.com/pjsip-libs.zip"
    unzip -o pjsip-libs.zip -d android/app/src/main/jniLibs/
    ls -la android/app/src/main/jniLibs/arm64-v8a/
```

### Option 2 : Compiler PJSIP dans le workflow (RECOMMANDÉ si pas de storage)

Ajoutez cette étape **avant** le build Flutter:

```yaml
- name: Build PJSIP libraries
  env:
    ANDROID_NDK_ROOT: ${{ env.ANDROID_NDK_ROOT }}
  run: |
    cd android/pjsip
    chmod +x build_pjsip.sh
    ./build_pjsip.sh
    echo "PJSIP build completed"
    
- name: Verify PJSIP libraries
  run: |
    ls -la android/app/src/main/jniLibs/arm64-v8a/ || \
    echo "ERROR: PJSIP libs not found!"
```

### Option 3 : Utiliser GitHub Artifacts (Pour caching)

**Step 1 : Workflow de compilation (à exécuter une fois)**
```yaml
# .github/workflows/build-pjsip.yml
name: Build PJSIP Libs

on:
  workflow_dispatch:  # Déclencher manuellement

jobs:
  build-pjsip:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup NDK
        uses: nativeActions/setup-ndk@v1
        with:
          ndk-version: r27
      
      - name: Build PJSIP
        run: |
          cd android/pjsip
          chmod +x build_pjsip.sh
          ./build_pjsip.sh
      
      - name: Upload PJSIP libs
        uses: actions/upload-artifact@v3
        with:
          name: pjsip-libs
          path: android/app/src/main/jniLibs/
          retention-days: 30
```

**Step 2 : Workflow principal (utilise le cache)**
```yaml
# .github/workflows/build-apk.yml
- name: Download PJSIP libs cache
  uses: actions/download-artifact@v3
  with:
    name: pjsip-libs
    path: android/app/src/main/jniLibs/
```

---

## 📝 Exemple de workflow complet

Voici un workflow GitHub Actions complet qui compile PJSIP et l'APK:

```yaml
name: Build Android APK with PJSIP

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    
    env:
      ANDROID_KEYSTORE_PATH: ${{ github.workspace }}/android/app/upload-keystore.jks
      GRADLE_OPTS: -Dorg.gradle.vfs.watch=false
      
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Flutter
        uses: subosito/flutter-action@v2
        with:
          flutter-version: '3.44.5'
          channel: 'stable'
      
      - name: Setup Android SDK
        uses: android-actions/setup-android@v3
        with:
          api-level: '36'
          ndk-version: '27.3.13750724'
      
      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'
      
      # Option A : Télécharger les libs (si vous les avez stockées)
      # - name: Download PJSIP libraries
      #   run: |
      #     mkdir -p android/app/src/main/jniLibs/arm64-v8a
      #     # Remplacer par votre URL de stockage
      #     wget -O libs.zip "https://your-storage.com/pjsip-libs.zip"
      #     unzip -o libs.zip -d android/app/src/main/jniLibs/
      
      # Option B : Compiler les libs (commentez l'Option A si utilisé)
      - name: Build PJSIP libraries
        env:
          ANDROID_NDK_ROOT: ${{ env.ANDROID_NDK_ROOT }}
        run: |
          cd android/pjsip
          chmod +x build_pjsip.sh
          ./build_pjsip.sh
      
      - name: Verify PJSIP libraries
        run: |
          ls -la android/app/src/main/jniLibs/arm64-v8a/
          if [ ! -f "android/app/src/main/jniLibs/arm64-v8a/libpjsip.so" ]; then
            echo "ERROR: libpjsip.so not found!"
            exit 1
          fi
      
      - name: Prepare keystore
        env:
          ANDROID_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
        run: |
          echo "$ANDROID_KEYSTORE_BASE64" | base64 -d > $ANDROID_KEYSTORE_PATH
      
      - name: Prepare Google Services JSON
        env:
          GOOGLE_SERVICES_JSON_BASE64: ${{ secrets.GOOGLE_SERVICES_JSON_BASE64 }}
        run: |
          echo "$GOOGLE_SERVICES_JSON_BASE64" | base64 -d > android/app/google-services.json
      
      - name: Get Flutter dependencies
        run: flutter pub get
      
      - name: Build APK (Release)
        env:
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
        run: |
          flutter build apk --release --no-tree-shake-icons
      
      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: app-release.apk
          path: build/app/outputs/apk/release/app-release.apk
      
      - name: Upload to Play Store
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.PLAY_STORE_SERVICE_ACCOUNT }}
          packageName: fr.celya.celyavox
          releaseFiles: 'build/app/outputs/apk/release/app-release.apk'
          track: internal
```

---

## 🚀 Déploiement du workflow

### Étape 1 : Créer le répertoire
```bash
mkdir -p .github/workflows
```

### Étape 2 : Copier le workflow
Sauvegardez le workflow YAML ci-dessus dans `.github/workflows/build-apk.yml`

### Étape 3 : Configurer les secrets GitHub
Allez à **Settings → Secrets and variables → Actions** et ajoutez:

```
ANDROID_KEYSTORE_BASE64      (votre keystore en base64)
ANDROID_KEYSTORE_PASSWORD    (password du keystore)
ANDROID_KEY_ALIAS            (alias de la clé)
ANDROID_KEY_PASSWORD         (password de la clé)
GOOGLE_SERVICES_JSON_BASE64  (firebase config en base64)
PLAY_STORE_SERVICE_ACCOUNT   (clé Play Store JSON)
```

### Étape 4 : Encoder les secrets en base64

Pour le keystore:
```bash
cat android/app/upload-keystore.jks | base64 -w 0
```

Pour google-services.json:
```bash
cat android/app/google-services.json | base64 -w 0
```

### Étape 5 : Tester le workflow
```bash
git push origin main
```

Le workflow devrait se déclencher automatiquement. Observez l'exécution dans **Actions** sur GitHub.

---

## ⚙️ Optimisations possibles

### Cache du build Gradle
```yaml
- uses: gradle/gradle-build-action@v2
  with:
    gradle-version: wrapper
```

### Cache PJSIP sources (si compilation longue)
```yaml
- name: Cache PJSIP sources
  uses: actions/cache@v3
  with:
    path: android/pjsip/pjproject-*
    key: pjsip-${{ hashFiles('android/pjsip/build_pjsip.sh') }}
```

### Build multivariant (debug + release)
```yaml
- name: Build APK (Debug)
  run: flutter build apk --debug
  
- name: Build APK (Release)
  run: flutter build apk --release
```

---

## 🔍 Debugging

### Logs complets
```yaml
- name: Build APK (Debug mode)
  run: flutter build apk --release -v 2>&1 | tee build.log
  
- name: Upload build logs
  if: failure()
  uses: actions/upload-artifact@v3
  with:
    name: build-logs
    path: build.log
```

### Vérification des libs
```yaml
- name: Check PJSIP libs
  run: |
    echo "=== PJSIP libs present ==="
    find android/app/src/main/jniLibs -name "*.so" -o -name "*.a" | head -20
    
    echo "=== Architecture ==="
    file android/app/src/main/jniLibs/arm64-v8a/*.so 2>/dev/null | head -5
```

---

## ❌ Problèmes courants

### "PJSIP libs directory not found"
**Solution** : Assurez-vous que l'étape de compilation/téléchargement se termine avant le build Flutter

### "libpjsip.so: No such file"
**Solution** : Vérifiez que le script `build_pjsip.sh` s'exécute sans erreur

### "build_pjsip.sh: permission denied"
**Solution** : Ajoutez `chmod +x` avant d'exécuter

### "ANDROID_NDK_ROOT not found"
**Solution** : L'action `setup-android` initialise les variables NDK correctement. Vérifiez votre version de l'action.

---

## 📊 Temps de build estimé

| Étape | Durée |
|-------|-------|
| Setup Flutter | 30s |
| Setup Android | 20s |
| Compilation PJSIP | **8-12 min** |
| Build Flutter | 2-3 min |
| Total | **~13-16 min** |

**Conseil** : Utilisez le caching ou téléchargez les libs pré-compilées pour réduire à ~5-7 min

---

## 📦 Pour une solution production

### Approche recommandée :
1. **Compiler PJSIP une fois** avec le workflow `build-pjsip.yml`
2. **Stocker les libs** dans GitHub Artifacts (retenus 30j) ou un S3/Artifact storage
3. **Réutiliser les libs** dans les builds futures pour économiser 10 minutes

### Alternative cloud :
```bash
# Stocker dans S3
aws s3 cp android/app/src/main/jniLibs/arm64-v8a/ s3://my-bucket/pjsip-libs/ --recursive

# Récupérer en CI
aws s3 sync s3://my-bucket/pjsip-libs/ android/app/src/main/jniLibs/arm64-v8a/
```

---

## 📞 Support

Si vous rencontrez des problèmes :

1. Vérifiez que `build_pjsip.sh` marche localement
2. Consultez les logs du workflow GitHub Actions
3. Assurez-vous que les secrets sont configurés correctement
4. Testez avec un workflow minimal d'abord
