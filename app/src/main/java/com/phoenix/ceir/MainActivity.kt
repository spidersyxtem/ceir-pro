package com.phoenix.ceir

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
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
    [span_2](start_span)private val client = OkHttpClient()[span_2](end_span)
    [span_3](start_span)private val mainHandler = Handler(Looper.getMainLooper())[span_3](end_span)
    [span_4](start_span)private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"[span_4](end_span)

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        [span_5](start_span)setContentView(R.layout.activity_main)[span_5](end_span)

        [span_6](start_span)webView = findViewById(R.id.webView)[span_6](end_span)

        webView.settings.apply {
            [span_7](start_span)javaScriptEnabled = true[span_7](end_span)
            [span_8](start_span)domStorageEnabled = true[span_8](end_span)
            [span_9](start_span)userAgentString = userAgent[span_9](end_span)
            [span_10](start_span)mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW[span_10](end_span)
        }

        // WebApp ထဲက B4A.CallSub(...) ကို တိုက်ရိုက်လက်ခံဖမ်းပေးမည့် Interface ဆောက်ခြင်း
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun CallSub(sub: String, vararg args: String) {
                mainHandler.post {
                    when (sub) {
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
                [span_11](start_span)if (url != null && url.contains("ceir.gov.mm")) {[span_11](end_span)
                    [span_12](start_span)mainHandler.postDelayed({[span_12](end_span)
                        [span_13](start_span)injectUI()[span_13](end_span)
                    [span_14](start_span)}, 2000)[span_14](end_span)
                }
            }
        }

        [span_15](start_span)CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)[span_15](end_span)
        [span_16](start_span)webView.loadUrl("https://ceir.gov.mm")[span_16](end_span)
    }

    private fun injectUI() {
        try {
            [span_17](start_span)val html = assets.open("data.html").bufferedReader().use { it.readText() }[span_17](end_span)
            webView.loadDataWithBaseURL(
                [span_18](start_span)"https://ceir.gov.mm",[span_18](end_span)
                [span_19](start_span)html,[span_19](end_span)
                [span_20](start_span)"text/html",[span_20](end_span)
                [span_21](start_span)"UTF-8",[span_21](end_span)
                [span_22](start_span)null[span_22](end_span)
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "Assets data.html ဖတ်မရပါ: ${e.message}")
            callJs("engineError('Local UI UI load failed')")
        }
    }

    private fun requestChallenge() {
        [span_23](start_span)val cookies = CookieManager.getInstance().getCookie("https://ceir.gov.mm") ?: ""[span_23](end_span)
        [span_24](start_span)val request = Request.Builder()[span_24](end_span)
            [span_25](start_span).url("https://ceir.gov.mm/openapi/API/Auth/altcha/altcha")[span_25](end_span)
            [span_26](start_span).header("User-Agent", userAgent)[span_26](end_span)
            [span_27](start_span).header("Cookie", cookies)[span_27](end_span)
            [span_28](start_span).header("Referer", "https://ceir.gov.mm/")[span_28](end_span)
            [span_29](start_span).build()[span_29](end_span)

        [span_30](start_span)client.newCall(request).enqueue(object : Callback {[span_30](end_span)
            [span_31](start_span)override fun onFailure(call: Call, e: IOException) {[span_31](end_span)
                [span_32](start_span)callJs("engineError('network error')")[span_32](end_span)
            }
            [span_33](start_span)override fun onResponse(call: Call, response: Response) {[span_33](end_span)
                [span_34](start_span)val body = response.body?.string() ?: ""[span_34](end_span)
                [span_35](start_span)val b64 = Base64.encodeToString(body.toByteArray(), Base64.NO_WRAP)[span_35](end_span)
                [span_36](start_span)callJs("receiveChallenge('$b64')")[span_36](end_span)
            }
        })
    }

    private fun solveAltcha(salt: String, challenge: String, maxNumber: Long) {
        Thread {
            try {
                [span_37](start_span)val md = MessageDigest.getInstance("SHA-256")[span_37](end_span)
                [span_38](start_span)var solved = -1L[span_38](end_span)
                [span_39](start_span)for (n in 0..maxNumber) {[span_39](end_span)
                    [span_40](start_span)val hash = md.digest((salt + n).toByteArray())[span_40](end_span)
                        [span_41](start_span).joinToString("") { "%02x".format(it) }[span_41](end_span)
                    [span_42](start_span)if (hash == challenge) {[span_42](end_span)
                        [span_43](start_span)solved = n[span_43](end_span)
                        [span_44](start_span)break[span_44](end_span)
                    }
                }
                [span_45](start_span)callJs("submitVerification($solved)")[span_45](end_span)
            } catch (e: Exception) {
                callJs("engineError('Altcha solver error')")
            }
        [span_46](start_span)}.start()[span_46](end_span)
    }

    private fun verifyImei(b64Payload: String, imei: String) {
        [span_47](start_span)val cookies = CookieManager.getInstance().getCookie("https://ceir.gov.mm") ?: ""[span_47](end_span)
        [span_48](start_span)val payloadStr = String(Base64.decode(b64Payload, Base64.DEFAULT))[span_48](end_span)
        [span_49](start_span)val altchaToken = java.net.URLEncoder.encode(payloadStr, "UTF-8")[span_49](end_span)

        [span_50](start_span)val verifyBody = JSONObject().put("imei", imei).toString()[span_50](end_span)
            [span_51](start_span).toRequestBody("application/json".toMediaType())[span_51](end_span)

        [span_52](start_span)val verifyReq = Request.Builder()[span_52](end_span)
            [span_53](start_span).url("https://ceir.gov.mm/openapi/API/IMEI/Verify?altcha=$altchaToken&imei=$imei")[span_53](end_span)
            [span_54](start_span).post(verifyBody)[span_54](end_span)
            [span_55](start_span).header("User-Agent", userAgent)[span_55](end_span)
            [span_56](start_span).header("Cookie", cookies)[span_56](end_span)
            [span_57](start_span).header("Referer", "https://ceir.gov.mm/")[span_57](end_span)
            [span_58](start_span).build()[span_58](end_span)

        [span_59](start_span)val deviceReq = Request.Builder()[span_59](end_span)
            [span_60](start_span).url("https://ceir.gov.mm/openapi/API/Device/personal-device-info?altcha=$altchaToken")[span_60](end_span)
            [span_61](start_span).header("User-Agent", userAgent)[span_61](end_span)
            [span_62](start_span).header("Cookie", cookies)[span_62](end_span)
            [span_63](start_span).header("Referer", "https://ceir.gov.mm/")[span_63](end_span)
            [span_64](start_span).build()[span_64](end_span)

        [span_65](start_span)client.newCall(verifyReq).enqueue(object : Callback {[span_65](end_span)
            [span_66](start_span)override fun onFailure(call: Call, e: IOException) {[span_66](end_span)
                [span_67](start_span)callJs("engineError('verify failed')")[span_67](end_span)
            }
            [span_68](start_span)override fun onResponse(call: Call, response: Response) {[span_68](end_span)
                [span_69](start_span)val vBody = response.body?.string() ?: ""[span_69](end_span)
                [span_70](start_span)val b64V = Base64.encodeToString(vBody.toByteArray(), Base64.NO_WRAP)[span_70](end_span)

                [span_71](start_span)client.newCall(deviceReq).enqueue(object : Callback {[span_71](end_span)
                    [span_72](start_span)override fun onFailure(call: Call, e: IOException) {[span_72](end_span)
                        [span_73](start_span)val b64P = Base64.encodeToString("SKIP".toByteArray(), Base64.NO_WRAP)[span_73](end_span)
                        [span_74](start_span)callJs("receiveFinalResult('$b64V','$b64P')")[span_74](end_span)
                    }
                    [span_75](start_span)override fun onResponse(call: Call, response: Response) {[span_75](end_span)
                        [span_76](start_span)val pBody = response.body?.string() ?: "SKIP"[span_76](end_span)
                        [span_77](start_span)val b64P = Base64.encodeToString(pBody.toByteArray(), Base64.NO_WRAP)[span_77](end_span)
                        [span_78](start_span)callJs("receiveFinalResult('$b64V','$b64P')")[span_78](end_span)
                    }
                })
            }
        })
    }

    private fun callJs(js: String) {
        [span_79](start_span)mainHandler.post { webView.evaluateJavascript(js, null) }[span_79](end_span)
    }
}
