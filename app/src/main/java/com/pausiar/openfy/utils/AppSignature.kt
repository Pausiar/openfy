package com.pausiar.openfy.utils

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * Huella SHA-256 del certificado con el que está firmada la app instalada.
 *
 * Openfy se distribuye fuera de Google Play, así que el usuario necesita una forma
 * de comprobar que el APK que ha instalado es el oficial. Esta huella se publica en
 * la web (https://pausiar.github.io/openfy/) y dentro de la propia app, en
 * "Ajustes → Información legal", para que ambos valores coincidan.
 */
object AppSignature {

    /**
     * Devuelve las huellas SHA-256 (en mayúsculas y separadas por ':') de los
     * certificados de firma de la app instalada. Lista vacía si no se puede leer.
     */
    fun sha256Fingerprints(context: Context): List<String> {
        val packageName = context.packageName
        val signingInfo = try {
            context.packageManager
                .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
        } catch (error: PackageManager.NameNotFoundException) {
            return emptyList()
        } catch (error: SecurityException) {
            return emptyList()
        } ?: return emptyList()

        val signers = signingInfo.apkContentsSigners ?: signingInfo.signingCertificateHistory ?: return emptyList()
        return signers.map { signer ->
            MessageDigest.getInstance("SHA-256")
                .digest(signer.toByteArray())
                .joinToString(":") { byte -> "%02X".format(byte) }
        }
    }

    /** Huella principal, o `null` si la app no está firmada o no se puede leer. */
    fun primaryFingerprint(context: Context): String? = sha256Fingerprints(context).firstOrNull()
}
