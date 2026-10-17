package com.beykus.beygram.data.vpn

import com.beykus.beygram.domain.model.VpnSettings
import com.beykus.beygram.BuildConfig
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/** Validates the provider document structure and forces app-private traffic settings. */
internal class MihomoProfileNormalizer(private val credentials: LoopbackProxyCredentials) {
    fun normalize(source: String): NormalizedMihomoProfile {
        require(source.isNotBlank()) { "Подписка пустая" }
        require(source.toByteArray(Charsets.UTF_8).size <= MAX_PROFILE_BYTES) { "Подписка слишком большая (больше 5 МБ)" }

        val loaderOptions = LoaderOptions().apply {
            codePointLimit = MAX_PROFILE_BYTES
            maxAliasesForCollections = 32
            isAllowDuplicateKeys = false
        }
        val safeYaml = Yaml(SafeConstructor(loaderOptions))
        val parsed = parseYaml(safeYaml, source)
        VpnSubscriptionDiagnostics.event("yaml", "sourceRoot=${rootKind(parsed)}")
        var root = parsed.value as? Map<*, *>
        if (root == null) {
            // A plain list of proxy URIs is not a YAML object, so it is read before the text is treated as Base64.
            val plainUris = parseProxyUriList(source)
            if (plainUris.proxies.isNotEmpty()) {
                VpnSubscriptionDiagnostics.event("uri", plainUris.summary)
                root = linkedMapOf("proxies" to plainUris.proxies)
            }
        }
        if (root == null) {
            val decoded = decodeBase64Yaml(source)
                ?: throw SubscriptionException(
                    "Ссылка не похожа на подписку с серверами."
                )
            VpnSubscriptionDiagnostics.event("base64", "decoded=true decodedBytes=${decoded.toByteArray(Charsets.UTF_8).size}")
            val decodedParsed = parseYaml(safeYaml, decoded)
            VpnSubscriptionDiagnostics.event("yaml", "decodedRoot=${rootKind(decodedParsed)}")
            root = decodedParsed.value as? Map<*, *>
            if (root == null) {
                val result = parseProxyUriList(decoded)
                VpnSubscriptionDiagnostics.event("uri", result.summary)
                if (result.proxies.isEmpty()) throw SubscriptionException("В подписке нет серверов, которые можно использовать")
                root = linkedMapOf("proxies" to result.proxies)
            }
        }
        val config = linkedMapOf<String, Any?>()
        root!!.forEach { (key, value) ->
            val stringKey = key as? String ?: throw SubscriptionException("В подписке есть некорректные данные")
            config[stringKey] = value
        }

        // Keep Mihomo's app-facing proxy private to this process/device. The core
        // uses an explicit loopback SOCKS inlet; no transparent TUN is created.
        config["allow-lan"] = false
        config["bind-address"] = MihomoProxyClient.HOST
        // Shortcut inbounds open server ports as well (the port sits inside their value), so they are removed with listeners.
        listOf(
            "external-controller", "external-controller-tls", "external-controller-unix",
            "external-controller-pipe", "secret", "authentication", "skip-auth-prefixes",
            "listeners", "ss-config", "vmess-config", "tuic-server"
        ).forEach(config::remove)
        listOf("port", "mixed-port", "redir-port", "tproxy-port", "tun-port").forEach { key ->
            if (key in config) config[key] = 0
        }
        config["socks-port"] = MihomoProxyClient.PORT
        // Every app on the device can reach the loopback port, so the listener requires per-installation credentials.
        config["authentication"] = listOf("${credentials.username}:${credentials.password}")
        // Set explicitly: when the key is absent, the core default may exempt loopback clients from the credentials above.
        config["skip-auth-prefixes"] = emptyList<String>()
        if (BuildConfig.DEBUG) config["log-level"] = "debug"
        val dns = (config["dns"] as? Map<*, *>)?.let { existing ->
            linkedMapOf<String, Any?>().apply {
                existing.forEach { (key, value) -> (key as? String)?.let { put(it, value) } }
            }
        } ?: linkedMapOf()
        // Node hostnames must use Android's active resolver. Provider DNS endpoints may be
        // blocked on mobile networks and otherwise prevent every proxy from ever dialing.
        dns["proxy-server-nameserver"] = listOf("system")
        config["dns"] = dns
        MihomoRuntimeDiagnostics.event("profile", "proxy-node DNS resolver=Android system")
        val tun = (config["tun"] as? Map<*, *>)?.let { existing ->
            linkedMapOf<String, Any?>().apply {
                existing.forEach { (key, value) -> (key as? String)?.let { put(it, value) } }
                put("enable", false)
            }
        } ?: linkedMapOf("enable" to false)
        config["tun"] = tun

        val groupName = VpnSettings.DEFAULT_CONNECTION_GROUP
        val groups = (config["proxy-groups"] as? List<*>)?.toMutableList() ?: mutableListOf()
        val groupIndex = groups.indexOfFirst { (it as? Map<*, *>)?.get("name") == groupName }
        if (groupIndex >= 0) {
            val existingGroup = groups[groupIndex] as? Map<*, *>
            val type = (existingGroup?.get("type") as? String)?.lowercase()
            if (type != "select") {
                throw SubscriptionException("В подписке уже есть группа с именем ${VpnSettings.DEFAULT_CONNECTION_GROUP}")
            }
            groups[groupIndex] = linkedMapOf<String, Any?>().apply {
                existingGroup?.forEach { (key, value) -> (key as? String)?.let { put(it, value) } }
                put("include-all", true)
            }
        } else {
            groups += linkedMapOf(
                "name" to groupName,
                "type" to "select",
                "include-all" to true
            )
        }
        config["proxy-groups"] = groups
        // Route all requests entering through BeyGram's local SOCKS listener through
        // the selected node. Other clients are explicitly configured to use SOCKS.
        config["mode"] = "rule"
        config["rules"] = listOf("MATCH,$groupName")

        val dumperOptions = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
            indicatorIndent = 0
            width = MAX_PROFILE_BYTES
        }
        val normalized = runCatching { Yaml(dumperOptions).dump(config) }
            .getOrElse { throw SubscriptionException("Не удалось обработать подписку") }
        VpnSubscriptionDiagnostics.event("normalize", "accepted topLevelFields=${config.size}")
        return NormalizedMihomoProfile(normalized, groupName)
    }

    private fun rootKind(parsed: ParsedYaml): String = when {
        parsed.failed -> "invalidYaml"
        parsed.value == null -> "null"
        parsed.value is Map<*, *> -> "object"
        parsed.value is List<*> -> "array"
        parsed.value is String -> "string"
        parsed.value is Number -> "number"
        parsed.value is Boolean -> "boolean"
        else -> "${parsed.value?.javaClass?.simpleName ?: "unknown"}"
    }

    private fun parseYaml(yaml: Yaml, source: String): ParsedYaml = try {
        ParsedYaml(value = yaml.load<Any?>(source), failed = false)
    } catch (_: Exception) {
        ParsedYaml(value = null, failed = true)
    }

    private fun decodeBase64Yaml(source: String): String? {
        val compact = source.filterNot(Char::isWhitespace)
        if (compact.length < MIN_BASE64_LENGTH || compact.length % 4 == 1 || !BASE64_DOCUMENT.matches(compact)) return null
        val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
        val decoder = if ('-' in compact || '_' in compact) Base64.getUrlDecoder() else Base64.getDecoder()
        val bytes = runCatching { decoder.decode(padded) }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_PROFILE_BYTES) return null
        val decoded = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .removePrefix("\uFEFF")
                .trim()
        }.getOrNull() ?: return null
        if (decoded.isBlank() || '\u0000' in decoded) return null
        return decoded
    }

    /** Converts common subscription URI formats to Mihomo proxy objects without retaining source text. */
    private fun parseProxyUriList(text: String): ParsedUriList {
        val lines = text.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toList()
        val counts = sortedMapOf<String, Int>()
        val proxies = mutableListOf<Map<String, Any?>>()
        lines.forEach { line ->
            val rawScheme = line.substringBefore("://", "other").lowercase()
            val scheme = rawScheme.takeIf { it in LOGGABLE_SCHEMES } ?: "other"
            val transport = if (scheme == "vless") {
                val query = line.substringAfter('?', "").substringBefore('#')
                val params = parseQuery(query)
                (params["type"] ?: params["network"] ?: "tcp").lowercase()
                    .let { if (it in LOGGABLE_TRANSPORTS) it else "other" }
            } else "-"
            val result = runCatching { parseProxyUri(line) }.getOrNull()
            if (result != null) proxies += result
            val label = "$scheme/$transport/${if (result == null) "rejected" else "accepted"}"
            counts[label] = (counts[label] ?: 0) + 1
        }
        val summary = "lines=${lines.size} accepted=${proxies.size} rejected=${lines.size - proxies.size} bySchemeTransport=${counts.entries.joinToString(",") { "${it.key}:${it.value}" }}"
        return ParsedUriList(proxies, summary)
    }

    private fun parseProxyUri(line: String): Map<String, Any?>? {
        if (line.startsWith("vmess://", true)) return parseVmess(line)
        val uri = runCatching { URI(line) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it in 1..65535 } ?: return null
        val fragment = uri.rawFragment?.let(::decodeUriPart).orEmpty()
        val name = fragment.ifBlank { "$scheme-$host-$port" }.take(96)
        val params = parseQuery(uri.rawQuery)
        val proxy = linkedMapOf<String, Any?>("name" to name, "server" to host, "port" to port)
        when (scheme) {
            "ss" -> {
                proxy["type"] = "ss"
                val userInfo = uri.rawUserInfo ?: return null
                val decoded = decodeUriPart(userInfo)
                val methodPassword = if (":" in decoded) decoded else decodeBase64Text(decoded) ?: return null
                val split = methodPassword.indexOf(':')
                if (split <= 0 || split == methodPassword.lastIndex) return null
                proxy["cipher"] = methodPassword.substring(0, split)
                proxy["password"] = methodPassword.substring(split + 1)
                params["plugin"]?.let { proxy["plugin"] = it }
            }
            "trojan" -> {
                proxy["type"] = "trojan"
                proxy["password"] = uri.rawUserInfo?.let(::decodeUriPart)?.takeIf(String::isNotBlank) ?: return null
                addTls(proxy, params, uri.host)
            }
            "vless" -> {
                proxy["type"] = "vless"
                proxy["uuid"] = uri.rawUserInfo?.let(::decodeUriPart)?.takeIf(String::isNotBlank) ?: return null
                addTransport(proxy, params)
                addTls(proxy, params, uri.host)
                params["flow"]?.let { proxy["flow"] = it }
                params["encryption"]?.let { proxy["encryption"] = it }
                params["udp"]?.let { if (it.equals("true", true) || it == "1") proxy["udp"] = true }
                (params["packetEncoding"] ?: params["packet-encoding"])?.let { proxy["packet-encoding"] = it }
                (params["fp"] ?: params["fingerprint"])?.let { proxy["client-fingerprint"] = it }
                (params["allowInsecure"] ?: params["insecure"])?.let {
                    if (it.equals("true", true) || it == "1") proxy["skip-cert-verify"] = true
                }
            }
            "vmess" -> return null
            "hysteria2", "hy2" -> {
                proxy["type"] = "hysteria2"
                proxy["password"] = uri.rawUserInfo?.let(::decodeUriPart)?.substringAfter(':', uri.rawUserInfo.let(::decodeUriPart))?.takeIf(String::isNotBlank) ?: return null
                addTls(proxy, params, uri.host)
                params["obfs"]?.let { proxy["obfs"] = it }
                params["obfs-password"]?.let { proxy["obfs-password"] = it }
                params["sni"]?.let { proxy["sni"] = it }
            }
            "tuic" -> {
                proxy["type"] = "tuic"
                val credentials = uri.rawUserInfo?.let(::decodeUriPart)?.split(':', limit = 2) ?: return null
                if (credentials.size != 2) return null
                proxy["uuid"] = credentials[0]
                proxy["password"] = credentials[1]
                addTls(proxy, params, uri.host)
                params["congestion_control"]?.let { proxy["congestion-controller"] = it }
                params["alpn"]?.let { proxy["alpn"] = it.split(',') }
            }
            else -> return null
        }
        return proxy
    }

    private fun parseVmess(line: String): Map<String, Any?>? {
        val jsonText = decodeBase64Text(line.substringAfter("vmess://")) ?: return null
        val json = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null
        val host = json.optString("add").takeIf(String::isNotBlank) ?: return null
        val port = json.optString("port").toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val uuid = json.optString("id").takeIf(String::isNotBlank) ?: return null
        val name = json.optString("ps").ifBlank { "vmess-$host-$port" }.take(96)
        val proxy = linkedMapOf<String, Any?>("name" to name, "type" to "vmess", "server" to host, "port" to port, "uuid" to uuid,
            "alterId" to json.optString("aid", "0").toIntOrNull().orZero(), "cipher" to "auto")
        val tls = json.optString("tls").equals("tls", true)
        if (tls) {
            proxy["tls"] = true
            proxy["servername"] = json.optString("sni").ifBlank { json.optString("host").ifBlank { host } }
            json.optString("alpn").takeIf(String::isNotBlank)?.let { proxy["alpn"] = it.split(',') }
        }
        when (json.optString("net").lowercase()) {
            "ws" -> proxy["ws-opts"] = linkedMapOf("path" to json.optString("path", "/"), "headers" to linkedMapOf("Host" to json.optString("host")))
            "grpc" -> proxy["grpc-opts"] = linkedMapOf("grpc-service-name" to json.optString("path"))
            "tcp", "" -> Unit
            else -> return null
        }
        return proxy
    }

    private fun addTls(proxy: MutableMap<String, Any?>, params: Map<String, String>, host: String) {
        val security = params["security"]?.lowercase()
        val tls = security == "tls" || security == "reality" || params["tls"]?.equals("true", true) == true || params["tls"] == "1"
        if (tls) {
            proxy["tls"] = true
            proxy["servername"] = params["sni"] ?: params["serverName"] ?: params["peer"] ?: host
            params["alpn"]?.let { proxy["alpn"] = it.split(',') }
            if (security == "reality") {
                proxy["reality-opts"] = linkedMapOf(
                    "public-key" to (params["pbk"] ?: params["publicKey"]),
                    "short-id" to (params["sid"] ?: params["shortId"])
                ).filterValues { it != null }
                (params["fp"] ?: params["fingerprint"])?.let { proxy["client-fingerprint"] = it }
            }
        }
    }

    private fun addTransport(proxy: MutableMap<String, Any?>, params: Map<String, String>) {
        val network = (params["type"] ?: params["network"] ?: "tcp").lowercase()
        proxy["network"] = network.takeIf { it in setOf("ws", "http", "h2", "grpc", "xhttp") } ?: "tcp"
        when (proxy["network"]) {
            "ws" -> proxy["ws-opts"] = linkedMapOf<String, Any?>("path" to (params["path"] ?: "/")).apply {
                params["host"]?.let { put("headers", linkedMapOf("Host" to it)) }
            }
            "grpc" -> proxy["grpc-opts"] = linkedMapOf("grpc-service-name" to (params["serviceName"] ?: params["service_name"] ?: params["path"] ?: ""))
            "http" -> proxy["http-opts"] = linkedMapOf<String, Any?>("path" to listOf(params["path"] ?: "/")).apply {
                params["host"]?.let { put("headers", linkedMapOf("Host" to listOf(it))) }
            }
            "h2" -> proxy["h2-opts"] = linkedMapOf<String, Any?>("path" to (params["path"] ?: "/")).apply {
                params["host"]?.let { put("host", it.split(',').map(String::trim).filter(String::isNotEmpty)) }
            }
            "xhttp" -> proxy["xhttp-opts"] = buildXhttpOptions(params)
            else -> Unit // Mihomo treats unset or unknown VLESS network values as TCP.
        }
    }

    private fun buildXhttpOptions(params: Map<String, String>): Map<String, Any?> = linkedMapOf<String, Any?>(
        "path" to (params["path"] ?: "/"),
        "mode" to (params["mode"] ?: "auto")
    ).apply {
        params["host"]?.let { put("host", it) }
        val mappings = mapOf(
            "noGrpcHeader" to "no-grpc-header",
            "xPaddingBytes" to "x-padding-bytes",
            "xPaddingObfsMode" to "x-padding-obfs-mode",
            "xPaddingKey" to "x-padding-key",
            "xPaddingHeader" to "x-padding-header",
            "xPaddingPlacement" to "x-padding-placement",
            "xPaddingMethod" to "x-padding-method",
            "uplinkHTTPMethod" to "uplink-http-method",
            "sessionPlacement" to "session-placement",
            "sessionKey" to "session-key",
            "sessionTable" to "session-table",
            "sessionLength" to "session-length",
            "seqPlacement" to "seq-placement",
            "seqKey" to "seq-key",
            "uplinkDataPlacement" to "uplink-data-placement",
            "uplinkDataKey" to "uplink-data-key",
            "uplinkChunkSize" to "uplink-chunk-size",
            "scMaxEachPostBytes" to "sc-max-each-post-bytes",
            "scMinPostsIntervalMs" to "sc-min-posts-interval-ms"
        )
        mappings.forEach { (sourceKey, configKey) -> params[sourceKey]?.let { value ->
            put(configKey, when (sourceKey) {
                "noGrpcHeader", "xPaddingObfsMode" -> value.equals("true", true) || value == "1"
                "sessionLength", "uplinkChunkSize", "scMaxEachPostBytes", "scMinPostsIntervalMs" -> value.toIntOrNull() ?: value
                else -> value
            })
        } }
        val headers = params["headers"]?.split(',')?.mapNotNull { header ->
            val split = header.indexOf(':')
            if (split <= 0) null else header.substring(0, split).trim() to header.substring(split + 1).trim()
        }?.toMap()
        if (!headers.isNullOrEmpty()) put("headers", headers)
    }

    private fun parseQuery(query: String?): MutableMap<String, String> = query.orEmpty().split('&').mapNotNull { item ->
        if (item.isBlank()) null else item.substringBefore('=').let { decodeUriPart(it) to decodeUriPart(item.substringAfter('=', "")) }
    }.toMap().toMutableMap()

    private fun decodeUriPart(value: String): String = runCatching { URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name()) }.getOrDefault(value)
    private fun decodeBase64Text(value: String): String? = runCatching {
        val compact = value.trim().replace('-', '+').replace('_', '/')
        String(Base64.getDecoder().decode(compact + "=".repeat((4 - compact.length % 4) % 4)), StandardCharsets.UTF_8)
    }.getOrNull()
    private fun Int?.orZero(): Int = this ?: 0

    companion object {
        private const val MAX_PROFILE_BYTES = 5 * 1024 * 1024
        private const val MIN_BASE64_LENGTH = 16
        private val BASE64_DOCUMENT = Regex("^[A-Za-z0-9_+/=-]+$")
        private val LOGGABLE_SCHEMES = setOf("vless", "vmess", "ss", "trojan", "hysteria2", "hy2", "tuic")
        private val LOGGABLE_TRANSPORTS = setOf("tcp", "raw", "ws", "http", "h2", "grpc", "xhttp")
    }
}

private data class ParsedYaml(val value: Any?, val failed: Boolean)
private data class ParsedUriList(val proxies: List<Map<String, Any?>>, val summary: String)

data class NormalizedMihomoProfile(val yaml: String, val connectionGroup: String)
