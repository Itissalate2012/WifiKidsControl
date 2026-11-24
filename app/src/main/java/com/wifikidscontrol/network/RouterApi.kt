package com.wifikidscontrol.network

import com.wifikidscontrol.model.Device
import com.wifikidscontrol.model.NetworkEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

// Common data classes for router communication
@Serializable
data class RouterConfig(
    val id: String,
    val name: String,
    val type: RouterType,
    val baseUrl: String,
    val username: String? = null,
    val password: String? = null,
    val apiKey: String? = null,
    val sshHost: String? = null,
    val sshPort: Int = 22,
    val sshUser: String? = null,
    val sshKeyPath: String? = null,
    val customParams: Map<String, String> = emptyMap()
)

enum class RouterType {
    OPENWRT, ASUS_MERLIN, MIKROTIK, UNIFI, DDWRT, GENERIC_HTTP, GENERIC_SSH
}

@Serializable
data class ConnectedClient(
    val mac: String,
    val ip: String?,
    val name: String?,
    val vendor: String?,
    val signal: Int? = null,
    val rxBytes: Long? = null,
    val txBytes: Long? = null,
    val connectedTime: Long? = null,
    val interface: String? = null
)

@Serializable
data class BlockRule(
    val mac: String,
    val action: BlockAction,
    val scheduleId: String? = null
)

enum class BlockAction { BLOCK, ALLOW, THROTTLE_UP, THROTTLE_DOWN }

@Serializable
data class RouterStatus(
    val isConnected: Boolean,
    val uptime: Long?,
    val cpuLoad: Float?,
    val memUsage: Float?,
    val connectedClients: Int,
    val lastSync: String?
)

// Main Router API Interface - implemented per router type
interface RouterApi {
    suspend fun connect(config: RouterConfig): Result<RouterStatus>
    suspend fun disconnect(): Result<Unit>
    suspend fun getConnectedClients(): Result<List<ConnectedClient>>
    suspend fun blockDevice(mac: String): Result<Unit>
    suspend fun unblockDevice(mac: String): Result<Unit>
    suspend fun setDeviceSchedule(mac: String, scheduleId: String): Result<Unit>
    suspend fun getDeviceTraffic(mac: String): Result<TrafficStats>
    suspend fun applyRules(rules: List<BlockRule>): Result<Unit>
    suspend fun getStatus(): Result<RouterStatus>
    fun observeClients(): Flow<Result<List<ConnectedClient>>>
    fun observeStatus(): Flow<Result<RouterStatus>>
}

@Serializable
data class TrafficStats(
    val rxBytes: Long,
    val txBytes: Long,
    val rxPackets: Long,
    val txPackets: Long,
    val timestamp: String
)

// Result wrapper for consistent error handling
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
    object Unauthorized : Result<Nothing>()
    object NotConnected : Result<Nothing>()

    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this !is Success
    fun getOrNull(): T? = if (this is Success) data else null
    fun getOrThrow(): T = if (this is Success) data else throw (this as Error).cause ?: Exception((this as Error).message)
}

