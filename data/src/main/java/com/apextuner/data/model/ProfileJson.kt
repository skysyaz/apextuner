package com.apextuner.data.model

import kotlinx.serialization.json.Json

/**
 * (De)serializes [Profile] graphs to/from compact JSON for Room storage and
 * file import/export. The format is stable across versions — unknown keys are
 * ignored so older builds can read newer exports without crashing.
 */
object ProfileSerializer {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        classDiscriminator = "__type"
    }

    fun encode(profile: Profile): String = json.encodeToString(Profile.serializer(), profile)

    fun decode(payload: String): Profile = json.decodeFromString(Profile.serializer(), payload)

    fun encodeList(profiles: List<Profile>): String =
        json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(Profile.serializer()),
            profiles
        )

    fun decodeList(payload: String): List<Profile> =
        json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(Profile.serializer()),
            payload
        )
}
