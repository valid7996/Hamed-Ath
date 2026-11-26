package com.v2ray.ang.core

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherTor
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.HamedDashboardSource
import com.v2ray.ang.handler.HamedMitmCertGenerator
import com.v2ray.ang.handler.HamedSubServerSummary
import com.v2ray.ang.handler.HamedSubscriptionManager
import com.v2ray.ang.ui.main.HamedChainType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class HamedSubscriptionAndChainTest {

    private val serverlessSampleJson = """
        {
          "remarks": "Serverless-v65-fragA",
          "inbounds": [
            {"tag": "mixed-in", "port": 10808, "protocol": "mixed"},
            {"tag": "tun", "port": 0, "protocol": "tun"}
          ],
          "outbounds": [
            {"tag": "block", "protocol": "block"},
            {"tag": "tcp-direct", "protocol": "direct"},
            {"tag": "udp-direct", "protocol": "direct"},
            {"tag": "dns-out", "protocol": "dns"},
            {"tag": "tcp-fragment-tls-try", "protocol": "direct"},
            {"tag": "tcp-fragment-tls", "protocol": "direct"},
            {"tag": "tls-repack-dns", "protocol": "direct"}
          ],
          "routing": {
            "domainStrategy": "IPIfNonMatch",
            "rules": [
              {"outboundTag": "tcp-direct", "inboundTag": ["domestic-dns"], "network": "tcp"},
              {"outboundTag": "tls-repack-dns", "inboundTag": ["no-filter-dns"]},
              {"outboundTag": "dns-out", "port": "53"},
              {"outboundTag": "block", "domain": ["geosite:category-ads-all"]},
              {"outboundTag": "block", "port": "853"},
              {"outboundTag": "tcp-direct", "network": "tcp", "domain": ["domain:ir", "geosite:category-ip-geo-detect"]},
              {"outboundTag": "block", "network": "udp", "protocol": ["quic"], "ip": ["0.0.0.0/0"]},
              {"outboundTag": "udp-direct", "network": "udp", "ip": ["0.0.0.0/0"]},
              {"outboundTag": "tcp-fragment-tls-try", "network": "tcp", "port": "443", "ip": ["0.0.0.0/0"]},
              {"outboundTag": "tcp-direct", "network": "tcp", "ip": ["0.0.0.0/0"]},
              {"outboundTag": "block", "port": "0-65535"}
            ]
          }
        }
    """.trimIndent()

    private val mitmSampleJson = """
        {
          "remarks": "MITM-DomainFronting-v24-fragA",
          "inbounds": [
            {"tag": "mixed-in", "port": 10808, "protocol": "mixed"},
            {"tag": "tun", "port": 0, "protocol": "tun"},
            {
              "tag": "tls-decrypt-h11",
              "port": 11666,
              "protocol": "tunnel",
              "streamSettings": {
                "security": "tls",
                "tlsSettings": {
                  "certificates": [
                    {"usage": "issue", "certificateFile": "mycert.crt", "keyFile": "mycert.key"}
                  ]
                }
              }
            }
          ],
          "outbounds": [
            {"tag": "block", "protocol": "block"},
            {"tag": "tcp-direct", "protocol": "direct"},
            {"tag": "udp-direct", "protocol": "direct"},
            {"tag": "dns-out", "protocol": "dns"},
            {"tag": "redirect-out-h11", "protocol": "direct"},
            {"tag": "tcp-fragment-tls", "protocol": "direct"},
            {"tag": "tls-repack-dns", "protocol": "direct"}
          ],
          "routing": {
            "domainStrategy": "IPIfNonMatch",
            "rules": [
              {"outboundTag": "tls-repack-dns", "inboundTag": ["no-filter-dns"]},
              {"outboundTag": "dns-out", "port": "53"},
              {"outboundTag": "tls-repack-dns", "inboundTag": ["tls-decrypt-h11"]},
              {"outboundTag": "tcp-direct", "network": "tcp", "domain": ["domain:ir", "geosite:category-ip-geo-detect"]},
              {"outboundTag": "udp-direct", "network": "udp", "ip": ["0.0.0.0/0"]},
              {"outboundTag": "redirect-out-h11", "network": "tcp", "port": "443", "ip": ["0.0.0.0/0"]},
              {"outboundTag": "tcp-direct", "network": "tcp", "ip": ["0.0.0.0/0"]},
              {"outboundTag": "block", "port": "0-65535"}
            ]
          }
        }
    """.trimIndent()

    @Test
    fun builtInSubscriptionsContainExactUserUrls() {
        assertEquals(
            "https://raw.githubusercontent.com/patterniha/Serverless-for-Iran/refs/heads/main/Subscription/Serverless-for-Iran.json#Patterniha-S",
            HamedDashboardSource.SERVERLESS.subUrl,
        )
        assertEquals(
            "https://raw.githubusercontent.com/patterniha/MITM-DomainFronting/refs/heads/main/Subscription/MITM-DomainFronting.json#Patterniha-M",
            HamedDashboardSource.MITM.subUrl,
        )
        assertEquals(
            "https://raw.githubusercontent.com/patterniha/Free-Configs/main/configs.txt",
            HamedDashboardSource.PATTERNIHA.subUrl,
        )
        assertEquals(3, HamedDashboardSource.subscriptionSources.size)
    }

    @Test
    fun selfSignedMitmCaGeneratesValidX509CertificateAndPkcs8Key() {
        val (certPem, keyPem) = HamedMitmCertGenerator.generateSelfSignedCaPem(
            encodeBase64 = { Base64.getEncoder().encodeToString(it) },
        )
        assertTrue(certPem.startsWith("-----BEGIN CERTIFICATE-----\n"))
        assertTrue(certPem.trimEnd().endsWith("-----END CERTIFICATE-----"))
        assertTrue(keyPem.startsWith("-----BEGIN PRIVATE KEY-----\n"))
        assertTrue(keyPem.trimEnd().endsWith("-----END PRIVATE KEY-----"))

        val certDer = Base64.getDecoder().decode(
            certPem.lineSequence().filter { !it.startsWith("-----") }.joinToString(""),
        )
        val x509 = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
        x509.verify(x509.publicKey)
        assertTrue(x509.basicConstraints >= 0)
        assertTrue(x509.keyUsage[5]) // keyCertSign

        val keyDer = Base64.getDecoder().decode(
            keyPem.lineSequence().filter { !it.startsWith("-----") }.joinToString(""),
        )
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyDer))
        assertNotNull(privateKey)
    }

    @Test
    fun serverlessDirectSkipsCarrierInjection() {
        val json = JsonParser.parseString(serverlessSampleJson).asJsonObject
        val result = HamedSubscriptionChain.injectCustomChain(
            json = json,
            chain = HamedChainType.DEFAULT,
            psiphonRegion = "DE",
            secondaryPort = AetherCoreManager.secondarySocksPort,
        )
        assertEquals(HamedSubscriptionChain.CustomInjectResult.Skipped, result)
        assertFalse(json.has(AetherDependency.COMMAND_KEY))
    }

    @Test
    fun serverlessChainsWithPsiphonAndPreservesDnsBeforeCarrierRule() {
        val json = JsonParser.parseString(serverlessSampleJson).asJsonObject
        val result = HamedSubscriptionChain.injectCustomChain(
            json = json,
            chain = HamedChainType.PSIPHON,
            psiphonRegion = "de",
            secondaryPort = AetherCoreManager.secondarySocksPort,
        )
        assertTrue(result is HamedSubscriptionChain.CustomInjectResult.Injected)
        val core = (result as HamedSubscriptionChain.CustomInjectResult.Injected).core
        assertTrue("--psiphon-only" in core.arguments)
        assertTrue("--psiphon-region" in core.arguments)
        assertTrue("DE" in core.arguments)
        assertEquals(
            "socks5://127.0.0.1:${AetherCoreManager.secondarySocksPort}",
            core.arguments[core.arguments.indexOf("--upstream") + 1],
        )

        val inbounds = json.getAsJsonArray("inbounds").map { it.asJsonObject }
        val secondary = inbounds.first { it.get("tag").asString == AppConfig.TAG_SECONDARY_SOCKS }
        assertEquals(AetherCoreManager.secondarySocksPort, secondary.get("port").asInt)
        assertTrue(secondary.getAsJsonObject("sniffing").get("routeOnly").asBoolean)

        val outbounds = json.getAsJsonArray("outbounds").map { it.asJsonObject }
        val carrier = outbounds.first { it.get("tag").asString == AppConfig.TAG_HAMED_AETHER_CARRIER }
        assertEquals("socks", carrier.get("protocol").asString)
        assertEquals(core.port, carrier.getAsJsonObject("settings").get("port").asInt)

        val rules = json.getAsJsonObject("routing").getAsJsonArray("rules").map { it.asJsonObject }
        val dnsIdx = rules.indexOfFirst { it.get("outboundTag")?.asString == "dns-out" }
        val carrierIdx = rules.indexOfFirst { it.get("outboundTag")?.asString == AppConfig.TAG_HAMED_AETHER_CARRIER }
        val ipGeoIdx = rules.indexOfFirst {
            it.get("domain")?.asJsonArray?.any { d -> d.asString == "geosite:category-ip-geo-detect" } == true
        }
        assertTrue(dnsIdx in 0 until carrierIdx)
        // Foreign IP detection must hit Psiphon carrier rather than leaking through direct rule.
        assertTrue(carrierIdx in 1 until ipGeoIdx)
    }

    @Test
    fun serverlessChainsWithTor() {
        val json = JsonParser.parseString(serverlessSampleJson).asJsonObject
        val result = HamedSubscriptionChain.injectCustomChain(
            json = json,
            chain = HamedChainType.TOR,
            psiphonRegion = "",
            secondaryPort = AetherCoreManager.secondarySocksPort,
        )
        assertTrue(result is HamedSubscriptionChain.CustomInjectResult.Injected)
        val core = (result as HamedSubscriptionChain.CustomInjectResult.Injected).core
        assertTrue("--tor-only" in core.arguments)
        assertFalse("--psiphon-only" in core.arguments)
        assertTrue("--upstream" in core.arguments)
    }

    @Test
    fun mitmChainsWithPsiphonAndBypassesTlsDecrypterForSecondarySocks() {
        val json = JsonParser.parseString(mitmSampleJson).asJsonObject
        val result = HamedSubscriptionChain.injectCustomChain(
            json = json,
            chain = HamedChainType.PSIPHON,
            psiphonRegion = "US",
            secondaryPort = AetherCoreManager.secondarySocksPort,
        )
        assertTrue(result is HamedSubscriptionChain.CustomInjectResult.Injected)

        val rules = json.getAsJsonObject("routing").getAsJsonArray("rules").map { it.asJsonObject }
        val mitmRedirectIdx = rules.indexOfFirst { it.get("outboundTag")?.asString == "redirect-out-h11" }
        val secondaryBypassIdx = rules.indexOfFirst {
            it.get("outboundTag")?.asString == "tcp-fragment-tls" &&
                it.get("inboundTag")?.asJsonArray?.any { tag -> tag.asString == AppConfig.TAG_SECONDARY_SOCKS } == true
        }
        assertTrue(secondaryBypassIdx >= 0)
        assertTrue(secondaryBypassIdx < mitmRedirectIdx)
    }

    @Test
    fun resolveCustomCertPathsRewritesWhenFilesExist(@TempDir tempDir: File) {
        val certFile = File(tempDir, AppConfig.MITM_CERT_FILE).apply { writeText("CERT") }
        val keyFile = File(tempDir, AppConfig.MITM_KEY_FILE).apply { writeText("KEY") }
        val json = JsonParser.parseString(mitmSampleJson).asJsonObject

        HamedSubscriptionChain.resolveCustomCertPaths(json, tempDir.absolutePath)

        val certs = json.getAsJsonArray("inbounds")
            .first { it.asJsonObject.get("tag").asString == "tls-decrypt-h11" }
            .asJsonObject
            .getAsJsonObject("streamSettings")
            .getAsJsonObject("tlsSettings")
            .getAsJsonArray("certificates")
            .first().asJsonObject
        assertEquals(certFile.absolutePath, certs.get("certificateFile").asString)
        assertEquals(keyFile.absolutePath, certs.get("keyFile").asString)
    }

    @Test
    fun carrierProfileSupportsBothPsiphonOnlyAndTorOnly() {
        assertNull(HamedSubscriptionChain.createCarrierProfile(HamedChainType.DEFAULT, "US"))

        val psiphonCarrier: ProfileItem = HamedSubscriptionChain.createCarrierProfile(HamedChainType.PSIPHON, "nl")!!
        assertEquals(EConfigType.AETHER, psiphonCarrier.configType)
        assertEquals(AetherPsiphon.ONLY.type, psiphonCarrier.aetherPsiphon)
        assertEquals(AetherTor.OFF.type, psiphonCarrier.aetherTor)
        assertEquals("NL", psiphonCarrier.aetherPsiphonRegion)

        val torCarrier: ProfileItem = HamedSubscriptionChain.createCarrierProfile(HamedChainType.TOR, "US")!!
        assertEquals(EConfigType.AETHER, torCarrier.configType)
        assertEquals(AetherTor.ONLY.type, torCarrier.aetherTor)
        assertEquals(AetherPsiphon.OFF.type, torCarrier.aetherPsiphon)
        assertNull(torCarrier.aetherPsiphonRegion)
    }

    @Test
    fun robinHoodDefaultsToAutoModeAndOrdersCandidatesProperly() {
        assertEquals("RobinHood", HamedDashboardSource.PATTERNIHA.subRemark)
        assertTrue(HamedSubscriptionManager.defaultAutoFor(HamedDashboardSource.PATTERNIHA))
        assertFalse(HamedSubscriptionManager.defaultAutoFor(HamedDashboardSource.SERVERLESS))
        assertFalse(HamedSubscriptionManager.defaultAutoFor(HamedDashboardSource.MITM))
        assertFalse(HamedSubscriptionManager.defaultAutoFor(HamedDashboardSource.AETHER))

        val list = listOf(
            HamedSubServerSummary("dead", "Dead", "", EConfigType.VLESS, -1L),
            HamedSubServerSummary("untested-1", "U1", "", EConfigType.VLESS, 0L),
            HamedSubServerSummary("slow", "Slow", "", EConfigType.VLESS, 420L),
            HamedSubServerSummary("fast", "Fast", "", EConfigType.VLESS, 95L),
            HamedSubServerSummary("untested-2", "U2", "", EConfigType.VLESS, 0L),
        )
        val ordered = HamedSubscriptionManager.orderedAutoCandidates(list).map { it.guid }
        assertEquals(listOf("fast", "slow", "untested-1", "untested-2", "dead"), ordered)
    }

    @Test
    fun batchOf5SelectorConnectsOnFirstRespondingBatchAndSkipsLaterBatches() = runBlocking {
        val guids = (1..12).map { "g$it" }
        val testedBatches = mutableListOf<List<String>>()
        val cancelledBatches = mutableListOf<List<String>>()

        // Batch 1 (g1..g5) all fail; Batch 2 (g6..g10) has g8=180ms and g10=90ms; Batch 3 must never run.
        val result = HamedAutoPingSelector.selectFirstRespondingInBatches(
            candidateGuids = guids,
            batchSize = 5,
            graceWindowMs = 0L,
        ) { batchGuids, onResult, onFinish ->
            testedBatches.add(batchGuids)
            batchGuids.forEach { guid ->
                val delay = when (guid) {
                    "g8" -> 180L
                    "g10" -> 90L
                    else -> -1L
                }
                onResult(guid, delay)
            }
            onFinish()
            HamedAutoPingSelector.BatchControl { cancelledBatches.add(batchGuids) }
        }

        assertNotNull(result)
        assertEquals("g10", result!!.guid)
        assertEquals(90L, result.delayMillis)
        assertEquals(2, result.progress.batchIndex)
        assertEquals(6, result.progress.startNumber)
        assertEquals(10, result.progress.endNumber)
        assertEquals(2, testedBatches.size)
        assertEquals(listOf("g1", "g2", "g3", "g4", "g5"), testedBatches[0])
        assertEquals(listOf("g6", "g7", "g8", "g9", "g10"), testedBatches[1])
        assertEquals(2, cancelledBatches.size)
    }

    @Test
    fun batchOf5SelectorReturnsNullWhenAllBatchesFail() = runBlocking {
        val guids = (1..7).map { "bad$it" }
        val testedBatches = mutableListOf<Int>()

        val result = HamedAutoPingSelector.selectFirstRespondingInBatches(
            candidateGuids = guids,
            batchSize = 5,
            graceWindowMs = 0L,
        ) { batchGuids, onResult, onFinish ->
            testedBatches.add(batchGuids.size)
            batchGuids.forEach { onResult(it, -1L) }
            onFinish()
            HamedAutoPingSelector.BatchControl {}
        }

        assertNull(result)
        assertEquals(listOf(5, 2), testedBatches)
    }
}