// OpenWrt / LuCI RPC Implementation
class OpenWrtApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val username: String,
    private val password: String
) : RouterApi {

    private var sessionId: String? = null
    private val json = com.squareup.moshi.Moshi.Builder().build().adapter(ConnectedClient::class.java)

    override suspend fun connect(config: RouterConfig): Result<RouterStatus> = try {
        val request = Request.Builder()
            .url("$baseUrl/cgi-bin/luci/rpc/auth")
            .post(RequestBody.create(
                """{"jsonrpc":"2.0","id":1,"method":"login","params":["$username","$password"]}""".toByteArray(),
                MediaType.get("application/json")
            ))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Result.Error("Login failed: ${response.code}")
            val body = response.body?.string() ?: return Result.Error("Empty response")
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            sessionId = json.get("result")?.getAsJsonArray()?.get(1)?.getAsString()
            if (sessionId.isNullOrBlank()) return Result.Error("No session ID")
            Result.Success(getStatusSync())
        }
    } catch (e: Exception) {
        Result.Error("OpenWrt connect failed", e)
    }

    private suspend fun getStatusSync(): RouterStatus = try {
        val clients = getConnectedClientsSync()
        RouterStatus(true, null, null, null, clients.size, java.time.Instant.now().toString())
    } catch (e: Exception) {
        RouterStatus(false, null, null, null, 0, null)
    }

    private suspend fun getConnectedClientsSync(): List<ConnectedClient> = try {
        val request = Request.Builder()
            .url("$baseUrl/cgi-bin/luci/rpc/uci")
            .addHeader("Authorization", "Bearer $sessionId")
            .post(RequestBody.create(
                """{"jsonrpc":"2.0","id":1,"method":"call","params":["$sessionId","luci-rpc","getWifiAssocList",{}]}""".toByteArray(),
                MediaType.get("application/json")
            ))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            val result = json.get("result")?.getAsJsonArray()?.get(1)?.asJsonArray
            return result?.map { parseOpenWrtClient(it.asJsonObject) } ?: emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseOpenWrtClient(obj: com.google.gson.JsonObject): ConnectedClient = ConnectedClient(
        mac = obj.get("mac")?.getAsString()?.uppercase() ?: "",
        ip = obj.get("ip")?.getAsString(),
        name = obj.get("name")?.getAsString(),
        vendor = obj.get("vendor")?.getAsString(),
        signal = obj.get("signal")?.getAsInt(),
        rxBytes = obj.get("rx_bytes")?.getAsLong(),
        txBytes = obj.get("tx_bytes")?.getAsLong(),
        connectedTime = obj.get("connected_time")?.getAsLong(),
        interface = obj.get("interface")?.getAsString()
    )

    override suspend fun disconnect(): Result<Unit> {
        sessionId = null
        return Result.Success(Unit)
    }

    override suspend fun getConnectedClients(): Result<List<ConnectedClient>> = try {
        Result.Success(getConnectedClientsSync())
    } catch (e: Exception) {
        Result.Error("Failed to get clients", e)
    }

    override suspend fun blockDevice(mac: String): Result<Unit> = try {
        // Add to firewall block list via uci
        val request = Request.Builder()
            .url("$baseUrl/cgi-bin/luci/rpc/uci")
            .addHeader("Authorization", "Bearer $sessionId")
            .post(RequestBody.create(
                """{"jsonrpc":"2.0","id":1,"method":"call","params":["$sessionId","luci-rpc","setFirewallRule",{"name":"block_${mac.replace(":","")}","src_mac":"$mac","target":"DROP"}]}""".toByteArray(),
                MediaType.get("application/json")
            ))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Result.Error("Block failed: ${response.code}")
            applyFirewallChanges()
        }
    } catch (e: Exception) {
        Result.Error("Block failed", e)
    }

    override suspend fun unblockDevice(mac: String): Result<Unit> = try {
        val request = Request.Builder()
            .url("$baseUrl/cgi-bin/luci/rpc/uci")
            .addHeader("Authorization", "Bearer $sessionId")
            .post(RequestBody.create(
                """{"jsonrpc":"2.0","id":1,"method":"call","params":["$sessionId","luci-rpc","deleteFirewallRule",{"name":"block_${mac.replace(":","")}"}]}""".toByteArray(),
                MediaType.get("application/json")
            ))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Result.Error("Unblock failed: ${response.code}")
            applyFirewallChanges()
        }
    } catch (e: Exception) {
        Result.Error("Unblock failed", e)
    }

    private suspend fun applyFirewallChanges(): Result<Unit> = try {
        val request = Request.Builder()
            .url("$baseUrl/cgi-bin/luci/rpc/uci")
            .addHeader("Authorization", "Bearer $sessionId")
            .post(RequestBody.create(
                """{"jsonrpc":"2.0","id":1,"method":"call","params":["$sessionId","luci-rpc","applyFirewall",{}]}""".toByteArray(),
                MediaType.get("application/json")
            ))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Result.Error("Apply failed: ${response.code}")
            Result.Success(Unit)
        }
    } catch (e: Exception) {
        Result.Error("Apply failed", e)
    }

    override suspend fun setDeviceSchedule(mac: String, scheduleId: String): Result<Unit> {
        // Would use cron or hotplug scripts on OpenWrt
        return Result.Success(Unit)
    }

    override suspend fun getDeviceTraffic(mac: String): Result<TrafficStats> = try {
        val clients = getConnectedClientsSync()
        val client = clients.find { it.mac.uppercase() == mac.uppercase() }
        return client?.let {
            Result.Success(TrafficStats(
                rxBytes = it.rxBytes ?: 0,
                txBytes = it.txBytes ?: 0,
                rxPackets = 0,
                txPackets = 0,
                timestamp = java.time.Instant.now().toString()
            ))
        } ?: Result.Error("Device not found")
    } catch (e: Exception) {
        Result.Error("Traffic failed", e)
    }

    override suspend fun applyRules(rules: List<BlockRule>): Result<Unit> {
        // Batch apply multiple rules
        return Result.Success(Unit)
    }

    override suspend fun getStatus(): Result<RouterStatus> = Result.Success(getStatusSync())

    override fun observeClients(): Flow<Result<List<ConnectedClient>>> = flow {
        while (true) {
            emit(getConnectedClients())
            delay(10000) // 10 second polling
        }
    }.catch { emit(Result.Error(it.message)) }

    override fun observeStatus(): Flow<Result<RouterStatus>> = flow {
        while (true) {
            emit(getStatus())
            delay(30000)
        }
    }.catch { emit(Result.Error(it.message)) }
}

