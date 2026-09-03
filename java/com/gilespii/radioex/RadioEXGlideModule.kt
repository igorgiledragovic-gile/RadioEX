package com.gilespii.radioex

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import okhttp3.OkHttpClient
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import android.util.Log

/**
 * Custom Glide Module that configures Glide to use an insecure OkHttpClient
 * for loading images from servers with invalid SSL certificates (like Radio S).
 */
@GlideModule
class RadioEXGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        super.registerComponents(context, glide, registry)
        
        try {
            // Create insecure OkHttpClient for Glide
            val insecureClient = createInsecureClient()
            
            // Replace the default HTTP loader with our insecure one
            registry.replace(
                GlideUrl::class.java,
                InputStream::class.java,
                OkHttpUrlLoader.Factory(insecureClient)
            )
            
            Log.d("GlideModule", "Insecure OkHttpClient registered successfully")
        } catch (e: Exception) {
            Log.e("GlideModule", "Failed to register insecure client", e)
        }
    }

    private fun createInsecureClient(): OkHttpClient {
        return try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<X509TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory = sslContext.socketFactory

            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0])
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            Log.e("GlideModule", "Failed to create insecure client, using default", e)
            OkHttpClient()
        }
    }
}
