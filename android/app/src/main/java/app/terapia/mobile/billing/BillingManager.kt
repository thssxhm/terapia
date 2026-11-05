package app.terapia.mobile.billing

import android.app.Activity
import android.content.Context
import app.terapia.mobile.data.VerifyPurchaseRequest
import app.terapia.mobile.data.supabase
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Assinaturas pela Google Play (Play Billing Library 8).
 * Planos vendidos de 10 em 10 pacientes: crie no Play Console as assinaturas
 * plano_10, plano_20, plano_30, plano_40 e plano_50 (cada uma com um plano base mensal).
 * Docs: https://developer.android.com/google/play/billing/integrate
 */
class BillingManager(context: Context, private val onLimitChanged: () -> Unit) {

    companion object {
        val PRODUCT_IDS = listOf("plano_10", "plano_20", "plano_30", "plano_40", "plano_50")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products
    private val _active = MutableStateFlow<List<Purchase>>(emptyList())
    val active: StateFlow<List<Purchase>> = _active
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases?.forEach { scope.launch { handle(it) } }
            } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                _message.value = "Não foi possível concluir a compra (${result.debugMessage})"
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { loadProducts(); restore() }
                } else {
                    _message.value = "Google Play indisponível: ${result.debugMessage}"
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    fun disconnect() = client.endConnection()

    private suspend fun loadProducts() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            PRODUCT_IDS.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
            }
        ).build()
        val list = suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { _, result ->
                cont.resume(result.productDetailsList)
            }
        }
        _products.value = list.sortedBy { PRODUCT_IDS.indexOf(it.productId) }
    }

    /** Recupera assinaturas já compradas (ex.: depois de reinstalar o app). */
    private suspend fun restore() {
        val purchases = suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            ) { _, list -> cont.resume(list) }
        }
        _active.value = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        purchases.forEach { handle(it) }
    }

    fun buy(activity: Activity, product: ProductDetails) {
        val offer = product.subscriptionOfferDetails?.firstOrNull() ?: return
        val builder = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(product)
                    .setOfferToken(offer.offerToken)
                    .build()
            )
        )
        // Troca de plano (ex.: 10 → 20 pacientes) substitui a assinatura atual
        _active.value.firstOrNull()?.let { current ->
            builder.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(current.purchaseToken)
                    .setSubscriptionReplacementMode(
                        BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
                    )
                    .build()
            )
        }
        client.launchBillingFlow(activity, builder.build())
    }

    private suspend fun handle(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val productId = purchase.products.firstOrNull() ?: return
        // 1) Validação no servidor (Edge Function verify-purchase) → atualiza o limite de pacientes
        runCatching {
            supabase.functions.invoke(
                "verify-purchase",
                body = VerifyPurchaseRequest(productId, purchase.purchaseToken),
            )
        }.onFailure { _message.value = "Compra recebida, mas houve erro ao validar. Tente novamente mais tarde." }
        // 2) Confirma a compra na Play (obrigatório em até 3 dias, senão é reembolsada)
        if (!purchase.isAcknowledged) {
            suspendCancellableCoroutine { cont ->
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                ) { cont.resume(Unit) }
            }
        }
        onLimitChanged()
    }

    /** Preço formatado do plano base (ex.: "R$ 49,90/mês"). */
    fun priceOf(p: ProductDetails): String =
        p.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice ?: "—"
}
