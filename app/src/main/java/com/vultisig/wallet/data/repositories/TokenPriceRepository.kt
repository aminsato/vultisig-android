package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CmcApi
import com.vultisig.wallet.data.api.CurrencyToPrice
import com.vultisig.wallet.data.db.dao.TokenPriceDao
import com.vultisig.wallet.data.db.models.TokenPriceEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.settings.AppCurrency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import javax.inject.Inject

internal interface TokenPriceRepository {

    suspend fun getCachedPrice(
        tokenId: String,
        appCurrency: AppCurrency,
    ): BigDecimal?

    suspend fun getCachedPrices(
        tokenIds: List<String>,
        appCurrency: AppCurrency,
    ): List<Pair<String, BigDecimal>>

    fun getPrice(
        token: Coin,
        appCurrency: AppCurrency,
    ): Flow<BigDecimal>

    suspend fun refresh(
        tokens: List<Coin>,
    )

    suspend fun getPrice(
        token: Coin
    ): BigDecimal
}


internal class TokenPriceRepositoryImpl @Inject constructor(
    private val appCurrencyRepository: AppCurrencyRepository,
    private val cmcApi: CmcApi,
    private val tokenPriceDao: TokenPriceDao,
) : TokenPriceRepository {

    private val tokenIdToPrice = MutableStateFlow(mapOf<String, CurrencyToPrice>())

    override suspend fun getCachedPrice(
        tokenId: String,
        appCurrency: AppCurrency
    ): BigDecimal? = tokenPriceDao
        .getTokenPrice(tokenId, appCurrency.ticker.lowercase())
        ?.let { BigDecimal(it) }

    override suspend fun getCachedPrices(
        tokenIds: List<String>,
        appCurrency: AppCurrency
    ): List<Pair<String, BigDecimal>> = tokenPriceDao
        .getTokenPrices(tokenIds, appCurrency.ticker.lowercase())
        .map { it.tokenId to BigDecimal(it.price) }

    @ExperimentalCoroutinesApi
    override fun getPrice(
        token: Coin,
        appCurrency: AppCurrency,
    ): Flow<BigDecimal> = tokenIdToPrice.map {
        it[token.id]
            ?.get(appCurrency.ticker.lowercase())
            ?: BigDecimal.ZERO
    }

    override suspend fun refresh(tokens: List<Coin>) {
        val currency = appCurrencyRepository.currency.first().ticker.lowercase()
        val refreshedPrices = cmcApi.fetchPrices(tokens,currency)
        savePrices(refreshedPrices, currency)
    }

    override suspend fun getPrice(token: Coin): BigDecimal {
        val currency = appCurrencyRepository.currency.first().ticker.lowercase()
        val currencyAndPrice = cmcApi.fetchPrice(token, currency)
        currencyAndPrice.let {
            savePrices(
                mapOf(token.id to it),
                currency
            )
            val price = it.values.first()
            return price
        }
    }

    private suspend fun savePrices(
        tokenIdToPrices: Map<String, CurrencyToPrice>,
        currency: String,
    ) {
        tokenIdToPrices.forEach { (tokenId, currencyToPrice) ->
            currencyToPrice[currency]?.toPlainString()?.let { price ->
                tokenPriceDao.insertTokenPrice(
                    TokenPriceEntity(
                        tokenId = tokenId,
                        currency = currency,
                        price = price,
                    )
                )
            }
        }

        tokenIdToPrice.update { it + tokenIdToPrices }
    }

}