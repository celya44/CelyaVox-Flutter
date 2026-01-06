# Secrets CI (Android / iOS)

## Android
- `ANDROID_KEYSTORE_BASE64` : keystore encodé base64
- `ANDROID_KEYSTORE_PASSWORD` : mot de passe du keystore
- `ANDROID_KEY_ALIAS` : alias de la clé
- `ANDROID_KEY_PASSWORD` : mot de passe de la clé
- (optionnel) `ANDROID_KEYSTORE_PATH` : chemin custom si différent de `android/app/upload-keystore.jks`

### Générer un keystore Android
```bash
keytool -genkeypair \
  -v \
  -keystore upload-keystore.jks \
  -storetype JKS \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias your-key-alias
```

### Encoder en base64 (pour le secret)
```bash
base64 -w0 upload-keystore.jks > keystore.b64
```

## iOS
- `MATCH_PASSWORD` : mot de passe du repo privé de provisioning (si utilisé)
- `APP_STORE_CONNECT_API_KEY` : clé API ASC (contenu du fichier .p8)
- `APP_STORE_CONNECT_API_KEY_ID` : Key ID
- `APP_STORE_CONNECT_API_ISSUER_ID` : Issuer ID
- `IOS_CERT_BASE64` : certificat .p12 encodé base64 (si distribution manuelle)
- `IOS_CERT_PASSWORD` : mot de passe du .p12
- `IOS_PROVISION_BASE64` : profil de provisioning encodé base64

### Générer un certificat iOS (distribution ad-hoc/app store)
1. Créer une CSR sur macOS :
```bash
openssl req -new -newkey rsa:2048 -nodes -keyout ios_dist.key -out ios_dist.csr -subj "/CN=Your Name/OU=Your Org/O=Your Company/L=City/C=FR"
```
2. Soumettre la CSR sur Apple Developer, télécharger le certificat (.cer) et le convertir en .p12 :
```bash
openssl x509 -inform DER -in ios_dist.cer -out ios_dist.pem
openssl pkcs12 -export -inkey ios_dist.key -in ios_dist.pem -out ios_dist.p12
```

### Encoder en base64 (certificat et provisioning)
```bash
base64 -w0 ios_dist.p12 > ios_dist.p12.b64
base64 -w0 YourProfile.mobileprovision > profile.mobileprovision.b64
```

## Bonnes pratiques sécurité
- Ne jamais committer de secrets en clair dans le dépôt.
- Régénérer et révoquer les clés en cas de doute ou de fuite.
- Limiter les droits des comptes utilisés pour la CI (principe du moindre privilège).
- Restreindre l’accès aux secrets aux seuls environnements/branches nécessaires.
- Sur GitHub, activer la protection des environnements et les reviewers requis pour les déploiements.
- Conserver les clés privées hors des postes partagés et utiliser des coffres-forts de secrets (1Password, Vault, etc.).
- Vérifier régulièrement les journaux d’audit et les dates d’expiration des certificats.
