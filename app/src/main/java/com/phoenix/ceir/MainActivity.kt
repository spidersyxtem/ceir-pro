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

        btnBack.setOnClickListener {
            showWebsite()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings = webView?.settings
        webSettings?.javaScriptEnabled = true
        webSettings?.domStorageEnabled = true
        webSettings?.databaseEnabled = true
        webSettings?.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        
        webView?.addJavascriptInterface(this, "AndroidBridge")
        
        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // UI အသစ်က 'Finished' button သို့မဟုတ် 'Tax Payment Status' ဆိုတဲ့ စာသားပေါ်လာတာနဲ့ data နှိုက်ပါမယ်
                val script = """
                    (function() {
                        var checkResult = setInterval(function() {
                            // UI အသစ်တွင် စာသားများကို ရှာဖွေခြင်း
                            var bodyText = document.body.innerText;
                            if (bodyText.includes('Tax Payment Status') || bodyText.includes('Finished')) {
                                
                                // IMEI status, Tax status စတာတွေကို စုစည်းခြင်း
                                var imei = "Checked";
                                var taxStatus = bodyText.includes('Paid') ? "Paid" : "Unpaid";
                                var blockStatus = bodyText.includes('Allowed') ? "Allowed" : "Blocked";
                                
                                // အခု UI အသစ်မှာ Brand/Date တိုက်ရိုက်မပါရင် IMEI နံပါတ်ကိုပဲ အချက်အလက်အဖြစ် ပို့ပေးပါမယ်
                                var imeiDisplay = document.querySelector('.text-2xl') ? document.querySelector('.text-2xl').innerText : "Device Info";

                                AndroidBridge.onResult(imeiDisplay, taxStatus, blockStatus);
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
    fun onResult(displayInfo: String, tax: String, block: String) {
        runOnUiThread {
            webView?.visibility = View.GONE
            resultLayout?.visibility = View.VISIBLE
            
            txtBrandInfo?.text = "IMEI: $displayInfo\nTAX: $tax\nSTATUS: $block"
            
            // Tax Paid ဖြစ်ရင် အစိမ်းရောင်၊ Unpaid ဆိုရင် အနီရောင် ပြပါမယ်
            if (tax.contains("Paid", ignoreCase = true)) {
                txtAprilStatus?.text = "VERIFIED: TAX PAID ✅"
                txtAprilStatus?.setBackgroundColor(Color.parseColor("#10B981")) // Green
            } else {
                txtAprilStatus?.text = "ALERT: TAX UNPAID ⚠️"
                txtAprilStatus?.setBackgroundColor(Color.parseColor("#E11D48")) // Red
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
        if (resultLayout?.visibility == View.VISIBLE) {
            showWebsite()
        } else if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
