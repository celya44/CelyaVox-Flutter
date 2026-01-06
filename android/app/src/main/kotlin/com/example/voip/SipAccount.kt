package com.example.voip

/**
 * Immutable SIP account descriptor. Password is supplied at runtime from Flutter
 * and must never be persisted in plaintext on disk.
 */
 data class SipAccount(
     val username: String,
     val password: String,
     val domain: String,
     val proxy: String = "",
     val displayName: String = "",
     val transport: String = "UDP",
     val registrationTimeout: Int = 300
 )