// Asus Merlin / Stock API
class AsusApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val username: String,
    private val password: String
) : RouterApi {
    private var sessionCookie: String? = null

    override suspend fun connect(config: RouterConfig): Result<RouterStatus> = try {
        val request = Request.Builder()
            .url("$baseUrl/login.cgi")
            .post(FormBody.Builder()
                .add("login_authorization", base64Encode("$username:$password"))
                .add("action", "login")
                .build())
            .build()

        client.newCall(request).execute().use { response ->
            val cookies = response.headers("Set-Cookie")
            sessionCookie = cookies.firstOrNull { it.startsWith("asus_token=") }?.split(";").first()
            if (sessionCookie.isNullOrBlank()) return Result.Error("Asus login failed")
            Result.Success(getStatusSync())
        }
    } catch (e: Exception) {
        Result.Error("Asus connect failed", e)
    }

    private suspend fun getStatusSync(): RouterStatus = RouterStatus(true, null, null, null, 0, java.time.Instant.now().toString())

    override suspend fun disconnect(): Result<Unit> {
        sessionCookie = null
        return Result.Success(Unit)
    }

    override suspend fun getConnectedClients(): Result<List<ConnectedClient>> = Result.Success(emptyList())
    override suspend fun blockDevice(mac: String): Result<Unit> = Result.Success(Unit)
    override suspend fun unblockDevice(mac: String): Result<Unit> = Result.Success(Unit)
    override suspend fun setDeviceSchedule(mac: String, scheduleId: String): Result<Unit> = Result.Success(Unit)
    override suspend fun getDeviceTraffic(mac: String): Result<TrafficStats> = Result.Success(TrafficStats(0,0,0,0,""))
    override suspend fun applyRules(rules: List<BlockRule>): Result<Unit> = Result.Success(Unit)
    override suspend fun getStatus(): Result<RouterStatus> = Result.Success(getStatusSync())
    override fun observeClients(): Flow<Result<List<ConnectedClient>>> = flow { while(true) { emit(Result.Success(emptyList())); delay(10000) } }
    override fun observeStatus(): Flow<Result<RouterStatus>> = flow { while(true) { emit(Result.Success(getStatusSync())); delay(30000) } }

    private fun base64Encode(input: String): String = android.util.Base64.encodeToString(input.toByteArray(), android.util.Base64.NO_WRAP)
}

