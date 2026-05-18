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
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val mainHandler = Handler(Looper.getMainLooper())
    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    
    // UI တစ်ကြိမ်သာ Inject ဖြစ်စေရန် ထိန်းချုပ်မည့် Flag
    private var isUiInjected = false

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // သင့် Layout နာမည်အတိုင်း ပြင်ဆင်ထားပါသည်

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
                // UI မရှိသေးမှသာ တစ်ကြိမ်တည်း ဆွဲတင်ရန် သေချာစွာ စစ်ဆေးခြင်း (အော်တိုစာသားပျောက်ခြင်းမှ ကာကွယ်ရန်)
                if (url != null && url.contains("ceir.gov.mm") && !isUiInjected) {
                    isUiInjected = true
                    mainHandler.postDelayed({
                        injectUI()
                    }, 2000)
                }
            }
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.loadUrl("https://ceir.gov.mm")

        // စာရိုက်ရအဆင်ပြေစေရန် Focus ရယူခြင်းစနစ်
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

    private fun handleBridge(url: String) {
        try {
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
        } catch (e: Exception) {
            Log.e("MainActivity", "Bridge Payload error: ${e.message}")
        }
    }

    // 🛠️ WebView AJAX Fetch စနစ်ဖြင့် Challenge တောင်းဆိုခြင်း (Cloudflare ကျော်ပြီးသားဖြစ်သည်)
    private fun requestChallenge() {
        val jsFetch = """
            fetch('https://ceir.gov.mm/openapi/API/Auth/altcha/altcha')
                .then(res => res.text())
                .then(text => {
                    var b64 = btoa(unescape(encodeURIComponent(text)));
                    receiveChallenge(b64);
                })
                .catch(err => {
                    engineError('network error');
                });
        """.trimIndent()
        mainHandler.post { webView.evaluateJavascript(jsFetch, null) }
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
                callJs("submitVerification(" + solved + ")")
            } catch (e: Exception) {
                callJs("engineError('Altcha solver error')")
            }
        }.start()
    }

    // 🛠️ OkHttp အစား Cloudflare Block လုံးဝမဖြစ်စေမည့် WebView Engine ပါဝင်သော IMEI Verification Logic အသစ်
    private fun verifyImei(b64Payload: String, imei: String) {
        val payloadStr = String(Base64.decode(b64Payload, Base64.DEFAULT))
        val altchaToken = java.net.URLEncoder.encode(payloadStr, "UTF-8")

        // WebView ရဲ့ JavaScript Fetch API ကို သုံးပြီး Verify နှင့် Device API (၂) ခုလုံးကို တပြိုင်တည်း တောင်းခိုင်းခြင်း Logic
        val jsVerifyFetch = """
            (function() {
                var verifyBody = JSON.stringify({ imei: "$imei" });
                
                // 1. IMEI Verify Request
                fetch('https://ceir.gov.mm/openapi/API/IMEI/Verify?altcha=$altchaToken&imei=$imei', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: verifyBody
                })
                .then(res => res.text())
                .then(vText => {
                    var b64V = btoa(unescape(encodeURIComponent(vText)));
                    
                    // 2. Personal Device Info Request
                    fetch('https://ceir.gov.mm/openapi/API/Device/personal-device-info?altcha=$altchaToken')
                    .then(res2 => res2.text())
                    .then(pText => {
                        var b64P = btoa(unescape(encodeURIComponent(pText)));
                        receiveFinalResult(b64V, b64P);
                    })
                    .catch(err2 => {
                        var b64P = btoa(unescape(encodeURIComponent("SKIP")));
                        receiveFinalResult(b64V, b64P);
                    });
                })
                .catch(err => {
                    engineError('verify failed');
                });
            })();
        """.trimIndent()

        mainHandler.post { webView.evaluateJavascript(jsVerifyFetch, null) }
    }

    private fun callJs(js: String) {
        mainHandler.post { webView.evaluateJavascript(js, null) }
    }
}
