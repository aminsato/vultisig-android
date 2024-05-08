package com.vultisig.wallet.presenter.keysign

import android.os.Parcelable
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName
import com.vultisig.wallet.chains.UtxoInfo
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.ERC20ApprovePayload
import com.vultisig.wallet.models.THORChainSwapPayload
import com.vultisig.wallet.models.Vault
import kotlinx.parcelize.Parcelize
import java.lang.reflect.Type
import java.math.BigInteger


@Parcelize
data class KeysignPayload(
    val coin: Coin,
    val toAddress: String,
    val toAmount: BigInteger,
    @SerializedName("chainSpecific") val blockChainSpecific: BlockChainSpecific,
    val utxos: List<UtxoInfo> = emptyList(),
    val memo: String? = null,
    val swapPayload: THORChainSwapPayload? = null,
    val approvePayload: ERC20ApprovePayload? = null,
    val vaultPublicKeyECDSA: String,
) : Parcelable {

    fun toJson(): String {
        val gson = GsonBuilder()
            .registerTypeAdapter(
                BlockChainSpecific::class.java,
                BlockChainSpecificSerializer()
            ).registerTypeAdapter(
                KeysignPayload::class.java,
                KeysignPayloadSerializer()
            )
            .create()
        return gson.toJson(this)
    }

    companion object {
        fun fromJson(json: String): KeysignPayload {
            val gson = GsonBuilder()
                .registerTypeAdapter(
                    KeysignPayload::class.java,
                    KeysignPayloadDeserializer()
                )
                .registerTypeAdapter(
                    BlockChainSpecific::class.java,
                    BlockChainSpecificDeserializer()
                )
                .create()
            return gson.fromJson(json, KeysignPayload::class.java)
        }
    }

    fun getKeysignMessages(vault: Vault): List<String> {
        throw Exception("Not implemented")
    }

}

fun BigInteger.toJson(): JsonArray {
    val jsonArray = JsonArray()
    jsonArray.add("+")
    jsonArray.add(this)
    return jsonArray
}

class KeysignPayloadSerializer : JsonSerializer<KeysignPayload> {
    override fun serialize(
        src: KeysignPayload,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement {
        val jsonObject = JsonObject()
        jsonObject.addProperty("toAddress", src.toAddress)
        jsonObject.add("toAmount", src.toAmount.toJson())
        jsonObject.addProperty("vaultPubKeyECDSA", src.vaultPublicKeyECDSA)
        jsonObject.add("chainSpecific", context?.serialize(src.blockChainSpecific))
        jsonObject.add("coin", context?.serialize(src.coin))
        jsonObject.add("utxos", context?.serialize(src.utxos))
        jsonObject.addProperty("memo", src.memo)
        jsonObject.add("swapPayload", context?.serialize(src.swapPayload))
        jsonObject.add("approvePayload", context?.serialize(src.approvePayload))
        return jsonObject
    }
}

class KeysignPayloadDeserializer : JsonDeserializer<KeysignPayload> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): KeysignPayload {
        val jsonObject = json.asJsonObject
        val toAddress = jsonObject.get("toAddress").asString
        val toAmount = jsonObject.get("toAmount").asJsonArray[1].asBigInteger
        val vaultPubKeyECDSA = jsonObject.get("vaultPubKeyECDSA").asString
        val chainSpecific = context?.deserialize<BlockChainSpecific>(
            jsonObject.get("chainSpecific"),
            BlockChainSpecific::class.java
        )!!
        val coin = context.deserialize<Coin>(jsonObject.get("coin"), Coin::class.java)
        val utxos = context.deserialize<List<UtxoInfo>>(
            jsonObject.get("utxos"),
            List::class.java
        )
        val memo = jsonObject.get("memo")?.asString
        val swapPayload = context.deserialize<THORChainSwapPayload>(
            jsonObject.get("swapPayload"),
            THORChainSwapPayload::class.java
        )
        val approvePayload = context.deserialize<ERC20ApprovePayload>(
            jsonObject.get("approvePayload"),
            ERC20ApprovePayload::class.java
        )
        return KeysignPayload(
            coin = coin,
            toAddress = toAddress,
            toAmount = toAmount,
            blockChainSpecific = chainSpecific,
            utxos = utxos,
            memo = memo,
            swapPayload = swapPayload,
            approvePayload = approvePayload,
            vaultPublicKeyECDSA = vaultPubKeyECDSA
        )
    }
}