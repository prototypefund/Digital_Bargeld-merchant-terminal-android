package net.taler.merchantpos

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import net.taler.merchantpos.Utils.Companion.hexStringToByteArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection


class Utils {
    companion object {
        private val HEX_CHARS = "0123456789ABCDEF"
        fun hexStringToByteArray(data: String): ByteArray {

            val result = ByteArray(data.length / 2)

            for (i in 0 until data.length step 2) {
                val firstIndex = HEX_CHARS.indexOf(data[i]);
                val secondIndex = HEX_CHARS.indexOf(data[i + 1]);

                val octet = firstIndex.shl(4).or(secondIndex)
                result.set(i.shr(1), octet.toByte())
            }

            return result
        }

        private val HEX_CHARS_ARRAY = "0123456789ABCDEF".toCharArray()
        fun toHex(byteArray: ByteArray): String {
            val result = StringBuffer()

            byteArray.forEach {
                val octet = it.toInt()
                val firstIndex = (octet and 0xF0).ushr(4)
                val secondIndex = octet and 0x0F
                result.append(HEX_CHARS_ARRAY[firstIndex])
                result.append(HEX_CHARS_ARRAY[secondIndex])
            }

            return result.toString()
        }
    }
}

val TALER_AID = "A0000002471001"


fun writeApduLength(stream: ByteArrayOutputStream, size: Int) {
    when {
        size == 0 -> {
            // No size field needed!
        }
        size <= 255 -> // One byte size field
            stream.write(size)
        size <= 65535 -> {
            stream.write(0)
            // FIXME: is this supposed to be little or big endian?
            stream.write(size and 0xFF)
            stream.write((size ushr 8) and 0xFF)
        }
        else -> throw Error("payload too big")
    }
}

fun apduSelectFile(): ByteArray {
    return hexStringToByteArray("00A4040007A0000002471001")
}


fun apduPutData(payload: ByteArray): ByteArray {
    val stream = ByteArrayOutputStream()

    // Class
    stream.write(0x00)

    // Instruction 0xDA = put data
    stream.write(0xDA)

    // Instruction parameters
    // (proprietary encoding)
    stream.write(0x01)
    stream.write(0x00)

    writeApduLength(stream, payload.size)

    stream.write(payload)

    return stream.toByteArray()
}

fun apduPutTalerData(talerInst: Int, payload: ByteArray): ByteArray {
    val realPayload = ByteArrayOutputStream()
    realPayload.write(talerInst)
    realPayload.write(payload)
    return apduPutData(realPayload.toByteArray())
}

fun apduGetData(): ByteArray {
    val stream = ByteArrayOutputStream()

    // Class
    stream.write(0x00)

    // Instruction 0xCA = get data
    stream.write(0xCA)

    // Instruction parameters
    // (proprietary encoding)
    stream.write(0x01)
    stream.write(0x00)

    // Max expected response size, two
    // zero bytes denotes 65536
    stream.write(0x0)
    stream.write(0x0)

    return stream.toByteArray()
}


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener,
    NfcAdapter.ReaderCallback {

    companion object {
        const val TAG = "taler-merchant"
    }

    private val model: PosTerminalViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    private var currentTag: IsoDep? = null

    override fun onTagDiscovered(tag: Tag?) {

        Log.v(TAG, "tag discovered")

        val isoDep = IsoDep.get(tag)
        isoDep.connect()

        currentTag = isoDep

        isoDep.transceive(apduSelectFile())

        val contractUri: String? = model.activeTalerPayUri

        if (contractUri != null) {
            isoDep.transceive(apduPutTalerData(1, contractUri.toByteArray()))
        }

        // FIXME: use better pattern for sleeps in between requests
        // -> start with fast polling, poll more slowly if no requests are coming

        while (true) {
            try {
                val reqFrame = isoDep.transceive(apduGetData())
                if (reqFrame.size < 2) {
                    Log.v(TAG, "request frame too small")
                    break
                }
                val req = ByteArray(reqFrame.size - 2)
                if (req.isEmpty()) {
                    continue
                }
                reqFrame.copyInto(req, 0, 0, reqFrame.size - 2)
                val jsonReq = JSONObject(req.toString(Charsets.UTF_8))
                val reqId = jsonReq.getInt("id")
                Log.v(TAG, "got request $jsonReq")
                val jsonInnerReq = jsonReq.getJSONObject("request")
                val method = jsonInnerReq.getString("method")
                val urlStr = jsonInnerReq.getString("url")
                Log.v(TAG, "url '$urlStr'")
                Log.v(TAG, "method '$method'")
                val url = URL(urlStr)
                val conn: HttpsURLConnection = url.openConnection() as HttpsURLConnection
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 5000
                conn.doInput = true
                when (method)  {
                    "get" -> {
                        conn.requestMethod = "GET"
                    }
                    "postJson" -> {
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json; utf-8")
                        val body =  jsonInnerReq.getString("body")
                        conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
                    }
                    else -> {
                        throw Exception("method not supported")
                    }
                }
                Log.v(TAG, "connecting")
                conn.connect()
                Log.v(TAG, "connected")

                val statusCode = conn.responseCode
                val tunnelResp = JSONObject()
                tunnelResp.put("id", reqId)
                tunnelResp.put("status", conn.responseCode)

                if (statusCode == 200) {
                    val stream = conn.inputStream
                    val httpResp = stream.buffered().readBytes()
                    tunnelResp.put("responseJson", JSONObject(httpResp.toString(Charsets.UTF_8)))
                }

                Log.v(TAG, "sending: $tunnelResp")

                isoDep.transceive(apduPutTalerData(2, tunnelResp.toString().toByteArray()))
            } catch (e: Exception) {
                Log.v(TAG, "exception during NFC loop: ${e}")
                break
            }
        }

        isoDep.close()
    }

    public override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    public override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        navView.setNavigationItemSelectedListener(this)

        val navController = findNavController(R.id.nav_host_fragment)
        val appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.order,
                    R.id.createPayment,
                    R.id.merchantSettings,
                    R.id.merchantHistory
                ), drawerLayout
            )

        findViewById<Toolbar>(R.id.toolbar)
            .setupWithNavController(navController, appBarConfiguration)

        val prefs = getSharedPreferences("taler-merchant-terminal", Context.MODE_PRIVATE)

        val baseUrl = prefs.getString("merchantBackendUrl", "https://backend.test.taler.net")
        val instance = prefs.getString("merchantBackendInstance", "default")
        val apiKey = prefs.getString("merchantBackendApiKey", "sandbox")
        val currency = prefs.getString("merchantBackendCurrency", "TESTKUDOS")

        model.merchantConfig =
            MerchantConfig(baseUrl!!, instance!!, apiKey!!, currency!!)
    }

    override fun onBackPressed() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        val nav: NavController = findNavController(R.id.nav_host_fragment)
        when (item.itemId) {
            R.id.nav_home -> nav.navigate(R.id.action_global_createPayment)
            R.id.nav_order -> nav.navigate(R.id.action_global_order)
            R.id.nav_history -> {
                nav.navigate(R.id.action_global_merchantHistory)
            }
            R.id.nav_settings -> {
                nav.navigate(R.id.action_global_merchantSettings)
            }
        }
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }


}
