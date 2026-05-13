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
        webView?.settings?.javaScriptEnabled = true
        webView?.settings?.domStorageEnabled = true
        webView?.settings?.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        
        webView?.addJavascriptInterface(this, "AndroidBridge")
        
        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val script = """
                    (function() {
                        var checkResult = setInterval(function() {
                            var bodyText = document.body.innerText;
                            if (bodyText.includes('Finished') || bodyText.includes('Tax Payment Status')) {
                                
                                // ၁။ IMEI နံပါတ်ကို ပိုမိုတိကျစွာ ရှာဖွေခြင်း (Regex ပိုကောင်းအောင် ပြင်ထားသည်)
                                var imeiMatch = bodyText.match(/\b\d{15}\b/);
                                var imeiValue = imeiMatch ? imeiMatch[0] : "Check Website for IMEI";

                                // ၂။ Tax နှင့် Status ကို စစ်ဆေးခြင်း
                                var isPaid = bodyText.toLowerCase().includes('paid');
                                var isAllowed = bodyText.toLowerCase().includes('allowed');

                                // ၃။ အချက်အလက်များကို ပို့လွှတ်ခြင်း
                                AndroidBridge.onFinalData(imeiValue, isPaid, isAllowed, bodyText);
                                clearInterval(checkResult);
                            }
                        }, 1000);
                    })();
                """.trimIndent()
                webView?.evaluateJavascript(script, null)
            }
        }
        webView?.loadUrl("https://ceir.gov.mm/check-status")
    }

    @JavascriptInterface
    fun onFinalData(imei: String, isPaid: Boolean, isAllowed: Boolean, rawText: String) {
        runOnUiThread {
            webView?.visibility = View.GONE
            resultLayout?.visibility = View.VISIBLE
            
            val taxIcon = if (isPaid) "PAID ✅" else "UNPAID ❌"
            val statusIcon = if (isAllowed) "ALLOWED ✅" else "BLOCKED ❌"
            
            // IMEI အပြင် အခြားပါလာနိုင်သည့် Brand/Model ကိုပါ စာသားထဲတွင် ရှာဖွေပြသခြင်း
            txtBrandInfo?.text = "IMEI: $imei\nTAX: $taxIcon\nSTATUS: $statusIcon"
            
            if (isPaid && isAllowed) {
                txtAprilStatus?.text = "VERIFIED: CLEAN DEVICE ✨"
                txtAprilStatus?.setBackgroundColor(Color.parseColor("#10B981"))
            } else {
                txtAprilStatus?.text = "ALERT: CHECK TAX STATUS ⚠️"
                txtAprilStatus?.setBackgroundColor(Color.parseColor("#E11D48"))
            }
            txtAprilStatus?.setTextColor(Color.WHITE)
        }
    }

    private fun showWebsite() {
        resultLayout?.visibility = View.GONE
        webView?.visibility = View.VISIBLE
        webView?.reload()
    }

    override fun onBackPressed() {
        if (resultLayout?.visibility == View.VISIBLE) showWebsite()
        else if (webView?.canGoBack() == true) webView?.goBack()
        else super.onBackPressed()
    }
}
