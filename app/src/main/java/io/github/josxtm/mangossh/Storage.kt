package io.github.josxtm.mangossh

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import io.github.josxtm.mangossh.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName = "profiles")
data class ProfileRow(@PrimaryKey val id: String, val name: String, val host: String,
    val port: Int, val user: String, val auth: String) {
    fun model() = Profile(id, name, host, port, user, AuthType.valueOf(auth))
    companion object { fun from(p: Profile) = ProfileRow(p.id, p.name, p.host, p.port, p.user, p.auth.name) }
}
@Entity(tableName = "history")
data class HistoryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long, val profileId: String, val profileName: String, val host: String,
    val commandId: String, val command: String, val durationMs: Long, val status: String,
    val exitCode: Int?, val output: String?,
)
@Dao interface MangoDao {
    @Query("SELECT * FROM profiles ORDER BY name") fun profiles(): Flow<List<ProfileRow>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(profile: ProfileRow)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun deleteProfile(id: String)
    @Query("SELECT * FROM history ORDER BY timestamp DESC, id DESC LIMIT 500") fun history(): Flow<List<HistoryRow>>
    @Insert suspend fun record(history: HistoryRow)
    @Query("DELETE FROM history WHERE id = :id") suspend fun deleteHistory(id: Long)
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("DELETE FROM history WHERE timestamp < :cutoff OR id NOT IN (SELECT id FROM history ORDER BY timestamp DESC, id DESC LIMIT 500)")
    suspend fun prune(cutoff: Long)
}
@Database(entities = [ProfileRow::class, HistoryRow::class], version = 1, exportSchema = false)
abstract class MangoDatabase : RoomDatabase() { abstract fun dao(): MangoDao }

/** AES-GCM envelope encryption; the key never leaves Android Keystore. No backup. */
class SecretVault(context: Context) : TrustedHosts {
    private val prefs = context.getSharedPreferences("encrypted_vault", Context.MODE_PRIVATE)
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("mangossh-v1", null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("mangossh-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun put(id: String, value: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(id.toByteArray())
        val envelope = cipher.iv + cipher.doFinal(value)
        check(prefs.edit().putString(id, Base64.encodeToString(envelope, Base64.NO_WRAP)).commit()) { "No se pudo guardar el secreto" }
    }
    @Synchronized fun get(id: String): ByteArray? {
        val value = prefs.getString(id, null) ?: return null
        val envelope = Base64.decode(value, Base64.NO_WRAP)
        require(envelope.size >= 28) { "Secreto dañado" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope, 0, 12))
        cipher.updateAAD(id.toByteArray())
        return cipher.doFinal(envelope, 12, envelope.size - 12)
    }
    fun remove(id: String) { check(prefs.edit().remove(id).commit()) }
    fun hasSecret(id: String) = prefs.contains("secret:$id")
    fun saveCredentials(profile: Profile, credentials: Credentials) {
        val json = org.json.JSONObject()
        json.put("type", profile.auth.name)
        when (credentials) {
            is Credentials.Password -> json.put("data", Base64.encodeToString(credentials.bytes, Base64.NO_WRAP))
            is Credentials.PrivateKey -> {
                json.put("data", Base64.encodeToString(credentials.bytes, Base64.NO_WRAP))
                json.put("pass", credentials.passphrase?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: "")
            }
        }
        put("secret:${profile.id}", json.toString().toByteArray())
    }
    fun credentials(profile: Profile): Credentials? {
        val data = get("secret:${profile.id}") ?: return null
        try {
            val json = org.json.JSONObject(data.toString(Charsets.UTF_8))
            if (json.getString("type") != profile.auth.name) return null
            val bytes = Base64.decode(json.getString("data"), Base64.NO_WRAP)
            return if (profile.auth == AuthType.PASSWORD) Credentials.Password(bytes) else Credentials.PrivateKey(
                bytes, json.optString("pass").takeIf { it.isNotEmpty() }?.let { Base64.decode(it, Base64.NO_WRAP) })
        } finally { data.fill(0) }
    }
    override fun fingerprint(endpoint: String): String? = get("host:$endpoint")?.toString(Charsets.UTF_8)
    override fun trust(identity: HostIdentity) = put("host:${identity.endpoint}", identity.sha256.toByteArray())
    fun trusted(): List<Pair<String, String>> = prefs.all.keys.filter { it.startsWith("host:") }.sorted().map {
        it.removePrefix("host:") to (get(it)?.toString(Charsets.UTF_8) ?: "")
    }
    fun forgetHost(endpoint: String) = remove("host:$endpoint")
}

private val Context.preferences by preferencesDataStore("settings")
data class Preferences(val theme: String = "Claro", val fontSize: Int = 16, val timeout: Int = 15, val saveOutput: Boolean = false)
class Settings(context: Context) {
    private val store = context.preferences
    val flow = store.data.map { p -> Preferences(p[stringPreferencesKey("theme")] ?: "Claro", p[intPreferencesKey("font")] ?: 16,
        p[intPreferencesKey("timeout")] ?: 15, p[booleanPreferencesKey("output")] ?: false) }
    suspend fun update(p: Preferences) { store.edit {
        it[stringPreferencesKey("theme")] = p.theme
        it[intPreferencesKey("font")] = p.fontSize.coerceIn(10, 28)
        it[intPreferencesKey("timeout")] = p.timeout.coerceIn(1, 60)
        it[booleanPreferencesKey("output")] = p.saveOutput
    } }
}
