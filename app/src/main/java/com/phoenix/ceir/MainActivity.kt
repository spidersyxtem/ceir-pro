package com.phoenix.ceir

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    private var webView: WebView? = null
    private var resultLayout: LinearLayout? = null
    private var txtBrandInfo: TextView? = null
    private var txtAprilStatus: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        resultLayout = findViewById(R.id.resultLayout)
        txtBrandInfo = findViewById(R.id.txtBrandInfo)
        txtAprilStatus = findViewById(R.id.txtAprilStatus)
        val btnBack: Button = findViewById(R.id.btnBack)

        setupWebView()
        btnBack.setOnClickListener { showWebsite() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings = webView?.settings
        webSettings?.javaScriptEnabled = true
        webSettings?.domStorageEnabled = true
        // CEIR website က Browser အစစ်ဖြစ်မှ API ခွင့်ပြုတာမို့ UserAgent ကို အသေအချာ ထည့်ထားပါတယ်
        webSettings?.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        
        webView?.addJavascriptInterface(this, "AndroidBridge")
        
        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Website ထဲမှာ IMEI စစ်ပြီးတာနဲ့ API response တွေကို ဖမ်းယူမယ့် Script
                // ဒီနေရာမှာ Webview က API ကို လှမ်းခေါ်ပြီး ရလာတဲ့ JSON ကို Android ဆီ ပို့ပေးမှာပါ
                val script = """
                    (function() {
                        const originalFetch = window.fetch;
                        window.fetch = function() {
                            return originalFetch.apply(this, arguments).then(async (response) => {
                                const url = response.url;
                                if (url.includes('personal-device-info') || url.includes('IMEI/Verify')) {
                                    const clone = response.clone();
                                    const data = await clone.text();
                                    AndroidBridge.processApiResponse(url, data);
                                }
                                return response;
                            });
                        };
                    })();
                """.trimIndent()
                webView?.evaluateJavascript(script, null)
            }
        }
        webView?.loadUrl("https://www.ceir.gov.mm/check-status")
    }

    private var deviceDetails = ""
    private var taxDetails = ""

    @JavascriptInterface
    fun processApiResponse(url: String, data: String) {
        runOnUiThread {
            try {
                if (url.contains("personal-device-info")) {
                    val json = JSONObject(data)
                    val brand = json.optString("gsmaBrandName", "Unknown")
                    val model = json.optString("gsmaModelName", "Device")
                    val internalModel = json.optString("standardisedDeviceModel", "")
                    deviceDetails = "$model\n$brand / $internalModel"
                } 
                else if (url.contains("IMEI/Verify")) {
                    val json = JSONObject(data)
                    val list = json.optJSONArray("IMEI_CHECK_LIST")
                    if (list != null && list.length() > 0) {
                        val item = list.getJSONObject(0)
                        val payment = item.optString("paymentState", "UNKNOWN")
                        val block = item.optString("blockState", "UNKNOWN")
                        val imei = item.optString("IMEI", "")
                        
                        taxDetails = "IMEI: $imei\nTAX: ${payment.replace("_", " ")}\nSTATUS: ${block.replace("_", " ")}"
                        
                        // အချက်အလက် ၂ မျိုးလုံး ရပြီဆိုရင် Result Page ပြပါမယ်
                        showResult(payment == "PAID", block == "UNBLOCKED")
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun showResult(isPaid: Boolean, isAllowed: Boolean) {
        webView?.visibility = View.GONE
        resultLayout?.visibility = View.VISIBLE
        
        txtBrandInfo?.text = "$deviceDetails\n\n$taxDetails"
        
        if (isPaid && isAllowed) {
            txtAprilStatus?.text = "VERIFIED: CLEAN DEVICE ✨"
            txtAprilStatus?.setBackgroundColor(Color.parseColor("#10B981"))
        } else {
            txtAprilStatus?.text = "ALERT: CHECK TAX STATUS ⚠️"
            txtAprilStatus?.setBackgroundColor(Color.parseColor("#E11D48"))
        }
        txtAprilStatus?.setTextColor(Color.WHITE)
    }

    private fun showWebsite() {
        resultLayout?.visibility = View.GONE
        webView?.visibility = View.VISIBLE
        webView?.reload()
    }
}