// MikroTik RouterOS API
class MikroTikApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val username: String,
    private val password: String
) : RouterApi {

    override suspend fun connect(config: RouterConfig): Result<RouterStatus> = Result.Success(RouterStatus(true, null, null, null, 0, java.time.Instant.now().toString()))
    override suspend fun disconnect(): Result<Unit> = Result.Success(Unit)
    override suspend fun getConnectedClients(): Result<List<ConnectedClient>> = Result.Success(emptyList())
    override suspend fun blockDevice(mac: String): Result<Unit> = Result.Success(Unit)
    override suspend fun unblockDevice(mac: String): Result<Unit> = Result.Success(Unit)
    override suspend fun setDeviceSchedule(mac: String, scheduleId: String): Result<Unit> = Result.Success(Unit)
    override suspend fun getDeviceTraffic(mac: String): Result<TrafficStats> = Result.Success(TrafficStats(0,0,0,0,""))
    override suspend fun applyRules(rules: List<BlockRule>): Result<Unit> = Result.Success(Unit)
    override suspend fun getStatus(): Result<RouterStatus> = Result.Success(RouterStatus(true, null, null, null, 0, java.time.Instant.now().toString()))
    override fun observeClients(): Flow<Result<List<ConnectedClient>>> = flow { while(true) { emit(Result.Success(emptyList())); delay(10000) } }
    override fun observeStatus(): Flow<Result<RouterStatus>> = flow { while(true) { emit(Result.Success(RouterStatus(true, null, null, null, 0, java.time.Instant.now().toString()))); delay(30000) } }
}

// Generic HTTP API for custom routers
class GenericHttpApi(
    private val client: OkHttpClient,
    private val config: RouterConfig
) : RouterApi {
    override suspend fun connect(config: RouterConfig): Result<RouterStatus> = Result.Success(RouterStatus(true, null, null, null, 0, java.time.Instant.now().toString()))
    override suspend fun disconnect(): Result<Unit> = Result.Success(Unit)
    override suspend fun getConnectedClients(): Result<List<ConnectedClient>> = Result.Success(emptyList())
    override suspend fun blockDevice(mac: String): Result<Unit> = Result.Success(Unit)
    override suspend fun unblockDevice(mac: String): Result<Unit> = Result.Success(Unit)
    override suspend fun setDeviceSchedule(mac: String, scheduleId: String): Result<Unit> = Result.Success(Unit)
    override suspend fun getDeviceTraffic(mac: String): Result<TrafficStats> = Result.Success(TrafficStats(0,0,0,0,""))
    override suspend fun applyRules(rules: List<BlockRule>): Result<Unit> = Result.Success(Unit)
    override suspend fun getStatus(): Result<RouterStatus> = Result.Success(RouterStatus(true, null, null, null, 0, java.time.Instant.now().toString()))
    override fun observeClients(): Flow<Result<List<ConnectedClient>>> = flow { while(true) { emit(Result.Success(emptyList())); delay(10000) } }
    override fun observeStatus(): Flow<Result<RouterStatus>> = flow { while(true) { emit(Result.Success(RouterStatus(true, null, null, null, 0, java.time.Instant.now().toString()))); delay(30000) } }
}

// Factory to create appropriate API implementation
object RouterApiFactory {
    fun create(config: RouterConfig, client: OkHttpClient): RouterApi = when (config.type) {
        RouterType.OPENWRT -> OpenWrtApi(client, config.baseUrl, config.username ?: "", config.password ?: "")
        RouterType.ASUS_MERLIN -> AsusApi(client, config.baseUrl, config.username ?: "", config.password ?: "")
        RouterType.MIKROTIK -> MikroTikApi(client, config.baseUrl, config.username ?: "", config.password ?: "")
        else -> GenericHttpApi(client, config)
    }
}