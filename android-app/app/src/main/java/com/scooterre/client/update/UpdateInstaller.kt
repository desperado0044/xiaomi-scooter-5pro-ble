package com.scooterre.client.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Why a downloaded update was not used. */
enum class UpdateProblem { DOWNLOAD, HASH, SIGNER, NOT_APK }

sealed interface UpdateDownloadResult {
    data class Ok(val file: File) : UpdateDownloadResult
    data class Failed(val problem: UpdateProblem) : UpdateDownloadResult
}

/**
 * "Download update" button: fetches the APK from this project's GitHub release page, checks it and
 * hands it to the system installer, which still asks the user to confirm. Nothing is installed
 * silently. The file is only offered to the installer if (1) it comes from [UpdateChecker.DOWNLOAD_PREFIX],
 * (2) its SHA-256 matches GitHub's digest when there is one, and (3) it is this app's package signed
 * with the same certificate as the installed app.
 */
object UpdateInstaller {
    private const val MAX_BYTES = 60L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun dir(context: Context) = File(context.cacheDir, "updates")

    /** Removes downloaded updates (called at app start: after an update the file is not needed any more). */
    fun cleanup(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Blocking - call off the main thread. [onProgress] gets 0..100 while downloading. */
    fun downloadAndVerify(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): UpdateDownloadResult {
        val url = info.apkUrl
        if (url == null || !url.startsWith(UpdateChecker.DOWNLOAD_PREFIX)) return UpdateDownloadResult.Failed(UpdateProblem.DOWNLOAD)
        val folder = dir(context).apply { mkdirs() }
        cleanup(context)
        val target = File(folder, "scooter-${info.version}.apk")
        val part = File(folder, "scooter-${info.version}.apk.part")
        try {
            val request = Request.Builder().url(url).header("User-Agent", "scooter-client-update").build()
            client.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) return UpdateDownloadResult.Failed(UpdateProblem.DOWNLOAD)
                val total = body.contentLength()
                if (total > MAX_BYTES) return UpdateDownloadResult.Failed(UpdateProblem.DOWNLOAD)
                var read = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            read += n
                            if (read > MAX_BYTES) return UpdateDownloadResult.Failed(UpdateProblem.DOWNLOAD)
                            output.write(buf, 0, n)
                            if (total > 0) {
                                val percent = (read * 100 / total).toInt()
                                if (percent != lastPercent) { lastPercent = percent; onProgress(percent) }
                            }
                        }
                    }
                }
            }
            if (!part.renameTo(target)) return UpdateDownloadResult.Failed(UpdateProblem.DOWNLOAD)
        } catch (e: Exception) {
            part.delete()
            return UpdateDownloadResult.Failed(UpdateProblem.DOWNLOAD)
        }
        val problem = verify(context, target, info.apkSha256)
        if (problem != null) {
            target.delete()
            return UpdateDownloadResult.Failed(problem)
        }
        return UpdateDownloadResult.Ok(target)
    }

    private fun signerHashes(info: PackageInfo?): Set<String> {
        if (info == null) return emptySet()
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()
        return signatures.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private val signatureFlags: Int
        get() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

    /** Null if the file may be installed, otherwise what is wrong with it. */
    fun verify(context: Context, file: File, expectedSha256: String?): UpdateProblem? {
        if (expectedSha256 != null && !sha256Hex(file).equals(expectedSha256, ignoreCase = true)) return UpdateProblem.HASH
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, signatureFlags)
        if (archive == null || archive.packageName != context.packageName) return UpdateProblem.NOT_APK
        val installed = runCatching { pm.getPackageInfo(context.packageName, signatureFlags) }.getOrNull()
        val downloadedSigners = signerHashes(archive)
        if (downloadedSigners.isEmpty() || downloadedSigners != signerHashes(installed)) return UpdateProblem.SIGNER
        return null
    }

    enum class InstallStart { OPENED, NEEDS_PERMISSION }

    const val ACTION_INSTALL_RESULT = "com.scooterre.client.UPDATE_INSTALL_RESULT"

    /**
     * Hands [file] to the system's PackageInstaller (no app chooser); the system then shows its normal
     * confirmation dialog - see [UpdateInstallReceiver]. If this app may not install apps yet, only says so.
     */
    fun install(context: Context, file: File): InstallStart {
        if (!context.packageManager.canRequestPackageInstalls()) return InstallStart.NEEDS_PERMISSION
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("update.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val result = Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_INSTALL_RESULT)
            // Mutable: the installer adds the status and the confirmation intent to it.
            val pending = PendingIntent.getBroadcast(context, sessionId, result, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
        return InstallStart.OPENED
    }

    /** The system page where the user allows this app to install apps. */
    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
