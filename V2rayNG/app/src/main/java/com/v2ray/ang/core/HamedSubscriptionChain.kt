package com.v2ray.ang.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherTor
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.HamedSubscriptionManager
import com.v2ray.ang.ui.main.HamedChainType
import com.v2ray.ang.util.JsonUtil
import java.io.File

/**
 * Helpers for chaining built-in subscriptions (Serverless, MITM-DomainFronting, and Patterniha)
 * with Psiphon or Tor via the bundled `libaether` core.
 */
object HamedSubscriptionChain {

    /** Selection for a profile: desired chain carrier and Psiphon exit region code (empty = auto). */
    data class ChainSelection(
        val chain: HamedChainType,
        val psiphonRegion: String = "",
    )

    private val CLIENT_INBOUND_PROTOCOLS = setOf("tun", "mixed", "socks", "http")
    private val MITM_REDIRECT_OUTBOUNDS = setOf("redirect-out-h11", "redirect-out-h211")
    private val PREFERRED_TLS_BYPASS_OUTBOUNDS = listOf(
        "tcp-fragment-tls",
        "tcp-oob",
        "tcp-fragment-tls-try",
        "tcp-oob-try",
        "tcp-fragment-tls-cloudflare",
        "tcp-oob-cloudflare",
        "tcp-direct",
        "direct",
    )

    /**
     * Resolves the active chain & region for [profile] if it belongs to one of Hamed's synced
     * subscriptions and has Psiphon or Tor chaining selected; `null` otherwise.
     */
    fun selectionForProfile(profile: ProfileItem?): ChainSelection? {
        if (profile == null || profile.configType == EConfigType.AETHER) return null
        if (profile.subscriptionId.isBlank()) return null
        return runCatching { HamedSubscriptionManager.getChainSelectionForProfile(profile) }.getOrNull()
    }

    /**
     * Builds a synthetic [EConfigType.AETHER] profile configured strictly in `--psiphon-only` or
     * `--tor-only` carrier mode (no WARP/MASQUE outer tunnel), ready to act as the entry carrier hop
     * in front of a normal node or a custom config.
     */
    fun createCarrierProfile(chain: HamedChainType, psiphonRegion: String): ProfileItem? {
        if (chain == HamedChainType.DEFAULT) return null
        return ProfileItem.create(EConfigType.AETHER).apply {
            remarks = "Hamed Carrier (${chain.displayName})"
            aetherProtocol = AetherProtocol.WIREGUARD.type
            aetherPsiphon = if (chain == HamedChainType.PSIPHON) AetherPsiphon.ONLY.type else AetherPsiphon.OFF.type
            aetherPsiphonRegion = if (chain == HamedChainType.PSIPHON) {
                psiphonRegion.trim().uppercase().ifEmpty { null }
            } else {
                null
            }
            aetherTor = if (chain == HamedChainType.TOR) AetherTor.ONLY.type else AetherTor.OFF.type
        }
    }

    /**
     * Builds the [AetherCore] command (`--psiphon-only` or `--tor-only`) configured to dial out
     * through Xray's `secondary-socks` inbound at [secondaryPort].
     */
    fun createCarrierCore(chain: HamedChainType, psiphonRegion: String, secondaryPort: Int): AetherCore? {
        val profile = createCarrierProfile(chain, psiphonRegion) ?: return null
        return AetherCore.of(profile).through(secondaryPort)
    }

    /**
     * Outcome of injecting Psiphon or Tor carrier chaining into a parsed custom Xray JSON config
     * (such as Serverless or MITM-DomainFronting).
     */
    sealed interface CustomInjectResult {
        data class Injected(val core: AetherCore) : CustomInjectResult
        data object PortTaken : CustomInjectResult
        data object Skipped : CustomInjectResult
    }

