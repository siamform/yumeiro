/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * `network_security_config.xml` trusts `@raw/isrg_root_x1` so Let's Encrypt sites work on Android 7.0.
 * The file once went missing from git (the repo ignores `*.pem`), which broke every clean build with
 * "resource raw/isrg_root_x1 not found". This pins the file to Let's Encrypt's published fingerprint.
 */
class BundledRootTest {
    private val pem = listOf("src/main/res/raw/isrg_root_x1.pem", "app/src/main/res/raw/isrg_root_x1.pem")
        .map(::File).first { it.exists() }

    @Test fun isrgRootX1IsTheRealRoot() {
        val cert = pem.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) } as X509Certificate
        val sha = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02X".format(it) }
        // https://letsencrypt.org/certificates/ — ISRG Root X1 (RSA 4096, self-signed)
        assertEquals("96BCEC06264976F37460779ACF28C5A7CFE8A3C0AAE11A8FFCEE05C0BDDF08C6", sha)
        assertEquals(cert.subjectX500Principal, cert.issuerX500Principal)
        assertTrue(cert.notAfter.time > System.currentTimeMillis())
    }

    @Test fun networkConfigStillReferencesIt() {
        val xml = File(pem.parentFile.parentFile, "xml/network_security_config.xml").readText()
        assertTrue(xml.contains("@raw/isrg_root_x1"))
        assertTrue("user-installed CAs must stay untrusted", !xml.contains("src=\"user\""))
    }
}
