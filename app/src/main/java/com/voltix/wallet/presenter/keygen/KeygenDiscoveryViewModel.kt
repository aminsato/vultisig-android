package com.voltix.wallet.presenter.keygen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.voltix.wallet.common.Endpoints
import com.voltix.wallet.common.Utils
import com.voltix.wallet.common.VoltixRelay
import com.voltix.wallet.mediator.MediatorService
import com.voltix.wallet.models.KeygenMessage
import com.voltix.wallet.models.PeerDiscoveryPayload
import com.voltix.wallet.models.ReshareMessage
import com.voltix.wallet.models.TssAction
import com.voltix.wallet.models.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class KeygenDiscoveryViewModel @Inject constructor(
    private val voltixRelay: VoltixRelay,
) : ViewModel() {
    private val sessionID: String = UUID.randomUUID().toString() // generate a random UUID
    val serviceName: String = "VoltixApp-${Random.nextInt(1, 1000)}"
    private var serverAddress: String = "http://127.0.0.1:18080" // local mediator server
    private var participantDiscovery: ParticipantDiscovery? = null
    private var action: TssAction = TssAction.KEYGEN
    private var vault: Vault = Vault("New Vault")
    val selection = MutableLiveData<List<String>>()
    val keygenPayloadState: State<String>
        get() = _keygenPayload
    private val _keygenPayload: MutableState<String> = mutableStateOf("")
    val participants: MutableLiveData<List<String>>
        get() = participantDiscovery?.participants ?: MutableLiveData(listOf())

    fun setData(action: TssAction, vault: Vault, context: Context) {
        this.action = action
        this.vault = vault
        if (this.vault.HexChainCode.isEmpty()) {
            val secureRandom = SecureRandom()
            val randomBytes = ByteArray(32)
            secureRandom.nextBytes(randomBytes)
            this.vault.HexChainCode = randomBytes.joinToString("") { "%02x".format(it) }
        }
        if (this.vault.LocalPartyID.isEmpty()) {
            this.vault.LocalPartyID = Utils.deviceName
        }
        this.selection.value = listOf(this.vault.LocalPartyID)
        this.participantDiscovery =
            ParticipantDiscovery(serverAddress, sessionID, this.vault.LocalPartyID)
        when (action) {
            TssAction.KEYGEN -> {
                _keygenPayload.value = PeerDiscoveryPayload.Keygen(
                    keygenMessage = KeygenMessage(
                        sessionID = sessionID,
                        hexChainCode = vault.HexChainCode,
                        serviceName = serviceName,
                        encryptionKeyHex = Utils.encryptionKeyHex,
                        useVoltixRelay = voltixRelay.IsRelayEnabled
                    )
                ).toJson()
            }

            TssAction.ReShare -> {
                _keygenPayload.value = PeerDiscoveryPayload.Reshare(
                    reshareMessage = ReshareMessage(
                        sessionID = sessionID,
                        hexChainCode = vault.HexChainCode,
                        serviceName = serviceName,
                        pubKeyECDSA = vault.PubKeyECDSA,
                        oldParties = vault.signers,
                        encryptionKeyHex = Utils.encryptionKeyHex,
                        useVoltixRelay = voltixRelay.IsRelayEnabled
                    )
                ).toJson()
            }
        }
        if (!voltixRelay.IsRelayEnabled)
        // when relay is disabled, start the mediator service
            startMediatorService(context)
        else {
            serverAddress = Endpoints.VOLTIX_RELAY
            // start the session
            startSession(serverAddress, sessionID, vault.LocalPartyID)
            // kick off discovery
            participantDiscovery?.discoveryParticipants()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val serviceStartedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MediatorService.SERVICE_ACTION) {
                Log.d("KeygenDiscoveryViewModel", "onReceive: Mediator service started")
                // send a request to local mediator server to start the session
                GlobalScope.launch(Dispatchers.IO) {
                    Thread.sleep(1000) // back off a second
                    startSession(serverAddress, sessionID, vault.LocalPartyID)
                }
                // kick off discovery
                participantDiscovery?.discoveryParticipants()
            }
        }
    }

    private fun startMediatorService(context: Context) {
        val filter = IntentFilter()
        filter.addAction(MediatorService.SERVICE_ACTION)
        context.registerReceiver(serviceStartedReceiver, filter, Context.RECEIVER_EXPORTED)

        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        intent.putExtra("serverName", serviceName)
        context.startService(intent)
        Log.d("KeygenDiscoveryViewModel", "startMediatorService: Mediator service started")
    }

    private fun startSession(
        serverAddr: String,
        sessionID: String,
        localPartyID: String,
    ) {
        // start the session
        try {
            val client = OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build()
            val request = okhttp3.Request.Builder()
                .url("$serverAddr/$sessionID")
                .post(Gson().toJson(listOf(localPartyID)).toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    HttpURLConnection.HTTP_CREATED -> {
                        Log.d("KeygenDiscoveryViewModel", "startSession: Session started")
                    }
                    else ->
                        Log.d(
                            "KeygenDiscoveryViewModel",
                            "startSession: Response code: ${response.code}"
                        )
                }
            }
        } catch (e: Exception) {
            Log.e("KeygenDiscoveryViewModel", "startSession: ${e.stackTraceToString()}")
        }
    }

    fun addParticipant(participant: String) {
        val currentList = selection.value ?: emptyList()
        if (currentList.contains(participant)) return
        selection.value = currentList + participant
    }

    fun removeParticipant(participant: String) {
        selection.value = selection.value?.minus(participant)
    }
}