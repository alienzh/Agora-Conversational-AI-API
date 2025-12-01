package io.agora.convoai.example

object KeyCenter {
    // Load values from BuildConfig, which are populated from env.properties at build time
    val AGORA_APP_ID: String = BuildConfig.AGORA_APP_ID
    val AGORA_APP_CERTIFICATE: String = BuildConfig.AGORA_APP_CERTIFICATE
    val REST_KEY: String = BuildConfig.REST_KEY
    val REST_SECRET: String = BuildConfig.REST_SECRET
    val PIPELINE_ID: String = BuildConfig.PIPELINE_ID
}