    /**
     * Routes all user client traffic (`tun`, `mixed-in`, etc.) in a custom JSON config through a
     * local [AetherCore] carrier running `--psiphon-only` or `--tor-only`, while [AetherCore] dials
     * `--upstream socks5://127.0.0.1:[secondaryPort]` (`secondary-socks`), so the carrier itself
     * traverses the custom config's fragment / OOB / anti-censorship transport rules.
     */
    fun injectCustomChain(
        json: JsonObject,
        chain: HamedChainType,
        psiphonRegion: String,
        secondaryPort: Int,
    ): CustomInjectResult {
        if (chain == HamedChainType.DEFAULT) return CustomInjectResult.Skipped
        if (json.has(AetherDependency.COMMAND_KEY)) return CustomInjectResult.Skipped

        val inbounds = json.get("inbounds")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: JsonArray().also { json.add("inbounds", it) }

        val portTaken = inbounds.any { elem ->
            elem.isJsonObject && elem.asJsonObject.get("port")?.takeIf { it.isJsonPrimitive }?.asIntOrNull() == secondaryPort
        }
        if (portTaken) return CustomInjectResult.PortTaken

        val core = createCarrierCore(chain, psiphonRegion, secondaryPort) ?: return CustomInjectResult.Skipped
        val outbounds = json.get("outbounds")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: JsonArray().also { json.add("outbounds", it) }

        val outboundTags = outbounds.mapNotNullTo(HashSet()) { elem ->
            elem.takeIf { it.isJsonObject }?.asJsonObject?.stringOrNull("tag")
        }

        // Collect all user-facing client inbound tags before adding secondary-socks.
        val clientInboundTags = mutableListOf<String>()
        inbounds.forEach { elem ->
            val obj = elem.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val tag = obj.stringOrNull("tag") ?: return@forEach
            val protocol = obj.stringOrNull("protocol")?.lowercase()
            if (tag != AppConfig.TAG_SECONDARY_SOCKS &&
                !tag.startsWith("tls-decrypt-") &&
                protocol in CLIENT_INBOUND_PROTOCOLS
            ) {
                clientInboundTags.add(tag)
            }
        }
        if (clientInboundTags.isEmpty()) return CustomInjectResult.Skipped

        // 1. Add carrier SOCKS outbound -> libaether socksPort
        if (AppConfig.TAG_HAMED_AETHER_CARRIER !in outboundTags) {
            outbounds.add(
                JsonObject().apply {
                    addProperty("tag", AppConfig.TAG_HAMED_AETHER_CARRIER)
                    addProperty("protocol", "socks")
                    add(
                        "settings",
                        JsonObject().apply {
                            addProperty("address", AppConfig.LOOPBACK)
                            addProperty("port", core.port)
                        },
                    )
                },
            )
        }

        // 2. Add secondary-socks inbound for libaether --upstream
        inbounds.add(
            JsonObject().apply {
                addProperty("tag", AppConfig.TAG_SECONDARY_SOCKS)
                addProperty("port", secondaryPort)
                addProperty("listen", AppConfig.LOOPBACK)
                addProperty("protocol", "mixed")
                add(
                    "settings",
                    JsonObject().apply {
                        addProperty("udp", true)
                    },
                )
                add(
                    "sniffing",
                    JsonObject().apply {
                        addProperty("enabled", true)
                        add(
                            "destOverride",
                            JsonArray().apply {
                                add("http")
                                add("tls")
                            },
                        )
                        addProperty("routeOnly", true)
                    },
                )
            },
        )

        // 3. Splice carrier and MITM-bypass routing rules cleanly
        val routing = json.get("routing")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: JsonObject().apply { addProperty("domainStrategy", "IPIfNonMatch") }.also { json.add("routing", it) }
        val existingRules = routing.get("rules")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: JsonArray().also { routing.add("rules", it) }

        val updatedRules = buildChainedCustomRules(
            existingRules = existingRules,
            clientInboundTags = clientInboundTags,
            outboundTags = outboundTags,
        )
        routing.add("rules", updatedRules)
        json.addProperty(AetherDependency.COMMAND_KEY, core.command)
        return CustomInjectResult.Injected(core)
    }

    /**
     * Rewrites relative MITM certificate filenames (`mycert.crt` and `mycert.key`) inside custom
     * inbound TLS settings to absolute paths inside [assetDirPath] when those files exist there, so
     * Xray loads them even if cwd / cert env differs.
     */
    fun resolveCustomCertPaths(json: JsonObject, assetDirPath: String) {
        if (assetDirPath.isBlank()) return
        val certFile = File(assetDirPath, AppConfig.MITM_CERT_FILE)
        val keyFile = File(assetDirPath, AppConfig.MITM_KEY_FILE)
        if (!certFile.isFile || !keyFile.isFile) return

        val inbounds = json.get("inbounds")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        inbounds.forEach { elem ->
            val certs = elem.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("streamSettings")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("tlsSettings")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("certificates")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return@forEach
            certs.forEach { certElem ->
                val certObj = certElem.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                if (certObj.stringOrNull("certificateFile") == AppConfig.MITM_CERT_FILE) {
                    certObj.addProperty("certificateFile", certFile.absolutePath)
                }
                if (certObj.stringOrNull("keyFile") == AppConfig.MITM_KEY_FILE) {
                    certObj.addProperty("keyFile", keyFile.absolutePath)
                }
            }
        }
    }

