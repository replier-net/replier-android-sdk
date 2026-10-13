package net.replier.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Thrown when the API answers with "success": false, or when the HTTP call fails. */
class ReplierException(
    message: String,
    val errorCode: String? = null,
    val response: JSONObject? = null,
    val httpStatus: Int = 0,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Replier.net WhatsApp API client. All calls are `suspend` functions and run on Dispatchers.IO.
 *
 * ```
 * val replier = Replier(baseUrl = "https://your-backend.example.com/replier")
 * lifecycleScope.launch { replier.sendMessage("15551234567", "Hello!") }
 * ```
 *
 * Anything compiled into an APK can be extracted. For public apps, set [baseUrl] to your own
 * backend proxy that injects the credentials, and leave [instanceId]/[token] null.
 */
class Replier(
    val instanceId: String? = null,
    val token: String? = null,
    /** Needed only for listInstances / newInstance / renewInstance. */
    val email: String? = null,
    val apiKey: String? = null,
    baseUrl: String = DEFAULT_BASE_URL,
    private val timeoutMs: Int = 70_000,
) {
    companion object {
        const val VERSION = "1.0.0"
        const val DEFAULT_BASE_URL = "https://api.replier.net"
    }

    private val baseUrl = baseUrl.trimEnd('/')

    // ------------------------------------------------------------------ core

    /** Call any instance endpoint; instanceid and token are added automatically. */
    suspend fun call(method: String, params: Map<String, Any?> = emptyMap()): JSONObject {
        val body = LinkedHashMap<String, Any?>()
        instanceId?.let { body["instanceid"] = it }
        token?.let { body["token"] = it }
        body.putAll(params)
        return request(method, body)
    }

    /** Call an account endpoint; email and apikey are added automatically. */
    suspend fun callAccount(method: String, params: Map<String, Any?> = emptyMap()): JSONObject {
        val body = LinkedHashMap<String, Any?>()
        email?.let { body["email"] = it }
        apiKey?.let { body["apikey"] = it }
        body.putAll(params)
        return request(method, body)
    }

    private suspend fun request(method: String, params: Map<String, Any?>): JSONObject = withContext(Dispatchers.IO) {
        val json = JSONObject()
        for ((k, v) in params) if (v != null) json.put(k, JSONObject.wrap(v))

        val conn = (URL("$baseUrl/$method").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = timeoutMs
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "replier-android/$VERSION")
        }
        try {
            val status: Int
            val raw: String
            try {
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                status = conn.responseCode
                val stream = if (status >= 400) conn.errorStream else conn.inputStream
                raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            } catch (e: IOException) {
                throw ReplierException("Request failed: ${e.message}", cause = e)
            }

            val data = try {
                JSONObject(raw)
            } catch (e: JSONException) {
                throw ReplierException("Invalid JSON response (HTTP $status)", httpStatus = status, cause = e)
            }
            val success = data.opt("success")
            val failed = success == false || success == "false"
            if (status >= 400 || failed) {
                val code = if (data.has("errorCode") && !data.isNull("errorCode")) data.get("errorCode").toString() else null
                throw ReplierException(reason(data, if (status >= 400) "HTTP $status" else "Request failed"), code, data, status)
            }
            data
        } finally {
            conn.disconnect()
        }
    }

    private fun reason(data: JSONObject, fallback: String): String {
        data.optString("reason").takeIf { it.isNotEmpty() }?.let { return it }
        when (val msg = data.opt("msg")) {
            is JSONArray -> return (0 until msg.length()).joinToString(", ") { msg.opt(it).toString() }
            is String -> if (msg.isNotEmpty()) return msg
        }
        return fallback
    }

    // ------------------------------------------------------------------ instance

    suspend fun status() = call("status")
    suspend fun qrCode() = call("qr_code")
    suspend fun pairCode(phone: String) = call("pair_code", mapOf("phone" to phone))
    suspend fun logout() = call("logout")
    suspend fun takeover() = call("takeover")
    suspend fun expiry() = call("expiry")
    suspend fun retry() = call("retry")
    suspend fun reboot() = call("reboot")
    suspend fun getSettings() = call("getSettings")
    suspend fun setSettings(settings: Map<String, Any?>) = call("settings", settings)
    suspend fun me() = call("me")
    suspend fun version() = call("version")
    suspend fun screenshot() = call("screenshot")

    // ------------------------------------------------------------------ messages

    suspend fun sendMessage(phone: String, body: String, options: Map<String, Any?>? = null) =
        call("sendMessage", mapOf("phone" to phone, "body" to body, "options" to options))

    /** [body] is a file URL or base64 data URI. */
    suspend fun sendFile(phone: String, body: String, filename: String, caption: String? = null) =
        call("sendFile", mapOf("phone" to phone, "body" to body, "filename" to filename, "caption" to caption))

    suspend fun sendPTT(phone: String, audio: String) = call("sendPTT", mapOf("phone" to phone, "audio" to audio))
    suspend fun sendLink(phone: String, body: String) = call("sendLink", mapOf("phone" to phone, "body" to body))
    suspend fun sendContact(phone: String, contactId: String) = call("sendContact", mapOf("phone" to phone, "contactId" to contactId))

    suspend fun sendLocation(phone: String, lat: Double, lng: Double, address: String? = null) =
        call("sendLocation", mapOf("phone" to phone, "lat" to lat, "lng" to lng, "address" to address))

    suspend fun sendVCard(phone: String, vcard: String) = call("sendVCard", mapOf("phone" to phone, "vcard" to vcard))

    suspend fun sendButton(phone: String, body: String, buttons: Any, header: String? = null, footer: String? = null) =
        call("sendButton", mapOf("phone" to phone, "body" to body, "buttons" to buttons, "header" to header, "footer" to footer))

    suspend fun sendList(phone: String, body: String, title: String, list: Any, header: String? = null, footer: String? = null) =
        call("sendList", mapOf("phone" to phone, "body" to body, "title" to title, "list" to list, "header" to header, "footer" to footer))

    suspend fun forwardMessage(phone: String, messageId: String) = call("forwardMessage", mapOf("phone" to phone, "messageId" to messageId))
    suspend fun deleteMessage(phone: String, messageId: String) = call("deleteMessage", mapOf("phone" to phone, "messageId" to messageId))
    suspend fun sendSeen(phone: String) = call("sendSeen", mapOf("phone" to phone))

    suspend fun messages(phone: String, limit: Int? = null, lastMessageNumber: Long? = null) =
        call("messages", mapOf("phone" to phone, "limit" to limit, "lastMessageNumber" to lastMessageNumber))

    suspend fun showMessagesQueue() = call("showMessagesQueue")
    suspend fun clearMessagesQueue() = call("clearMessagesQueue")
    suspend fun showActionsQueue() = call("showActionsQueue")
    suspend fun clearActionsQueue() = call("clearActionsQueue")

    // ------------------------------------------------------------------ chats

    suspend fun dialogs() = call("dialogs")
    suspend fun chats() = call("chats")
    suspend fun contacts() = call("contacts")

    // ------------------------------------------------------------------ groups

    suspend fun createGroup(groupName: String, phones: List<String>) =
        call("group", mapOf("groupName" to groupName, "phones" to phones.joinToString(",")))

    suspend fun leaveGroup(groupId: String) = call("leaveGroup", mapOf("groupId" to groupId))

    suspend fun addGroupParticipant(groupId: String, phones: List<String>) =
        call("addGroupParticipant", mapOf("groupId" to groupId, "phones" to phones.joinToString(",")))

    suspend fun removeGroupParticipant(groupId: String, phones: List<String>) =
        call("removeGroupParticipant", mapOf("groupId" to groupId, "phones" to phones.joinToString(",")))

    suspend fun promoteGroupParticipant(groupId: String, phones: List<String>) =
        call("promoteGroupParticipant", mapOf("groupId" to groupId, "phones" to phones.joinToString(",")))

    suspend fun demoteGroupParticipant(groupId: String, phones: List<String>) =
        call("demoteGroupParticipant", mapOf("groupId" to groupId, "phones" to phones.joinToString(",")))

    // ------------------------------------------------------------------ channels

    suspend fun createChannel(name: String, description: String? = null, picture: String? = null) =
        call("channel", mapOf("name" to name, "description" to description, "picture" to picture))

    suspend fun followChannel(channelId: String) = call("channelFollow", mapOf("channelId" to channelId))
    suspend fun unfollowChannel(channelId: String) = call("channelUnfollow", mapOf("channelId" to channelId))
    suspend fun channelInfo(channelId: String) = call("channelInfo", mapOf("channelId" to channelId))

    // ------------------------------------------------------------------ webhook / bot / ban

    suspend fun setWebhook(webhookUrl: String) = call("webhook", mapOf("webhookUrl" to webhookUrl))
    suspend fun getWebhookStatus() = call("getWebhookStatus")
    suspend fun getBanSettings() = call("getBanSettings")
    suspend fun botSettings() = call("botSettings")
    suspend fun getBotSettings() = call("getbotSettings")

    // ------------------------------------------------------------------ account

    suspend fun listInstances() = callAccount("listInstances")

    suspend fun newInstance(packageId: Int, voucher: String? = null) =
        callAccount("newInstance", mapOf("packageid" to packageId, "voucher" to voucher))

    suspend fun renewInstance(instanceId: String, packageId: Int, voucher: String? = null) =
        callAccount("renewInstance", mapOf("instanceid" to instanceId, "packageid" to packageId, "voucher" to voucher))
}
