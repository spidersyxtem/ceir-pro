package com.phoenix.ceir

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
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

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "ClickableViewAccessibility")
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

        // မူရင်း Logic အတိုင်း တိကျစွာ အလုပ်လုပ်မည့် JavaScript Interface
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun CallSub(sub: String, vararg args: String) {
                mainHandler.post {
                    when (sub) {
                        "Bridge_RequestChallenge" -> requestChallenge()
                        "StartAltchaSolver" -> {
                            if (args.size >= 3) {
                                val salt = args[0]
                                val challenge = args[1]
                                val maxNumber = args[2].toLongOrNull() ?: 1000000L
                                solveAltcha(salt, challenge, maxNumber)
                            }
                        }
                        "Bridge_VerifyImei" -> {
                            if (args.size >= 2) {
                                val b64Payload = args[0]
                                val imei = args[1]
                                verifyImei(b64Payload, imei)
                            }
                        }
                    }
                }
            }
        }, "B4A")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && url.contains("ceir.gov.mm")) {
                    mainHandler.postDelayed({
                        injectUI()
                    }, 2000)
                }
            }
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.loadUrl("https://ceir.gov.mm")

        // WebView focus နှင့် keyboard ပွင့်စေရန် လုပ်ဆောင်ချက်များ
        webView.requestFocus()
        webView.requestFocusFromTouch()
        
        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_UP -> {
                    if (!v.hasFocus()) {
                        v.requestFocus()
                    }
                }
            }
            false
        }
    }

    private fun injectUI() {
        try {
            val html = assets.open("data.html").bufferedReader().use { it.readText() }
            webView.loadDataWithBaseURL(
                "https://ceir.gov.mm",
                html,
                "text/html",
                "UTF-8",
                null
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "Assets data.html load failed: ${e.message}")
            callJs("engineError('Local UI load failed')")
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
                callJs("receiveChallenge('$b64')")
            }
        })
    }

    private fun solveAltcha(salt: String, challenge: String, maxNumber: Long) {
        Thread {
            try {
                val md = MessageDigest.getInstance("SHA-256")
                var solved = -1L
                for (n in 0..maxNumber) {
                    val hash = md.digest((salt + n).toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    if (hash == challenge) { 
                        solved = n
                        break 
                    }
                }
                callJs("submitVerification($solved)")
            } catch (e: Exception) {
                callJs("engineError('Altcha solver error')")
            }
        }.start()
    }

    private fun verifyImei(b64Payload: String, imei: String) {
        val cookies = CookieManager.getInstance().getCookie("https://ceir.gov.mm") ?: ""
        val payloadStr = String(Base64.decode(b64Payload, Base64.DEFAULT))
        val altchaToken = java.net.URLEncoder.encode(payloadStr, "UTF-8")

        val verifyBody = JSONObject().put("imei", imei).toString()
            .toRequestBody("application/json".toMediaType())

        val verifyReq = Request.Builder()
            .url("https://ceir.gov.mm/openapi/API/IMEI/Verify?altcha=$altchaToken&imei=$imei")
            .post(verifyBody)
            .header("User-Agent", userAgent)
            .header("Cookie", cookies)
            .header("Referer", "https://ceir.gov.mm/")
            .build()

        val deviceReq = Request.Builder()
            .url("https://ceir.gov.mm/openapi/API/Device/personal-device-info?altcha=$altchaToken")
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
                        callJs("receiveFinalResult('$b64V','$b64P')")
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val pBody = response.body?.string() ?: "SKIP"
                        val b64P = Base64.encodeToString(pBody.toByteArray(), Base64.NO_WRAP)
                        callJs("receiveFinalResult('$b64V','$b64P')")
                    }
                })
            }
        })
    }

    private fun callJs(js: String) {
        mainHandler.post { webView.evaluateJavascript(js, null) }
    }
}
