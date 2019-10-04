package it.ipzs.cieidsdk.common

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.provider.Settings
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers
import it.ipzs.cieidsdk.exceptions.BlockedPinException
import it.ipzs.cieidsdk.exceptions.NoCieException
import it.ipzs.cieidsdk.exceptions.PinInputNotValidException
import it.ipzs.cieidsdk.network.NetworkClient
import it.ipzs.cieidsdk.network.service.IdpService
import it.ipzs.cieidsdk.nfc.Ias
import it.ipzs.cieidsdk.url.DeepLinkInfo
import it.ipzs.cieidsdk.util.CieIDSdkLogger
import okhttp3.ResponseBody
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLProtocolException

val CERTIFICATE_EXPIRED: CharSequence = "SSLV3_ALERT_CERTIFICATE_EXPIRED"
val CERTIFICATE_REVOKED: CharSequence = "SSLV3_ALERT_CERTIFICATE_REVOKED"

class Event {

    interface EventValue {
        val nameEvent: String
    }

    enum class EventTag : EventValue {
        //tag
        ON_TAG_DISCOVERED_NOT_CIE,
        ON_TAG_DISCOVERED,
        ON_TAG_LOST;

        override val nameEvent: String = name
    }
    enum class EventCard: EventValue {
        //card
        ON_CARD_PIN_LOCKED,
        ON_PIN_ERROR;

        override val nameEvent: String = name

    }
    enum class EventCertificate : EventValue {
        //certificate
        CERTIFICATE_EXPIRED,
        CERTIFICATE_REVOKED;

        override val nameEvent: String = name
    }
    enum class EventError : EventValue {
        //error
        AUTHENTICATION_ERROR,
        GENERAL_ERROR,
        ON_NO_INTERNET_CONNECTION;
        override val nameEvent: String = name
    }

    private val eventValue: EventValue

    constructor (event: EventValue) {
        this.eventValue = event
    }

    override fun toString(): String {
        return eventValue.nameEvent
    }
}


interface Callback {

    fun onSuccess(url: String)
    fun onError(error: Throwable)
    fun onEvent(event: Event.EventValue)
}

