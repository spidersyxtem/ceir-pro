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
    // Variable တွေကို class ရဲ့ အပြင်ဘက်ဆုံးမှာ Nullable အဖြစ် ကြေညာထားပါမယ်
    private var webView: WebView? = null
    private var resultLayout: LinearLayout? = null
    private var txtInfo: TextView? = null
    private var txtStatus: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // onCreate ထဲမှာ View တွေကို စနစ်တကျ ချိတ်ဆက်ပါမယ်
        webView = findViewById(R.id.webView)
        resultLayout = findViewById(R.id.resultLayout)
        txtInfo = findViewById(R.id.txtInfo)
        txtStatus = findViewById(R.id.txtStatus)
        val btnBack: Button = findViewById(R.id.btnBack)

        setupWebView()

        btnBack.setOnClickListener {
            // အဖြေပြမျက်နှာပြင်ကို ပိတ်ပြီး Website ဆီ ပြန်သွားခြင်း
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
                // Website မှာ အဖြေပေါ်လာတာနဲ့ data နှိုက်မယ့် logic
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
        // UI ကို update လုပ်မှာဖြစ်လို့ UI Thread (Main Thread) ပေါ်မှာ Run ရပါမယ်
        runOnUiThread {
            webView?.visibility = View.GONE
            resultLayout?.visibility = View.VISIBLE
            
            // အချက်အလက်များ ထည့်သွင်းခြင်း
            txtInfo?.text = "Device: $brand $model"
            
            // April 2024 ရက်စွဲ စစ်ဆေးခြင်း
            if (date.contains("2024") && !date.contains("Jan") && !date.contains("Feb") && !date.contains("Mar")) {
                txtStatus?.text = "AFTER APRIL 2024 (TAX REQUIRED)"
                txtStatus?.setTextColor(Color.RED)
            } else {
                txtStatus?.text = "BEFORE APRIL 2024 (CLEAN)"
                txtStatus?.setTextColor(Color.GREEN)
            }
        }
    }

    override fun onBackPressed() {
        // ဖုန်းရဲ့ Back Key နှိပ်ရင် အဖြေပြမျက်နှာပြင်ကနေ မူရင်း Website ဆီ ပြန်သွားစေရန်
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
