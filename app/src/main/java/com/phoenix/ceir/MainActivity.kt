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
            resultLayout?.visibility = View.GONE
            webView?.visibility = View.VISIBLE
            webView?.reload()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView?.settings?.javaScriptEnabled = true
        webView?.settings?.domStorageEnabled = true
        webView?.settings?.databaseEnabled = true
        webView?.addJavascriptInterface(this, "AndroidBridge")
        
        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // MutationObserver ကိုသုံးပြီး Result ပေါ်လာတာနဲ့ ချက်ချင်းဖမ်းပါမယ်
                val script = """
                    (function() {
                        var observer = new MutationObserver(function(mutations) {
                            var table = document.querySelector('table');
                            if (table && table.innerText.includes('Brand')) {
                                var b = table.rows[0].cells[1].innerText;
                                var m = table.rows[1].cells[1].innerText;
                                var d = table.rows[2].cells[1].innerText;
                                AndroidBridge.onResult(b, m, d);
                                observer.disconnect();
                            }
                        });
                        observer.observe(document.body, { childList: true, subtree: true });
                    })();
                """.trimIndent()
                webView?.evaluateJavascript(script, null)
            }
        }
        webView?.loadUrl("https://ceir.gov.mm/check-status")
    }

    @JavascriptInterface
    fun onResult(brand: String, model: String, date: String) {
        runOnUiThread {
            if (brand.isNotEmpty()) {
                webView?.visibility = View.GONE
                resultLayout?.visibility = View.VISIBLE
                
                txtBrandInfo?.text = "Device: ${brand.trim()} ${model.trim()}"
                
                // April 2024 Check
                if (date.contains("2024") && !date.contains("Jan") && !date.contains("Feb") && !date.contains("Mar")) {
                    txtAprilStatus?.text = "AFTER APRIL 2024\n(TAX REQUIRED)"
                    txtAprilStatus?.setBackgroundColor(Color.RED)
                    txtAprilStatus?.setTextColor(Color.WHITE)
                } else {
                    txtAprilStatus?.text = "BEFORE APRIL 2024\n(CLEAN DEVICE)"
                    txtAprilStatus?.setBackgroundColor(Color.parseColor("#10B981"))
                    txtAprilStatus?.setTextColor(Color.WHITE)
                }
            }
        }
    }
}
