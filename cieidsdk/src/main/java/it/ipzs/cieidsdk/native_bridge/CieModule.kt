
package it.ipzs.cieidsdk.native_bridge

import com.facebook.react.bridge.*
import it.ipzs.cieidsdk.common.Callback
import it.ipzs.cieidsdk.common.CieIDSdk
import it.ipzs.cieidsdk.common.Event
import com.facebook.react.modules.core.RCTNativeAppEventEmitter
import com.facebook.react.bridge.Arguments.createMap


class CieModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), Callback {


    /**
     * Callback method that return the access url when the tag nfc is correctly decripted
     */
    override fun onSuccess(url: String) {
        this.sendSuccess(url)
    }

    /**
     * Callback method that returns any errors during the decript phase
     */
    override fun onError(e: Throwable) {
        e.message?.let { this.sendError(it) }
    }

    /**
     * Callback method that returns an enum describing the steps of decryption
     */
    override fun onEvent(event: Event.EventValue) {
        this.sendEvent(event)
    }

    override fun getName(): String {
        return "NativeCieModule"
    }

    private fun sendEvent(
        event: Event.EventValue
    ) {
        reactApplicationContext
            .getJSModule(RCTNativeAppEventEmitter::class.java)
            .emit("onEvent", event.toString())
    }

    private fun sendError(
        errorMessage: String
    ) {
        reactApplicationContext
            .getJSModule(RCTNativeAppEventEmitter::class.java)
            .emit("onError", errorMessage)
    }

    private fun sendSuccess(
        url: String
    ) {
        reactApplicationContext
            .getJSModule(RCTNativeAppEventEmitter::class.java)
            .emit("onSuccess", url)
    }


    @ReactMethod
    fun start(callback: com.facebook.react.bridge.Callback){
        try {
            CieIDSdk.start(getCurrentActivity()!!, this)
            callback.invoke(null, null)
        } catch (e: RuntimeException) {
            callback.invoke(e.message,null)
        }
    }

    @ReactMethod
    fun isNFCEnabled(callback: com.facebook.react.bridge.Callback) {
        callback.invoke(CieIDSdk.isNFCEnabled(getCurrentActivity()!!))
    }

    @ReactMethod
    fun hasNFCFeature(callback: com.facebook.react.bridge.Callback) {
        callback.invoke(CieIDSdk.hasFeatureNFC(getCurrentActivity()!!))
    }

    @ReactMethod
    fun setPin(pin: String) {
        CieIDSdk.pin = pin
    }

    @ReactMethod
    fun setAuthenticationUrl(url: String) {
        CieIDSdk.setUrl(url)
    }

    @ReactMethod
    fun startListeningNFC(callback: com.facebook.react.bridge.Callback) {
        try {
            CieIDSdk.startNFCListening(getCurrentActivity()!!)
            callback.invoke(null,null)
        } catch (e: RuntimeException) {
            callback.invoke(e.message,null)
        }
    }

    @ReactMethod
    fun stopListeningNFC(callback: com.facebook.react.bridge.Callback) {
        try {
            CieIDSdk.stopNFCListening(getCurrentActivity()!!)
            callback.invoke(null, null)
        } catch (e: RuntimeException) {
            callback.invoke(e.message,null)
        }
    }

}
