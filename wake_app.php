#!/usr/bin/env php
<?php
/**
 * Script de réveil de l'application mobile CelyaVox
 * Usage: php wake_app.php <FCM_TOKEN>
 * 
 * Ce script envoie une notification silencieuse (data-only) pour réveiller l'app
 * en arrière-plan et forcer la reconnexion SIP.
 * 
 * Configuration requise:
 * 1. Télécharger le fichier Service Account JSON depuis Firebase Console
 * 2. Créer un fichier .env dans le même dossier avec:
 *    FCM_PROJECT_ID=votre-project-id
 *    FCM_SERVICE_ACCOUNT_JSON=/chemin/vers/service-account.json
 * 
 * Note: Ce fichier ne doit PAS être versionné dans Git!
 */

// Charger la configuration depuis .env
function loadEnv($filePath) {
    if (!file_exists($filePath)) {
        die("❌ Erreur: Fichier .env introuvable à $filePath\n");
    }
    
    $lines = file($filePath, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
    foreach ($lines as $line) {
        if (strpos(trim($line), '#') === 0) continue;
        
        list($name, $value) = explode('=', $line, 2);
        $name = trim($name);
        $value = trim($value);
        
        if (!array_key_exists($name, $_ENV)) {
            putenv("$name=$value");
            $_ENV[$name] = $value;
        }
    }
}

// Générer un JWT pour l'authentification OAuth2
function generateJWT($serviceAccount) {
    $now = time();
    $header = [
        'alg' => 'RS256',
        'typ' => 'JWT'
    ];
    
    $payload = [
        'iss' => $serviceAccount['client_email'],
        'sub' => $serviceAccount['client_email'],
        'aud' => 'https://oauth2.googleapis.com/token',
        'iat' => $now,
        'exp' => $now + 3600,
        'scope' => 'https://www.googleapis.com/auth/firebase.messaging'
    ];
    
    $base64UrlHeader = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode(json_encode($header)));
    $base64UrlPayload = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode(json_encode($payload)));
    
    $signature = '';
    $privateKey = openssl_pkey_get_private($serviceAccount['private_key']);
    openssl_sign($base64UrlHeader . '.' . $base64UrlPayload, $signature, $privateKey, OPENSSL_ALGO_SHA256);
    openssl_free_key($privateKey);
    
    $base64UrlSignature = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($signature));
    
    return $base64UrlHeader . '.' . $base64UrlPayload . '.' . $base64UrlSignature;
}

// Obtenir un access token OAuth2
function getAccessToken($serviceAccount) {
    $jwt = generateJWT($serviceAccount);
    
    $ch = curl_init('https://oauth2.googleapis.com/token');
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, http_build_query([
        'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        'assertion' => $jwt
    ]));
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_TIMEOUT, 10);
    
    $response = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    
    if ($httpCode !== 200) {
        return null;
    }
    
    $data = json_decode($response, true);
    return $data['access_token'] ?? null;
}

// Fonction d'envoi de notification FCM (API v1)
function sendWakeUpNotification($token) {
    $serviceAccountPath = getenv('FCM_SERVICE_ACCOUNT_JSON');
    $projectId = getenv('FCM_PROJECT_ID');
    
    if (empty($serviceAccountPath) || empty($projectId)) {
        return [
            'success' => false,
            'error' => 'FCM_SERVICE_ACCOUNT_JSON ou FCM_PROJECT_ID non configuré dans .env'
        ];
    }
    
    if (!file_exists($serviceAccountPath)) {
        return [
            'success' => false,
            'error' => "Fichier service account introuvable: $serviceAccountPath"
        ];
    }
    
    // Charger le service account
    $serviceAccountJson = file_get_contents($serviceAccountPath);
    $serviceAccount = json_decode($serviceAccountJson, true);
    
    if (!$serviceAccount) {
        return [
            'success' => false,
            'error' => 'Fichier service account JSON invalide'
        ];
    }
    
    // Obtenir le token OAuth2
    $accessToken = getAccessToken($serviceAccount);
    
    if (!$accessToken) {
        return [
            'success' => false,
            'error' => 'Impossible d\'obtenir le token OAuth2'
        ];
    }
    
    // Message FCM v1 DATA-ONLY (sans notification) pour forcer l'appel de onMessageReceived()
    $message = [
        'message' => [
            'token' => $token,
            // PAS de clé 'notification' ici - seulement 'data'
            // Cela force Firebase à appeler onMessageReceived() même si l'app est fermée
            'data' => [
                'type' => 'wake_up',
                'forceOpen' => 'true',
                'action' => 'open_app',
                'timestamp' => (string)time(),
                'title' => 'Réveil CelyaVox',
                'body' => 'Ouverture immédiate de l’app'
            ],
            'android' => [
                'priority' => 'high',
                'ttl' => '60s'
            ],
            'apns' => [
                'headers' => [
                    'apns-priority' => '10',
                    'apns-push-type' => 'background'
                ],
                'payload' => [
                    'aps' => [
                        'content-available' => 1
                    ]
                ]
            ]
        ]
    ];
    
    $url = "https://fcm.googleapis.com/v1/projects/{$projectId}/messages:send";
    
    $ch = curl_init($url);
    curl_setopt($ch, CURLOPT_HTTPHEADER, [
        'Authorization: Bearer ' . $accessToken,
        'Content-Type: application/json'
    ]);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($message));
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_TIMEOUT, 10);
    
    $response = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $error = curl_error($ch);
    curl_close($ch);
    
    if ($error) {
        return [
            'success' => false,
            'error' => "Erreur cURL: $error"
        ];
    }
    
    $responseData = json_decode($response, true);
    
    return [
        'success' => ($httpCode == 200),
        'http_code' => $httpCode,
        'response' => $responseData,
        'raw_response' => $response
    ];
}

