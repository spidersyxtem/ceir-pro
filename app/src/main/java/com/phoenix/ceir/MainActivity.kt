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

        // XML ID တွေနဲ့ အတိအကျ ပြန်ချိတ်ထားပါတယ်
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
        webView?.addJavascriptInterface(this, "AndroidBridge")
        
        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
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
                webView?.evaluateJavascript(script, null)
            }
        }
        webView?.loadUrl("https://ceir.gov.mm/check-status")
    }

    @JavascriptInterface
    fun onResult(brand: String, model: String, date: String) {
        runOnUiThread {
            webView?.visibility = View.GONE
            resultLayout?.visibility = View.VISIBLE
            
            // Layout ထဲက ID နာမည်တွေအတိုင်း ပြန်သုံးထားပါတယ်
            txtBrandInfo?.text = "Device: $brand $model"
            
            if (date.contains("2024") && !date.contains("Jan") && !date.contains("Feb") && !date.contains("Mar")) {
                txtAprilStatus?.text = "AFTER APRIL 2024 (TAX REQUIRED)"
                txtAprilStatus?.setBackgroundColor(Color.RED)
            } else {
                txtAprilStatus?.text = "BEFORE APRIL 2024 (CLEAN)"
                txtAprilStatus?.setBackgroundColor(Color.parseColor("#10B981"))
            }
        }
    }

    override fun onBackPressed() {
        if (resultLayout?.visibility == View.VISIBLE) {
            resultLayout?.visibility = View.GONE
            webView?.visibility = View.VISIBLE
        } else if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