    private fun buildChainedCustomRules(
        existingRules: JsonArray,
        clientInboundTags: List<String>,
        outboundTags: Set<String>,
    ): JsonArray {
        val result = JsonArray()
        val hasBlock = "block" in outboundTags
        val directTag = when {
            "tcp-direct" in outboundTags -> "tcp-direct"
            "direct" in outboundTags -> "direct"
            else -> null
        }
        val tlsBypassTag = PREFERRED_TLS_BYPASS_OUTBOUNDS.firstOrNull { it in outboundTags }

        var carrierInserted = false
        var mitmBypassInserted = false

        for (elem in existingRules) {
            val obj = elem.takeIf { it.isJsonObject }?.asJsonObject
            if (obj != null && !carrierInserted && !isEarlyControlRule(obj)) {
                appendCarrierPreludeRules(result, clientInboundTags, directTag, hasBlock)
                result.add(makeInboundRule(clientInboundTags, AppConfig.TAG_HAMED_AETHER_CARRIER))
                carrierInserted = true
            }
            if (obj != null && !mitmBypassInserted && obj.stringOrNull("outboundTag") in MITM_REDIRECT_OUTBOUNDS && tlsBypassTag != null) {
                // Prevent Psiphon/Tor's second-hop TLS 443 sockets on secondary-socks from entering MITM decryption.
                result.add(
                    makeInboundRule(
                        inboundTags = listOf(AppConfig.TAG_SECONDARY_SOCKS),
                        outboundTag = tlsBypassTag,
                        network = "tcp",
                        port = "443",
                    ),
                )
                mitmBypassInserted = true
            }
            result.add(elem)
        }

        if (!carrierInserted) {
            appendCarrierPreludeRules(result, clientInboundTags, directTag, hasBlock)
            result.add(makeInboundRule(clientInboundTags, AppConfig.TAG_HAMED_AETHER_CARRIER))
        }
        return result
    }

    /**
     * Rules preserved verbatim before the carrier intercept:
     * - internal DNS routing (`domestic-dns`, `no-filter-dns`, `dns-out`)
     * - TLS MITM decrypter follow-up rules (`tls-decrypt-*`)
     * - non-catch-all `block` rules (ads, DoH, port 853, QUIC/UDP 443, bogon CIDRs)
     */
    private fun isEarlyControlRule(rule: JsonObject): Boolean {
        val inboundTags = rule.get("inboundTag")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { p -> p.isJsonPrimitive }?.asString }
            .orEmpty()
        if (inboundTags.any { it == "domestic-dns" || it == "no-filter-dns" || it.startsWith("tls-decrypt-") }) {
            return true
        }
        val outboundTag = rule.stringOrNull("outboundTag")
        if (outboundTag == "dns-out") return true
        if (outboundTag == "block") {
            val port = rule.stringOrNull("port")
            val isCatchAllBlock = port == "0-65535" &&
                !rule.has("domain") && !rule.has("ip") && !rule.has("protocol")
            val isQuicBlock = rule.get("protocol")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.any { it.isJsonPrimitive && it.asString == "quic" } == true
            val isUdp443Block = rule.stringOrNull("network") == "udp" && port == "443"
            // Stop before the general data-plane QUIC/UDP443 blocks so we insert local rules in consistent order.
            return !isCatchAllBlock && !isQuicBlock && !isUdp443Block
        }
        return false
    }

    private fun appendCarrierPreludeRules(
        target: JsonArray,
        clientInboundTags: List<String>,
        directTag: String?,
        hasBlock: Boolean,
    ) {
        if (hasBlock) {
            // Keep browsers off QUIC / UDP-443 so HTTPS traffic reliably uses TCP through Psiphon/Tor.
            target.add(
                makeInboundRule(
                    inboundTags = clientInboundTags,
                    outboundTag = "block",
                    network = "udp",
                    protocol = listOf("quic"),
                ),
            )
            target.add(
                makeInboundRule(
                    inboundTags = clientInboundTags,
                    outboundTag = "block",
                    network = "udp",
                    port = "443",
                ),
            )
        }
        if (directTag != null) {
            // Preserve local/Iranian direct routing while keeping foreign IP-check, Cloudflare & AI routed via carrier.
            target.add(
                makeInboundRule(
                    inboundTags = clientInboundTags,
                    outboundTag = directTag,
                    domain = listOf("domain:ir", "geosite:private", "geosite:category-ir"),
                ),
            )
            target.add(
                makeInboundRule(
                    inboundTags = clientInboundTags,
                    outboundTag = directTag,
                    ip = listOf("geoip:private", "geoip:ir"),
                ),
            )
        }
    }

    private fun makeInboundRule(
        inboundTags: List<String>,
        outboundTag: String,
        network: String? = null,
        port: String? = null,
        domain: List<String>? = null,
        ip: List<String>? = null,
        protocol: List<String>? = null,
    ): JsonObject = JsonObject().apply {
        addProperty("type", "field")
        add("inboundTag", JsonArray().apply { inboundTags.forEach(::add) })
        network?.let { addProperty("network", it) }
        port?.let { addProperty("port", it) }
        domain?.let { add("domain", JsonArray().apply { it.forEach(::add) }) }
        ip?.let { add("ip", JsonArray().apply { it.forEach(::add) }) }
        protocol?.let { add("protocol", JsonArray().apply { it.forEach(::add) }) }
        addProperty("outboundTag", outboundTag)
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun com.google.gson.JsonElement.asIntOrNull(): Int? =
        if (isJsonPrimitive) runCatching { asInt }.getOrNull() else null
}