object CieIDSdk : NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private var callback: Callback? = null
    internal var deepLinkInfo: DeepLinkInfo = DeepLinkInfo()
    internal var ias: Ias? = null
    var enableLog: Boolean = false
    var pin: String = ""
    private const val isoDepTimeout: Int = 5000


    @SuppressLint("CheckResult")
    fun call(certificate: ByteArray) {

        val idpService: IdpService = NetworkClient(certificate).idpService
        val mapValues = hashMapOf<String, String>().apply {
            put(deepLinkInfo.name!!, deepLinkInfo.value!!)
            put(IdpService.authnRequest, deepLinkInfo.authnRequest ?: "")
            put(IdpService.generaCodice, "1")
        }

        idpService.callIdp(mapValues).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(object :
                DisposableSingleObserver<Response<ResponseBody>>() {
                override fun onSuccess(idpResponse: Response<ResponseBody>) {
                    CieIDSdkLogger.log("onSuccess")
                    if (idpResponse.body() != null) {
                        val codiceServer =
                            idpResponse.body()!!.string().split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                        if(!checkCodiceServer(codiceServer)){
                            callback?.onEvent(Event.EventError.GENERAL_ERROR)
                        }
                        val url =
                            deepLinkInfo.nextUrl + "?" + deepLinkInfo.name + "=" + deepLinkInfo.value + "&login=1&codice=" + codiceServer
                        callback?.onSuccess(url)

                    } else {
                        callback?.onEvent(Event.EventError.AUTHENTICATION_ERROR)
                    }

                }

                override fun onError(e: Throwable) {
                    CieIDSdkLogger.log("onError")

                    when (e) {
                        is SocketTimeoutException , is UnknownHostException -> {
                            CieIDSdkLogger.log("SocketTimeoutException or UnknownHostException")
                            callback?.onEvent(Event.EventError.ON_NO_INTERNET_CONNECTION)

                        }
                        is SSLProtocolException -> {

                            CieIDSdkLogger.log("SSLProtocolException")
                            e.message?.let {
                                when {
                                    it.contains(CERTIFICATE_EXPIRED) -> callback?.onEvent(Event.EventCertificate.CERTIFICATE_EXPIRED)
                                    it.contains(CERTIFICATE_REVOKED) -> callback?.onEvent(Event.EventCertificate.CERTIFICATE_REVOKED)
                                    else -> callback?.onError(e)
                                }
                            }

                        }
                        else -> callback?.onError(e)
                    }
                }
            })
    }

    private fun checkCodiceServer(codiceServer: String): Boolean {
        val regex = Regex("^[0-9]+$")
        if(codiceServer.length==16 && regex.matches(codiceServer)){
            return true
        }
        return false
    }


    override fun onTagDiscovered(tag: Tag?) {
        try {
            callback?.onEvent(Event.EventTag.ON_TAG_DISCOVERED)
            val isoDep = IsoDep.get(tag)
            isoDep.timeout = isoDepTimeout
            isoDep.connect()
            ias = Ias(isoDep)

            ias!!.getIdServizi()
            ias!!.startSecureChannel(pin)
            val certificate = ias!!.readCertCie()
            call(certificate)

        } catch (throwable: Throwable) {
            CieIDSdkLogger.log(throwable.toString())
            when (throwable) {
                is PinInputNotValidException -> callback?.onEvent(Event.EventCard.ON_PIN_ERROR)
                is BlockedPinException -> callback?.onEvent(Event.EventCard.ON_CARD_PIN_LOCKED)
                is NoCieException -> callback?.onEvent(Event.EventTag.ON_TAG_DISCOVERED_NOT_CIE)
                is TagLostException -> callback?.onEvent(Event.EventTag.ON_TAG_LOST)
                else -> callback?.onError(throwable)
            }
        }
    }


    /**
     * Set the SDK callback and init NFC adapter.
     * start method must be called before accessing nfc features
     * */
    fun start(activity: Activity, cb: Callback) {
        callback = cb
        nfcAdapter = (activity.getSystemService(Context.NFC_SERVICE) as NfcManager).defaultAdapter
    }


    fun setUrl(url: String) {
        val appLinkData = Uri.parse(url)
        deepLinkInfo = DeepLinkInfo(
            value = appLinkData.getQueryParameter(DeepLinkInfo.KEY_VALUE),
            name = appLinkData.getQueryParameter(DeepLinkInfo.KEY_NAME),
            authnRequest = appLinkData.getQueryParameter(DeepLinkInfo.KEY_AUTHN_REQUEST_STRING),
            nextUrl = appLinkData.getQueryParameter(DeepLinkInfo.KEY_NEXT_UTL),
            opText = appLinkData.getQueryParameter(DeepLinkInfo.KEY_OP_TEXT),
            host = appLinkData.host ?: "",
            logo = appLinkData.getQueryParameter(DeepLinkInfo.KEY_LOGO)
        )

    }

    /**
     * Call on Resume of NFC Activity
     */
    fun startNFCListening(activity: Activity) {
        nfcAdapter?.enableReaderMode(
            activity, this, NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null
        )
    }

    /**
     * Call on Pause Of NFC Activity
     */
    fun stopNFCListening(activity: Activity) {
        nfcAdapter?.disableReaderMode(activity)
    }

    /**
     *  Return true if device has NFC supports
     */
    fun hasFeatureNFC(context: Activity): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
    }

    /**
     *  Return true if NFC is enabled on device
     */
    fun isNFCEnabled(activity: Activity): Boolean = hasFeatureNFC(activity) && nfcAdapter?.isEnabled ?: false

    /**
    Open NFC Settings PAge
     */
    fun enableNFC(activity: Activity) {
        activity.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    }


}
