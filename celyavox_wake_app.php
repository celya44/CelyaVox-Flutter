#!/usr/bin/env php
<?php

function sendWakeUpNotification($token, $callerid) {
    
    $url = "https://celyavox.celya.fr/phone/wake_app.php";
    
    $ch = curl_init($url);
    curl_setopt($ch, CURLOPT_HTTPHEADER, [
        'Content-Type: application/json'
    ]);
    $message = [ 'server' => gethostname(), 'token' => $token, 'callerid' => $callerid ];
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


$bootstrap_settings['skip_astman'] = true;
$bootstrap_settings['include_compress'] = false;
include '/etc/freepbx.conf';

// --- MAIN ---
$token = $_SERVER["argv"][1];
$callerid = $_SERVER["argv"][2];
// Envoi de la notification
echo("On envoie la demande de notif par webservice au serveur CelyaVox");
$result = sendWakeUpNotification($token, $callerid);
if ( $result['success'] == 1 ){
    echo("Envoie OK");
}else{
    echo("Envoie echoue");
    if ( array_key_exists('error', $result['response']) ){
	echo("HTTP Code : ".$result['http_code']);
	echo("Raison d echec : ".$result['response']['error']);
    }else{
	echo("Raison d echec inconnue");
    }
}

?>
