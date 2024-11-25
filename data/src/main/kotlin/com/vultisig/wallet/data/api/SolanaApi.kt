package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.BroadcastTransactionRespJson
import com.vultisig.wallet.data.api.models.JupiterTokenResponseJson
import com.vultisig.wallet.data.api.models.RecentBlockHashResponseJson
import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.api.models.SPLTokenRequestJson
import com.vultisig.wallet.data.api.models.SolanaBalanceJson
import com.vultisig.wallet.data.api.models.SolanaFeeObjectJson
import com.vultisig.wallet.data.api.models.SolanaFeeObjectRespJson
import com.vultisig.wallet.data.api.models.SolanaMinimumBalanceForRentExemptionJson
import com.vultisig.wallet.data.api.models.SplAmountRpcResponseJson
import com.vultisig.wallet.data.api.models.SplResponseAccountJson
import com.vultisig.wallet.data.api.models.SplResponseJson
import com.vultisig.wallet.data.api.models.SplTokenJson
import com.vultisig.wallet.data.api.utils.postRpc
import com.vultisig.wallet.data.api.models.SplTokenInfo
import com.vultisig.wallet.data.models.SplTokenDeserialized
import com.vultisig.wallet.data.utils.SplTokenResponseJsonSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.util.encodeBase64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.avianlabs.solana.SolanaClient
import net.avianlabs.solana.client.RpcKtorClient
import net.avianlabs.solana.domain.core.Commitment
import net.avianlabs.solana.domain.core.Transaction
import net.avianlabs.solana.domain.core.decode
import net.avianlabs.solana.domain.core.serialize
import net.avianlabs.solana.domain.program.SystemProgram
import net.avianlabs.solana.methods.getBalance
import net.avianlabs.solana.methods.getFeeForMessage
import net.avianlabs.solana.methods.getLatestBlockhash
import net.avianlabs.solana.methods.getMinimumBalanceForRentExemption
import net.avianlabs.solana.methods.getNonce
import net.avianlabs.solana.methods.getTransaction
import net.avianlabs.solana.methods.requestAirdrop
import net.avianlabs.solana.methods.sendTransaction
import net.avianlabs.solana.methods.simulateTransaction
import net.avianlabs.solana.tweetnacl.TweetNaCl
import net.avianlabs.solana.tweetnacl.TweetNaCl.Signature.Companion.generateKey
import net.avianlabs.solana.tweetnacl.ed25519.Ed25519Keypair
import net.avianlabs.solana.tweetnacl.vendor.encodeToBase58String
import org.slf4j.MDC.put
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

interface SolanaApi {
    suspend fun getBalance(address: String): BigInteger
    suspend fun getMinimumBalanceForRentExemption(dataLength: Long? = null): BigInteger    suspend fun getRecentBlockHash(): String
    suspend fun getHighPriorityFee(account: String): String
    suspend fun broadcastTransaction(tx: String, jsonObject: JsonObject? = null): String?
    suspend fun getSPLTokens(walletAddress: String): List<SplResponseAccountJson>?
    suspend fun getSPLTokensInfo(tokens: List<String>): List<SplTokenJson>
    suspend fun getSPLTokensInfo2(tokens: List<String>): List<SplTokenInfo>
    suspend fun getJupiterTokens(): List<JupiterTokenResponseJson>
    suspend fun getSPLTokenBalance(walletAddress: String, coinAddress: String): String?
    suspend fun getTokenAssociatedAccountByOwner(walletAddress: String, mintAddress: String): String?
    suspend fun createNonceAccount(address: String)
}