// Validation du token
function isValidToken($token) {
    // Les tokens FCM font généralement plus de 100 caractères
    return !empty($token) && strlen($token) > 50;
}

// Récupérer le token depuis la BDD
function getFcmTokenFromExtension($extension) {
    $host = getenv('DB_HOST');
    $dbname = getenv('DB_NAME');
    $user = getenv('DB_USER');
    $pass = getenv('DB_PASS');

    if (!$host || !$dbname || !$user || !$pass) {
        die("❌ Erreur: Configuration base de données incomplète dans .env (DB_HOST, DB_NAME, DB_USER, DB_PASS)\n");
    }

    try {
        $dsn = "mysql:host=$host;dbname=$dbname;charset=utf8mb4";
        $pdo = new PDO($dsn, $user, $pass, [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC
        ]);

        $stmt = $pdo->prepare("SELECT token_fcm FROM token_fcm WHERE extension = ?");
        $stmt->execute([$extension]);
        $result = $stmt->fetch();

        if ($result) {
            return $result['token_fcm'];
        } else {
            return null;
        }
    } catch (PDOException $e) {
        die("❌ Erreur connexion BDD: " . $e->getMessage() . "\n");
    }
}

// Vérifie si un contact PJSIP est enregistré
function isContactRegistered($contactAor) {
    $output = shell_exec("asterisk -rx 'pjsip show contacts'");

    if ($output === null) {
        return false;
    }

    return (bool)preg_match('/Contact:\\s*' . preg_quote($contactAor, '/') . '\\b/', $output);
}

// Attend qu'un contact soit enregistré avec tentatives limitées
function waitForRegisteredContact($contactAor, $maxAttempts = 10, $sleepMicroseconds = 500000) {
    for ($attempt = 0; $attempt < $maxAttempts; $attempt++) {
        if (isContactRegistered($contactAor)) {
            return true;
        }

        usleep($sleepMicroseconds);
    }

    return false;
}

// --- MAIN ---

// Chargement de la configuration
$envPath = __DIR__ . '/.env';
loadEnv($envPath);

// Vérification des arguments
if ($argc < 2) {
    echo "❌ Usage: php " . basename(__FILE__) . " <EXTENSION>\n\n";
    echo "Exemple:\n";
    echo "  php wake_app.php 1001\n\n";
    echo "Configuration:\n";
    echo "  1. Téléchargez le Service Account JSON depuis Firebase\n";
    echo "  2. Créez un fichier .env avec:\n";
    echo "     FCM_PROJECT_ID=votre-project-id\n";
    echo "     FCM_SERVICE_ACCOUNT_JSON=/chemin/vers/service-account.json\n";
    echo "     DB_HOST=localhost\n";
    echo "     DB_NAME=ma_base\n";
    echo "     DB_USER=mon_user\n";
    echo "     DB_PASS=mon_pass\n\n";
    exit(1);
}

$extension = $argv[1];
echo "🔍 Recherche du token pour l'extension: $extension...\n";

$token = getFcmTokenFromExtension($extension);

if (!$token) {
    echo "❌ Erreur: Aucun token FCM trouvé pour l'extension $extension\n";
    exit(1);
}

// Validation du token
if (!isValidToken($token)) {
    echo "❌ Erreur: Le token FCM trouvé en base semble invalide (trop court)\n";
    exit(1);
}

echo "📱 Réveil de l'application mobile...\n";
echo "Token: " . substr($token, 0, 20) . "...\n\n";

// Envoi de la notification
$result = sendWakeUpNotification($token);

if ($result['success']) {
    echo "✅ Notification envoyée avec succès!\n\n";
    
    if (isset($result['response']['name'])) {
        echo "📊 Résultat FCM:\n";
        echo "   - Message: " . $result['response']['name'] . "\n";
    }

    $contactAor = $extension;
    echo "🔁 Vérification de l'enregistrement PJSIP pour $contactAor...\n";

    if (waitForRegisteredContact($contactAor)) {
        echo "📞 Extension $contactAor enregistrée, lancement de l'appel...\n";
        shell_exec("asterisk -rx 'channel originate PJSIP/$contactAor application Playback demo-congrats'");
    } else {
        echo "⚠️ Extension $contactAor non enregistrée après plusieurs tentatives, appel annulé.\n";
    }
    exit(0);
} else {
    echo "❌ Échec de l'envoi\n\n";
    echo "Erreur: " . ($result['error'] ?? 'Inconnue') . "\n";
    
    if (isset($result['http_code'])) {
        echo "Code HTTP: " . $result['http_code'] . "\n";
    }
    
    if (isset($result['raw_response'])) {
        echo "\nRéponse brute:\n" . $result['raw_response'] . "\n";
    }
    
    exit(1);
}
