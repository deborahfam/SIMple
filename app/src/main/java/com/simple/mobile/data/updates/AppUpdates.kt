package com.simple.mobile.data.updates

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simple.mobile.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseApk(val code: Int, val url: String)
data class UpdateState(val busy: Boolean = false, val message: String = "Busca nuevas versiones en GitHub cuando lo necesites.", val release: ReleaseApk? = null, val ready: Boolean = false,
    val downloading: Boolean = false, val downloadedBytes: Long = 0, val totalBytes: Long? = null)

class AppUpdates(app: Application) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private val prefs = app.getSharedPreferences("update_preferences", android.content.Context.MODE_PRIVATE)
    private val autoMutable = MutableStateFlow((BuildConfig.DIRECT_DISTRIBUTION && prefs.getBoolean("automatic", false)))
    val automatic = autoMutable.asStateFlow()
    private var downloadControl: DownloadControl? = null
    @Volatile private var downloadConnection: HttpURLConnection? = null
    private val partial: File get() = File(apk.parentFile, "update.apk.part")
    init {
        if (BuildConfig.DIRECT_DISTRIBUTION) {
            mutable.value = UpdateState(busy = true, message = "Comprobando actualización guardada…")
            viewModelScope.launch {
                val cached = withContext(Dispatchers.IO) {
                    partial.delete()
                    cachedUpdate()
                }
                mutable.value = cached ?: UpdateState()
                if (cached == null) checkOnOpen()
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun cachedUpdate(): UpdateState? {
        val code = CachedApk.recover(apk,
            readCode = { getApplication<Application>().packageManager.getPackageArchiveInfo(it.path, 0)?.versionCode },
            validate = { expected, file -> validate(expected, file) })
        if (code == null) {
            prefs.edit().remove("cached_url").apply()
            return null
        }
        return UpdateState(release = ReleaseApk(code, prefs.getString("cached_url", "").orEmpty()), ready = true,
            message = "Actualización guardada y verificada. Puedes instalarla sin descargarla otra vez.")
    }

    fun cancelDownload() {
        val control = downloadControl ?: return
        control.cancel()
        mutable.update { it.copy(message = "Cancelando descarga…") }
        val connection = downloadConnection
        viewModelScope.launch(Dispatchers.IO) { connection?.disconnect() }
    }

    fun setAutomatic(enabled: Boolean) {
        if (!BuildConfig.DIRECT_DISTRIBUTION) return
        prefs.edit().putBoolean("automatic", enabled).apply()
        autoMutable.value = enabled
        if (enabled) checkOnOpen()
    }
    fun checkOnOpen() {
        if (!BuildConfig.DIRECT_DISTRIBUTION) return
        val now = System.currentTimeMillis()
        val last = prefs.getLong("last_attempt", 0)
        if (autoMutable.value && !mutable.value.busy && (now < last || now - last >= 86_400_000L)) check()
    }
    val apk: File get() = File(getApplication<Application>().cacheDir, "updates/update.apk")
    fun check() {
        if (!BuildConfig.DIRECT_DISTRIBUTION) return
        if (mutable.value.busy) return
        val previous = mutable.value
        prefs.edit().putLong("last_attempt", System.currentTimeMillis()).apply()
        mutable.value = UpdateState(busy = true, message = "Buscando actualizaciones…")
        viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    val json = request("https://api.github.com/repos/deborahfam/SIMple/releases/latest") { connection ->
                        val bytes = connection.inputStream.use { it.readBytesLimited(1_000_000) }
                        JSONObject(String(bytes, Charsets.UTF_8))
                    }
                    check(!json.optBoolean("draft") && !json.optBoolean("prerelease")) { "No hay una versión estable publicada." }
                    val assets = json.getJSONArray("assets")
                    (0 until assets.length()).mapNotNull { i ->
                        val asset = assets.getJSONObject(i)
                        val code = UpdatePolicy.code(asset.getString("name")) ?: return@mapNotNull null
                        val url = asset.getString("browser_download_url")
                        if (!UpdatePolicy.releaseUrl(url)) return@mapNotNull null
                        ReleaseApk(code, url)
                    }.maxByOrNull { it.code }
                }
                val cached = withContext(Dispatchers.IO) { cachedUpdate() }
                mutable.value = when {
                    cached != null && (release == null || cached.release!!.code >= release.code) -> cached
                    release == null -> UpdateState(message = "La publicación no contiene un APK compatible (SIMple-versionCode.apk).")
                    release.code <= BuildConfig.VERSION_CODE -> UpdateState(message = "Tienes la versión más reciente publicada.")
                    else -> UpdateState(message = "Nueva compilación disponible: ${release.code}.", release = release)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = previous.copy(busy = false, message = error(e)) }
        }
    }
    fun download() {
        if (!BuildConfig.DIRECT_DISTRIBUTION || mutable.value.busy) return
        val release = mutable.value.release ?: return
        if (!UpdatePolicy.releaseUrl(release.url)) return
        val control = DownloadControl()
        downloadControl = control
        mutable.value = mutable.value.copy(busy = true, ready = false, downloading = true,
            downloadedBytes = 0, totalBytes = null, message = "Descargando APK…")
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apk.parentFile!!.mkdirs()
                    try {
                        request(release.url, control) { connection ->
                            connection.inputStream.use { input -> partial.outputStream().use { output ->
                                ApkDownload.copy(input, output, connection.contentLengthLong, control) { count, size ->
                                    mutable.update { it.copy(downloadedBytes = count, totalBytes = size) }
                                }
                            } }
                        }
                        control.check()
                        mutable.update { it.copy(message = "Verificando APK…") }
                        validate(release.code, partial)
                        control.check()
                        check(partial.renameTo(apk)) { "No se pudo guardar la actualización." }
                        prefs.edit().putString("cached_url", release.url).apply()
                    } finally { partial.delete() }
                }
                // Cancellation may arrive after verification; the cached file remains independently verifiable.
                control.check()
                mutable.value = mutable.value.copy(busy = false, ready = true, downloading = false,
                    message = "APK verificado. Continúa con el instalador de Android.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val cancelled = try { control.check(); false } catch (_: DownloadCancelled) { true }
                mutable.value = mutable.value.copy(busy = false, ready = false, downloading = false,
                    downloadedBytes = 0, totalBytes = null,
                    message = if (cancelled) "Descarga cancelada. Puedes volver a intentarlo." else error(e))
            } finally { downloadControl = null }
        }
    }
    @Suppress("DEPRECATION")
    fun validate(expectedCode: Int, file: File = apk) {
        check(BuildConfig.DIRECT_DISTRIBUTION) { "Las actualizaciones de esta edición se gestionan desde Google Play." }
        val pm = getApplication<Application>().packageManager
        val flags = if (android.os.Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = pm.getPackageArchiveInfo(file.path, flags) ?: error("El archivo no es un APK válido.")
        val current = pm.getPackageInfo(BuildConfig.APPLICATION_ID, flags)
        check(candidate.packageName == BuildConfig.APPLICATION_ID && candidate.versionCode == expectedCode && candidate.versionCode > current.versionCode) { "El APK no corresponde a una actualización de SIMple." }
        val compatible = if (android.os.Build.VERSION.SDK_INT >= 28) {
            val installed = current.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            val downloaded = candidate.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            val history = candidate.signingInfo?.signingCertificateHistory?.map { it.toCharsString() }.orEmpty()
            UpdatePolicy.signersCompatible(installed, downloaded, history)
        } else {
            val installed = current.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
            val downloaded = candidate.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
            installed.isNotEmpty() && installed == downloaded
        }
        check(compatible) { "La firma no corresponde a esta instalación ni a una rotación autorizada. No desinstales la aplicación." }
    }
    private fun error(e: Exception): String = if (e is IllegalStateException) e.message ?: "No se pudo verificar la actualización."
        else "No se pudo conectar o descargar. Comprueba Internet e inténtalo de nuevo."

    private fun <T> request(url: String, control: DownloadControl? = null, read: (HttpURLConnection) -> T): T {
        var next = url
        repeat(6) {
            control?.check()
            check(UpdatePolicy.downloadHost(next)) { "Dirección de descarga no permitida." }
            val connection = URL(next).openConnection() as HttpURLConnection
            if (control != null) downloadConnection = connection
            try {
                control?.check()
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "SIMple/${BuildConfig.VERSION_NAME}")
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    next = URL(URL(next), connection.getHeaderField("Location") ?: error("Redirección inválida.")).toString()
                } else {
                    check(code != 404) { "No hay una publicación pública disponible. El repositorio debe publicar un APK en GitHub Releases." }
                    check(code == 200) { "GitHub no está disponible (HTTP $code). Inténtalo más tarde." }
                    return read(connection)
                }
            } finally {
                if (control != null) downloadConnection = null
                connection.disconnect()
            }
        }
        error("Demasiadas redirecciones.")
    }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            check(out.size() + n <= limit) { "Respuesta demasiado grande." }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
