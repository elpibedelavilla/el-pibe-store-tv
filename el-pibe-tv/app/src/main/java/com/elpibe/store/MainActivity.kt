package com.elpibe.store

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private val blue = Color.rgb(38, 185, 241)
    private val pending = mutableMapOf<Long, String>()
    private var waitingInstallUri: Uri? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            val name = pending.remove(id) ?: return
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (state == DownloadManager.STATUS_SUCCESSFUL) {
                        val uri = manager.getUriForDownloadedFile(id)
                        if (uri != null) {
                            status.text = "Descarga terminada: $name. Abriendo instalador..."
                            openInstaller(uri)
                        } else {
                            status.text = "Descargado: $name. Abre Download para instalar."
                        }
                    } else {
                        status.text = "No se pudo descargar $name"
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.rgb(8, 14, 23))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 26, 24, 12)
        }
        val title = TextView(this).apply {
            text = "REPOSITORIO EL PIBE DE LA VILLA"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        }
        status = TextView(this).apply {
            text = "Cargando aplicaciones..."
            textSize = 15f
            setTextColor(blue)
            setPadding(0, 0, 0, 16)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(title)
        root.addView(status)
        root.addView(scroll)
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
        load()
    }

    private fun openInstaller(uri: Uri) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            waitingInstallUri = uri
            status.text = "Autoriza instalar aplicaciones desde EL PIBE STORE y vuelve atrás."
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (e: Exception) {
                status.text = "No se pudo abrir la autorización: ${e.message}"
            }
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            status.text = "No se pudo abrir el instalador. Busca el APK en Download."
            Toast.makeText(this, "Instalación manual desde Download", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= 26 && packageManager.canRequestPackageInstalls()) {
            val uri = waitingInstallUri
            if (uri != null) {
                waitingInstallUri = null
                openInstaller(uri)
            }
        }
    }

    private fun load() = thread {
        try {
            val items = mutableListOf<Pair<String, String>>()
            for (page in 1..5) {
                val conn = URL("https://api.github.com/repos/elpibedelavilla/el-pibe-store/releases?per_page=100&page=$page").openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "ElPibeStoreTV/1.0")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                val response = try {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
                val releases = JSONArray(response)
                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    val assets = release.getJSONArray("assets")
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            items.add(name to asset.getString("browser_download_url"))
                        }
                    }
                }
                if (releases.length() < 100) break
            }
            runOnUiThread {
                status.text = if (items.isEmpty()) "No hay archivos APK públicos en las releases."
                else "${items.size} APK disponibles · ▲ ▼ Navegar · OK Descargar"
                list.removeAllViews()
                items.forEach { (name, url) -> addItem(name, url) }
                if (items.isNotEmpty()) list.getChildAt(0).requestFocus()
            }
        } catch (e: Exception) {
            runOnUiThread {
                status.text = "No se pudo cargar el catálogo. Comprueba Internet y que las releases sean públicas."
            }
        }
    }

    private fun addItem(name: String, url: String) {
        val item = TextView(this).apply {
            text = "↓  $name"
            textSize = 19f
            setTextColor(Color.WHITE)
            setPadding(24, 20, 20, 20)
            isFocusable = true
            isClickable = true
            setBackgroundColor(Color.rgb(24, 34, 48))
            setOnFocusChangeListener { v, focused ->
                v.setBackgroundColor(if (focused) Color.rgb(16, 91, 130) else Color.rgb(24, 34, 48))
            }
            setOnClickListener { download(name, url) }
        }
        list.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 7 })
    }

    private fun download(name: String, url: String) {
        try {
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val safeName = name.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(safeName)
                .setDescription("EL PIBE STORE")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
            val id = dm.enqueue(request)
            pending[id] = safeName
            status.text = "Descargando $safeName en Download..."
        } catch (e: Exception) {
            status.text = "Error al iniciar la descarga: ${e.message}"
        }
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}
