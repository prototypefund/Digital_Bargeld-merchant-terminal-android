package net.taler.merchantpos

import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.FLAG_READER_NFC_A
import android.nfc.NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import net.taler.merchantpos.Utils.hexStringToByteArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class NfcManager : NfcAdapter.ReaderCallback {

    companion object {
        const val TAG = "taler-merchant"
    }

    val TALER_AID = "A0000002471001"
    val flags = FLAG_READER_NFC_A or FLAG_READER_SKIP_NDEF_CHECK

    private var contractUri: String? = null
    private var currentTag: IsoDep? = null

    fun setContractUri(contractUri: String) {
        this.contractUri = contractUri
    }

    override fun onTagDiscovered(tag: Tag?) {

        Log.v(TAG, "tag discovered")

        val isoDep = IsoDep.get(tag)
        isoDep.connect()

        currentTag = isoDep

        isoDep.transceive(apduSelectFile())

        val contractUri: String? = contractUri

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
                when (method) {
                    "get" -> {
                        conn.requestMethod = "GET"
                    }
                    "postJson" -> {
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json; utf-8")
                        val body = jsonInnerReq.getString("body")
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
                Log.v(TAG, "exception during NFC loop: $e")
                break
            }
        }

        isoDep.close()
    }

    private fun writeApduLength(stream: ByteArrayOutputStream, size: Int) {
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

    private fun apduSelectFile(): ByteArray {
        return hexStringToByteArray("00A4040007A0000002471001")
    }

    private fun apduPutData(payload: ByteArray): ByteArray {
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

    private fun apduPutTalerData(talerInst: Int, payload: ByteArray): ByteArray {
        val realPayload = ByteArrayOutputStream()
        realPayload.write(talerInst)
        realPayload.write(payload)
        return apduPutData(realPayload.toByteArray())
    }

    private fun apduGetData(): ByteArray {
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

}