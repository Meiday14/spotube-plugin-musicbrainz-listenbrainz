package org.community.listenbrainz

import kotlinx.serialization.Serializable
import org.community.listenbrainz.host.*
import java.util.zip.ZipFile
import kotlin.test.*

/** Uses the enum decoders from Spotube v6.0.0, rather than checking JSON strings alone. */
@Serializable
internal data class HostPluginEntry(
    val name: String,
    val version: String,
    val apiVersion: String,
    val description: String,
    val author: String,
    @Serializable(with = LenientPluginCapabilityListSerializer::class)
    val capabilities: List<PluginCapability>,
    @Serializable(with = LenientPluginAbilityListSerializer::class)
    val abilities: List<PluginAbility>,
    val cookieDomains: List<String> = emptyList(),
    val contact: String,
    val repository: String,
    val bugs: String,
    val license: String,
)

internal fun installedManifest(archive: ZipFile): HostPluginEntry =
    json.decodeFromString<HostPluginEntry>(archive.getInputStream(archive.getEntry("plugin.json")).bufferedReader().use { it.readText() })

class HostManifestTest {
    @Test fun packagedPluginAppearsInHostMetadataSelectionAndHasHostPermissions() {
        ZipFile("build/distributions/plugin-production.smplug").use { archive ->
            val installed=installedManifest(archive)
            // Same filtering rule as PluginManager.filterPluginByType in Spotube 6.0.0.
            val dropdown=listOf(installed).filter { PluginAbility.METADATA in it.abilities }
            assertEquals(1,dropdown.size)
            assertEquals(PLUGIN_VERSION,installed.version)
            assertEquals("musicbrainz-listenbrainz-v6",installed.name)
            assertEquals("Community ListenBrainz port",installed.author)
            assertEquals(setOf(PluginAbility.METADATA,PluginAbility.SCROBBLE),installed.abilities.toSet())
            assertEquals(PluginCapability.entries.toSet(),installed.capabilities.toSet())
        }
    }
    @Test fun uppercaseValuesAreSilentlyDroppedByHostAndExplainVersion010Failure() {
        val abilities=json.decodeFromString(LenientPluginAbilityListSerializer,"""["METADATA","SCROBBLE"]""")
        val capabilities=json.decodeFromString(LenientPluginCapabilityListSerializer,"""["NETWORK_REQUESTS","PERSISTENT_STORAGE","WEBVIEW"]""")
        assertTrue(abilities.isEmpty())
        assertTrue(capabilities.isEmpty())
    }
}
