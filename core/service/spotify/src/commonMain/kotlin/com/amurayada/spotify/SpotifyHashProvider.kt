/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.amurayada.spotify

import java.util.concurrent.ConcurrentHashMap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Thread-safe provider for Spotify GQL persisted-query hashes.
 *
 * Initialized with hardcoded defaults that ship with each release.
 * The app module can update hashes at runtime from a remote JSON
 * registry via [updateHashes], enabling automatic recovery when
 * Spotify rotates hashes between app releases.
 *
 * Resolution order: remote/cached → hardcoded (always available).
 */
object SpotifyHashProvider {

    enum class HashSource { HARDCODED, CACHED, REMOTE }

    data class GqlHashEntry(
        val hash: String,
        val previousHash: String? = null,
        val source: HashSource = HashSource.HARDCODED,
    )

    private val hashes = ConcurrentHashMap<String, GqlHashEntry>()

    init {
        loadHardcodedDefaults()
    }

    private fun loadHardcodedDefaults() {
        val defaults = mapOf(
            "profileAttributes" to GqlHashEntry(
                hash = "08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0",
                previousHash = "53bcb064f6cd18c23f752bc324a791194d20df612d8e1239c735144ab0399ced",
                source = HashSource.HARDCODED
            ),
            "libraryV3" to GqlHashEntry(
                hash = "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455",
                previousHash = "973e511ca44261fda7eebac8b653155e7caee3675abb4fb110cc1b8c78b091c3",
                source = HashSource.HARDCODED
            ),
            "fetchPlaylist" to GqlHashEntry(
                hash = "86dde7b9d9356e2369414647cf6950cfed96e778e129cfdfc99aea6c1613b3b0",
                previousHash = "346811f856fb0b7e4f6c59f8ebea78dd081c6e2fb01b77c954b26259d5fc6763",
                source = HashSource.HARDCODED
            ),
            "fetchLibraryTracks" to GqlHashEntry(
                hash = "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240",
                source = HashSource.HARDCODED
            ),
            "searchDesktop" to GqlHashEntry(
                hash = "4801118d4a100f756e833d33984436a3899cff359c532f8fd3aaf174b60b3b49",
                source = HashSource.HARDCODED
            ),
            "queryArtistOverview" to GqlHashEntry(
                hash = "5b9e64f43843fa3a9b6a98543600299b0a2cbbbccfdcdcef2402eb9c1017ca4c",
                source = HashSource.HARDCODED
            ),
            "getAlbum" to GqlHashEntry(
                hash = "b9bfabef66ed756e5e13f68a942deb60bd4125ec1f1be8cc42769dc0259b4b10",
                source = HashSource.HARDCODED
            ),
            "queryWhatsNewFeed" to GqlHashEntry(
                hash = "3b53dede3c6054e8b7c962dd280eb6761c5d1c82b06b039f4110d76a62b4966b",
                source = HashSource.HARDCODED
            ),
            "addToPlaylist" to GqlHashEntry(
                hash = "47b2a1234b17748d332dd0431534f22450e9ecbb3d5ddcdacbd83368636a0990",
                source = HashSource.HARDCODED
            ),
            "removeFromPlaylist" to GqlHashEntry(
                hash = "47b2a1234b17748d332dd0431534f22450e9ecbb3d5ddcdacbd83368636a0990",
                source = HashSource.HARDCODED
            ),
            "moveItemsInPlaylist" to GqlHashEntry(
                hash = "47b2a1234b17748d332dd0431534f22450e9ecbb3d5ddcdacbd83368636a0990",
                source = HashSource.HARDCODED
            ),
            "editPlaylistAttributes" to GqlHashEntry(
                hash = "35a1a9ce3a2f4f8c32ee0e24c63c2069c6613c0a0b7e56d0e40dabe69a0b4f80",
                source = HashSource.HARDCODED
            ),
            "addToLibrary" to GqlHashEntry(
                hash = "7c5a69420e2bfae3da5cc4e14cbc8bb3f6090f80afc00ffc179177f19be3f33d",
                source = HashSource.HARDCODED
            ),
            "removeFromLibrary" to GqlHashEntry(
                hash = "7c5a69420e2bfae3da5cc4e14cbc8bb3f6090f80afc00ffc179177f19be3f33d",
                source = HashSource.HARDCODED
            ),
            "home" to GqlHashEntry(
                hash = "23e37f2e58d82d567f27080101d36609009d8c3676457b1086cb0acc55b72a5d",
                source = HashSource.HARDCODED
            ),
        )
        hashes.putAll(defaults)
    }

    /**
     * Returns the best available hash for [operationName].
     * Throws [IllegalStateException] if the operation is unknown
     * (should never happen — all operations have hardcoded defaults).
     */
    fun getHash(operationName: String): String =
        hashes[operationName]?.hash
            ?: error("No hash registered for GQL operation: $operationName")

    /**
     * Returns the previous hash for [operationName], if one was recorded
     * during a hash rotation. Used as a fallback when the current hash
     * returns a PersistedQueryNotFound error.
     */
    fun getPreviousHash(operationName: String): String? =
        hashes[operationName]?.previousHash

    /**
     * Bulk-update hashes from a remote or cached source.
     * Only overwrites entries whose remote hash differs from the
     * current hardcoded default, preserving the hardcoded value
     * as an implicit fallback (always reachable via [loadHardcodedDefaults]).
     */
    fun updateHashes(
        remoteHashes: Map<String, RemoteHashEntry>,
        source: HashSource,
    ): UpdateResult {
        var updated = 0
        var unchanged = 0
        remoteHashes.forEach { (op, remote) ->
            val current = hashes[op]
            if (current != null) {
                val hashChanged = current.hash != remote.hash
                if (hashChanged) updated++ else unchanged++
                hashes[op] = GqlHashEntry(
                    hash = remote.hash,
                    previousHash = remote.previousHash,
                    source = source,
                )
            }
        }
        return UpdateResult(updated = updated, unchanged = unchanged)
    }

    data class UpdateResult(val updated: Int, val unchanged: Int)

    fun getAll(): Map<String, GqlHashEntry> = hashes.toMap()

    suspend fun fetchRemoteHashes() {
        try {
            val client = HttpClient(com.amurayada.ktorext.getEngine())
            val responseText = client.get("https://francescograzioso.github.io/Meld/spotify-gql-hashes.json").bodyAsText()
            val root = Json.parseToJsonElement(responseText).jsonObject
            val operations = root["operations"]?.jsonObject
            if (operations != null) {
                val remoteMap = mutableMapOf<String, RemoteHashEntry>()
                for (key in operations.keys) {
                    val op = operations[key]?.jsonObject ?: continue
                    val hash = op["hash"]?.jsonPrimitive?.contentOrNull ?: continue
                    val prev = op["previous_hash"]?.jsonPrimitive?.contentOrNull
                    remoteMap[key] = RemoteHashEntry(hash = hash, previousHash = prev)
                }
                if (remoteMap.isNotEmpty()) {
                    updateHashes(remoteMap, HashSource.REMOTE)
                }
            }
            client.close()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    data class RemoteHashEntry(
        val hash: String,
        val previousHash: String? = null,
    )
}
