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
    // Variable တွေကို Class level မှာ ကြေညာထားမှ Function အားလုံးက လှမ်းသုံးလို့ရမှာပါ
    private lateinit var webView: WebView
    private lateinit var resultLayout: LinearLayout
    private lateinit var txtInfo: TextView
    private lateinit var txtStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View တွေကို ID နဲ့ ချိတ်ဆက်ခြင်း
        webView = findViewById(R.id.webView)
        resultLayout = findViewById(R.id.resultLayout)
        txtInfo = findViewById(R.id.txtInfo)
        txtStatus = findViewById(R.id.txtStatus)
        val btnBack: Button = findViewById(R.id.btnBack)

        setupWebView()

        btnBack.setOnClickListener {
            resultLayout.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.reload()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(this, "AndroidBridge")
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Website ထဲက data နှိုက်မယ့် script
                val script = """
                    var check = setInterval(function() {
                        var table = document.querySelector('table');
                        if (table && table.innerText.includes('Brand')) {
                            var b = table.rows[0].cells[1].innerText;
                            var m = table.rows[1].cells[1].innerText;
                            var d = table.rows[2].cells[1].innerText;
                            AndroidBridge.onResult(b, m, d);
                            clearInterval(check);
                        }
                    }, 1000);
                """.trimIndent()
                webView.evaluateJavascript(script, null)
            }
        }
        webView.loadUrl("https://ceir.gov.mm/check-status")
    }

    @JavascriptInterface
    fun onResult(brand: String, model: String, date: String) {
        runOnUiThread {
            webView.visibility = View.GONE
            resultLayout.visibility = View.VISIBLE
            
            // UI Update လုပ်ခြင်း
            txtInfo.text = "Device: $brand $model"
            
            // April 2024 Logic
            if (date.contains("2024") && !date.contains("Jan") && !date.contains("Feb") && !date.contains("Mar")) {
                txtStatus.text = "AFTER APRIL 2024 (TAX REQUIRED)"
                txtStatus.setTextColor(Color.RED)
            } else {
                txtStatus.text = "BEFORE APRIL 2024 (CLEAN)"
                txtStatus.setTextColor(Color.GREEN)
            }
        }
    }

    override fun onBackPressed() {
        if (resultLayout.visibility == View.VISIBLE) {
            resultLayout.visibility = View.GONE
            webView.visibility = View.VISIBLE
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