internal class SolanaApiImp @Inject constructor(
    private val json: Json,
    private val httpClient: HttpClient,
    private val splTokenSerializer: SplTokenResponseJsonSerializer,
) : SolanaApi {

    private val rpcEndpoint = "https://api.devnet.solana.com"
    private val rpcEndpoint2 = "https://solana-rpc.publicnode.com"
    private val splTokensInfoEndpoint = "https://api.solana.fm/v1/tokens"
    private val splTokensInfoEndpoint2 = "https://tokens.jup.ag/token"
    private val solanaRentExemptionEndpoint = "https://api.devnet.solana.com"
    private val jupiterTokensUrl = "https://tokens.jup.ag/tokens"
    private val client = SolanaClient(
        client = RpcKtorClient(
            "https://api.devnet.solana.com",
            httpClient = httpClient
        ),
    )
    override suspend fun getBalance(address: String): BigInteger {
        return try {
            val payload = RpcPayload(
                jsonrpc = "2.0",
                method = "getBalance",
                params = buildJsonArray {
                    add(address)
                },
                id = 1,
            )
            val response = httpClient.post(rpcEndpoint) {
                setBody(payload)
            }
            val rpcResp = response.body<SolanaBalanceJson>()
            Timber.tag("solanaApiImp").d(response.toString())

            if (rpcResp.error != null) {
                Timber.tag("solanaApiImp")
                    .d("get balance ,address: $address error: ${rpcResp.error}")
                BigInteger.ZERO
            }
            rpcResp.result?.value ?: error("getBalance error")
        } catch (e: Exception) {
            BigInteger.ZERO
        }
    }

    override suspend fun createNonceAccount(address: String) {
        try {
            val keypair = TweetNaCl.Signature.generateKey(Random.nextBytes(32))
            println("Keypair: ${keypair.publicKey}")
            val nonceAccount = TweetNaCl.Signature.generateKey(Random.nextBytes(32))
            println("Nonce account: ${nonceAccount.publicKey}")

//            client.requestAirdrop(keypair.publicKey, 2_000_000_000)

            delay(1.seconds)
            val balance = client.getBalance(keypair.publicKey)
            println("Balance: $balance")

            val rentExempt =
                client.getMinimumBalanceForRentExemption(SystemProgram.NONCE_ACCOUNT_LENGTH).result!!

            val blockhash = client.getLatestBlockhash().result!!.value

            val initTransaction = Transaction.Builder()
                .addInstruction(
                    SystemProgram.createAccount(
                        fromPublicKey = keypair.publicKey,
                        newAccountPublicKey = nonceAccount.publicKey,
                        lamports = rentExempt,
                        space = SystemProgram.NONCE_ACCOUNT_LENGTH,
                    )
                )
                .addInstruction(
                    SystemProgram.nonceInitialize(
                        nonceAccount = nonceAccount.publicKey,
                        authorized = keypair.publicKey,
                    )
                )
                .setRecentBlockHash(blockhash.blockhash)
                .build()
                .sign(listOf(keypair, nonceAccount))


            val simulated = client.simulateTransaction(initTransaction)

            println("simulated: $simulated")

            val initSignature = client.sendTransaction(initTransaction)

            println("Initialized nonce account: $initSignature")
            delay(1.seconds)

            val lamportsPerSignature = client.getFeeForMessage(initTransaction.message.serialize())
            println("Lamports per signature: $lamportsPerSignature")

            val nonce = client.getNonce(nonceAccount.publicKey, Commitment.Confirmed)
            println("Nonce account info: $nonce")

            val testTransaction = Transaction.Builder()
                .addInstruction(
                    SystemProgram.nonceAdvance(
                        nonceAccount = nonceAccount.publicKey,
                        authorized = keypair.publicKey,
                    )
                )
                .addInstruction(
                    SystemProgram.transfer(
                        fromPublicKey = keypair.publicKey,
                        toPublicKey = nonceAccount.publicKey,
                        lamports = 1_000_000_000,
                    )
                )
                .setRecentBlockHash(nonce!!.nonce)
                .build()
                .sign(keypair)

            val testSignature = client.sendTransaction(testTransaction).result!!
            println("Advanced nonce account: $testSignature")

            delay(1.seconds)

            val testTxInfo = client.getTransaction(testSignature, Commitment.Confirmed).result
            println("Transaction info: ${testTxInfo?.decode()}")

            val newNonce = client.getNonce(nonceAccount.publicKey, Commitment.Processed)
            println("New nonce account info: $newNonce")
        } catch (e: Exception) {
            Timber.tag("SolanaApiImp").e("Error createNonceAccount: ${e.message}")
        }
    }

    override suspend fun getMinimumBalanceForRentExemption(dataLength: Long?): BigInteger = try {
        httpClient.postRpc<SolanaMinimumBalanceForRentExemptionJson>(
            solanaRentExemptionEndpoint,
            "getMinimumBalanceForRentExemption",
            params = buildJsonArray {
                add(dataLength?:DATA_LENGTH_MINIMUM_BALANCE_FOR_RENT_EXEMPTION)
            },
        ).result
    } catch (e: Exception) {
        Timber.e("Error getting minimum balance for rent exemption: ${e.message}")
        BigInteger.ZERO
    }

    override suspend fun getRecentBlockHash(): String {
        val payload = RpcPayload(
            jsonrpc = "2.0",
            method = "getLatestBlockhash",
            params = buildJsonArray {
                addJsonObject {
                    put("commitment", "confirmed")
                }
            },
            id = 1,
        )
        val response = httpClient.post(rpcEndpoint) {
            setBody(payload)
        }
        val responseContent = response.bodyAsText()
        Timber.tag("solanaApiImp").d(responseContent)
        val rpcResp = response.body<RecentBlockHashResponseJson>()
        if (rpcResp.error != null) {
            Timber.tag("solanaApiImp")
                .d("get recent blockhash  error: ${rpcResp.error}")
            return ""
        }
        return rpcResp.result?.value?.blockHash ?: error("getRecentBlockHash error")
    }

    override suspend fun getHighPriorityFee(account: String): String {
        try {
            val payload = RpcPayload(
                jsonrpc = "2.0",
                method = "getRecentPrioritizationFees",
                params = buildJsonArray {
                    addJsonArray {
                        add(account)
                    }
                },
                id = 1,
            )
            val response = httpClient.post(rpcEndpoint) {
                setBody(payload)
            }
            val responseContent = response.bodyAsText()
            Timber.d(responseContent)
            val rpcResp = response.body<SolanaFeeObjectRespJson>()

            if (rpcResp.error != null) {
                Timber.d("get high priority fee  error: ${rpcResp.error}")
                return ""
            }
            val fees: List<SolanaFeeObjectJson> =
                rpcResp.result ?: error("getHighPriorityFee error")
            return fees.maxOf { it.prioritizationFee }.toString()
        } catch (e: Exception) {
            Timber.tag("SolanaApiImp").e("Error getting high priority fee: ${e.message}")
        }
        return "0"
    }

    override suspend fun broadcastTransaction(tx: String,jsonObject: JsonObject?): String? {
        try {
            val requestBody = RpcPayload(
                jsonrpc = "2.0",
                method = "sendTransaction",
                params = buildJsonArray {
                    add(tx)
                    jsonObject?.let {
                        add(it)
                    }
                },
                id = 1,
            )
            val response = httpClient.post(rpcEndpoint) {
                setBody(requestBody)
            }
            val responseRawString = response.bodyAsText()
            val result = response.body<BroadcastTransactionRespJson>()
            result.error?.let { error ->
                Timber.tag("SolanaApiImp").d("Error broadcasting transaction: $responseRawString")
                error(error["message"].toString())
            }
            return result.result ?: error("broadcastTransaction error")
        } catch (e: Exception) {
            Timber.tag("SolanaApiImp").e("Error broadcasting transaction: ${e.message}")
            throw e
        }

    }

    override suspend fun getSPLTokensInfo(tokens: List<String>): List<SplTokenJson> {
        try {
            val requestBody = SPLTokenRequestJson(
                tokens = tokens
            )
            val response = httpClient.post(splTokensInfoEndpoint) {
                setBody(requestBody)
            }
            val responseRawString = response.bodyAsText()
            when (val result = json.decodeFromString(splTokenSerializer, responseRawString)) {
                is SplTokenDeserialized.Error -> {
                    Timber.tag("SolanaApiImp").d(
                        "Error getting spl tokens: ${result.error.error.message}"
                    )
                    return emptyList()
                }
                is SplTokenDeserialized.Result -> return result.result.values.toList()
            }
        } catch (e: Exception) {
            Timber.tag("SolanaApiImp").e("Error getting spl tokens: ${e.message}")
            return emptyList()
        }
    }

    override suspend fun getSPLTokensInfo2(tokens: List<String>) = coroutineScope {
        tokens.map { token ->
            async {
                try {
                    httpClient.get("$splTokensInfoEndpoint2/$token").body<SplTokenInfo>()
                } catch (e: Exception) {
                    Timber.tag("SolanaApiImp")
                        .e("Error getting spl token for $token message : ${e.message}")
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    override suspend fun getJupiterTokens(): List<JupiterTokenResponseJson> =
        httpClient.get(jupiterTokensUrl) {
            parameter(
                "tags",
                "verified"
            )
        }.body()

    override suspend fun getSPLTokens(walletAddress: String): List<SplResponseAccountJson>? {
        try {
            val payload = RpcPayload(
                jsonrpc = "2.0",
                method = "getTokenAccountsByOwner",
                params = buildJsonArray {
                    add(walletAddress)
                    addJsonObject {
                        put("programId", PROGRAM_ID_SPL_REQUEST_PARAM)
                    }
                    addJsonObject {
                        put("encoding", ENCODING_SPL_REQUEST_PARAM)
                    }
                },
                id = 1,
            )
            val response = httpClient.post(rpcEndpoint2) {
                setBody(payload)
            }
            val responseContent = response.bodyAsText()
            Timber.d(responseContent)
            val rpcResp = response.body<SplResponseJson>()

            if (rpcResp.error != null) {
                Timber.d("get spl token addresses error: ${rpcResp.error}")
                return null
            }
            return rpcResp.result?.accounts
        } catch (e: Exception) {
            Timber.e(e)
            return null
        }
    }

    override suspend fun getSPLTokenBalance(walletAddress: String, coinAddress: String): String? {
        try {
            val payload = RpcPayload(
                jsonrpc = "2.0",
                method = "getTokenAccountsByOwner",
                params = buildJsonArray {
                    add(walletAddress)
                    addJsonObject {
                        put("mint", coinAddress)
                    }
                    addJsonObject {
                        put("encoding", ENCODING_SPL_REQUEST_PARAM)
                    }
                },
                id = 1,
            )
            val response = httpClient.post(rpcEndpoint2) {
                setBody(payload)
            }
            val responseContent = response.bodyAsText()
            Timber.d(responseContent)
            val rpcResp = response.body<SplAmountRpcResponseJson>()

            if (rpcResp.error != null) {
                Timber.d("get spl token amount error: ${rpcResp.error}")
                return null
            }
            val value = rpcResp.value ?: error("getSPLTokenBalance error")
            return value.value[0].account.data.parsed.info.tokenAmount.amount
        } catch (e: Exception) {
            Timber.e(e)
            return null
        }
    }

    override suspend fun getTokenAssociatedAccountByOwner(
        walletAddress: String,
        mintAddress: String,
    ): String? {
        try {
            val response = httpClient.postRpc<SplAmountRpcResponseJson>(
                url = rpcEndpoint2,
                method = "getTokenAccountsByOwner",
                params = buildJsonArray {
                    add(walletAddress)
                    addJsonObject {
                        put("mint", mintAddress)
                    }
                    addJsonObject {
                        put("encoding", ENCODING_SPL_REQUEST_PARAM)
                    }
                }
            )
            if (response.error != null) {
                Timber.d("getTokenAssociatedAccountByOwner error: ${response.error}")
                return null
            }
            val value = response.value ?: error("getTokenAssociatedAccountByOwner error")
            return value.value[0].pubKey
        } catch (e: Exception) {
            Timber.e(e)
            return null
        }
    }


    companion object {
        private const val PROGRAM_ID_SPL_REQUEST_PARAM =
            "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        private const val ENCODING_SPL_REQUEST_PARAM = "jsonParsed"
        private const val DATA_LENGTH_MINIMUM_BALANCE_FOR_RENT_EXEMPTION = 165
    }

}