package com.phoenix.ceir

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = userAgent
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("b4a://call")) {
                    handleBridge(url)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url?.contains("ceir.gov.mm") == true) {
                    injectUI()
                }
            }
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.loadUrl("https://ceir.gov.mm")
    }

    private fun injectUI() {
        val html = assets.open("data.html").bufferedReader().use { it.readText() }
        val encoded = Base64.encodeToString(html.toByteArray(), Base64.NO_WRAP)
        webView.evaluateJavascript(
            "document.open(); document.write(atob('" + encoded + "')); document.close();",
            null
        )
    }

    private fun handleBridge(url: String) {
        val uri = android.net.Uri.parse(url)
        val dataB64 = uri.getQueryParameter("data") ?: return
        val json = JSONObject(String(Base64.decode(dataB64, Base64.DEFAULT)))
        val sub = json.getString("sub")
        val args = json.optJSONArray("args")

        when (sub) {
            "Bridge_RequestChallenge" -> requestChallenge()
            "StartAltchaSolver" -> {
                val salt = args?.getString(0) ?: return
                val challenge = args.getString(1)
                val maxNumber = args.getString(2).toLong()
                solveAltcha(salt, challenge, maxNumber)
            }
            "Bridge_VerifyImei" -> {
                val b64Payload = args?.getString(0) ?: return
                val imei = args.getString(1)
                verifyImei(b64Payload, imei)
            }
        }
    }

    private fun requestChallenge() {
        val cookies = CookieManager.getInstance().getCookie("https://ceir.gov.mm") ?: ""
        val request = Request.Builder()
            .url("https://ceir.gov.mm/openapi/API/Auth/altcha/altcha")
            .header("User-Agent", userAgent)
            .header("Cookie", cookies)
            .header("Referer", "https://ceir.gov.mm/")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callJs("engineError('network error')")
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val b64 = Base64.encodeToString(body.toByteArray(), Base64.NO_WRAP)
                callJs("receiveChallenge('" + b64 + "')")
            }
        })
    }

    private fun solveAltcha(salt: String, challenge: String, maxNumber: Long) {
        Thread {
            val md = MessageDigest.getInstance("SHA-256")
            var solved = -1L
            for (n in 0..maxNumber) {
                val hash = md.digest((salt + n).toByteArray())
                    .joinToString("") { "%02x".format(it) }
                if (hash == challenge) { solved = n; break }
            }
            callJs("submitVerification(" + solved + ")")
        }.start()
    }

    private fun verifyImei(b64Payload: String, imei: String) {
        val cookies = CookieManager.getInstance().getCookie("https://ceir.gov.mm") ?: ""
        val payloadStr = String(Base64.decode(b64Payload, Base64.DEFAULT))
        val altchaToken = java.net.URLEncoder.encode(payloadStr, "UTF-8")

        val verifyBody = JSONObject().put("imei", imei).toString()
            .toRequestBody("application/json".toMediaType())

        val verifyReq = Request.Builder()
            .url("https://ceir.gov.mm/openapi/API/IMEI/Verify?altcha=" + altchaToken + "&imei=" + imei)
            .post(verifyBody)
            .header("User-Agent", userAgent)
            .header("Cookie", cookies)
            .header("Referer", "https://ceir.gov.mm/")
            .build()

        val deviceReq = Request.Builder()
            .url("https://ceir.gov.mm/openapi/API/Device/personal-device-info?altcha=" + altchaToken)
            .header("User-Agent", userAgent)
            .header("Cookie", cookies)
            .header("Referer", "https://ceir.gov.mm/")
            .build()

        client.newCall(verifyReq).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callJs("engineError('verify failed')")
            }
            override fun onResponse(call: Call, response: Response) {
                val vBody = response.body?.string() ?: ""
                val b64V = Base64.encodeToString(vBody.toByteArray(), Base64.NO_WRAP)
                client.newCall(deviceReq).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        val b64P = Base64.encodeToString("SKIP".toByteArray(), Base64.NO_WRAP)
                        callJs("receiveFinalResult('" + b64V + "','" + b64P + "')")
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val pBody = response.body?.string() ?: "SKIP"
                        val b64P = Base64.encodeToString(pBody.toByteArray(), Base64.NO_WRAP)
                        callJs("receiveFinalResult('" + b64V + "','" + b64P + "')")
                    }
                })
            }
        })
    }

    private fun callJs(js: String) {
        mainHandler.post { webView.evaluateJavascript(js, null) }
    }
}
