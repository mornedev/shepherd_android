package com.mamlambofossils.legacyretriever

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import java.net.HttpURLConnection
import java.net.URL

data class SubscriptionProduct(
    val productId: String,
    val name: String,
    val description: String,
    val price: String,
    val productDetails: ProductDetails
)

sealed class BillingState {
    object Idle : BillingState()
    object Loading : BillingState()
    data class ProductsLoaded(val products: List<SubscriptionProduct>) : BillingState()
    data class PurchaseSuccess(val productId: String) : BillingState()
    data class Error(val message: String) : BillingState()
}

interface PurchaseTokenCallback {
    suspend fun getApiBaseUrl(): String
    suspend fun getAccessToken(): String?
}

class BillingManager(
    private val context: Context,
    private val purchaseTokenCallback: PurchaseTokenCallback? = null
) : PurchasesUpdatedListener {
    
    private var billingClient: BillingClient? = null
    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()
    
    private val _activePurchases = MutableStateFlow<Set<String>>(emptySet())
    val activePurchases: StateFlow<Set<String>> = _activePurchases.asStateFlow()
    
    companion object {
        private const val TAG = "BillingManager"
        
        // Product IDs - these must match what you configure in Google Play Console
        const val PRODUCT_STARTER = "starter_monthly"
        const val PRODUCT_STANDARD = "standard_monthly"
        const val PRODUCT_PREMIUM = "premium_monthly"
        
        val SUBSCRIPTION_PRODUCT_IDS = listOf(
            PRODUCT_STARTER,
            PRODUCT_STANDARD,
            PRODUCT_PREMIUM
        )
    }
    
    fun initialize() {
        Log.d(TAG, "Initializing BillingClient")
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        
        startConnection()
    }
    
    private fun startConnection() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected successfully")
                    queryActivePurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _billingState.value = BillingState.Error("Billing setup failed: ${billingResult.debugMessage}")
                }
            }
            
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected, will retry connection")
                // Retry connection
                startConnection()
            }
        })
    }
    
    suspend fun querySubscriptionProducts(): List<SubscriptionProduct> = suspendCancellableCoroutine { continuation ->
        val client = billingClient
        if (client == null || !client.isReady) {
            Log.e(TAG, "BillingClient not ready")
            _billingState.value = BillingState.Error("Billing not ready")
            continuation.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        
        _billingState.value = BillingState.Loading
        
        val productList = SUBSCRIPTION_PRODUCT_IDS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val products = productDetailsList.map { productDetails ->
                    val offerDetails = productDetails.subscriptionOfferDetails?.firstOrNull()
                    val pricingPhase = offerDetails?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    
                    SubscriptionProduct(
                        productId = productDetails.productId,
                        name = productDetails.name,
                        description = productDetails.description,
                        price = pricingPhase?.formattedPrice ?: "N/A",
                        productDetails = productDetails
                    )
                }
                
                Log.d(TAG, "Loaded ${products.size} subscription products")
                _billingState.value = BillingState.ProductsLoaded(products)
                continuation.resume(products)
            } else {
                Log.e(TAG, "Failed to query products: ${billingResult.debugMessage}")
                _billingState.value = BillingState.Error("Failed to load products: ${billingResult.debugMessage}")
                continuation.resume(emptyList())
            }
        }
    }
    
    fun launchPurchaseFlow(activity: Activity, product: SubscriptionProduct) {
        val client = billingClient
        if (client == null || !client.isReady) {
            Log.e(TAG, "BillingClient not ready for purchase")
            _billingState.value = BillingState.Error("Billing not ready")
            return
        }
        
        val offerToken = product.productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            Log.e(TAG, "No offer token found for product ${product.productId}")
            _billingState.value = BillingState.Error("Product not available")
            return
        }
        
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product.productDetails)
                .setOfferToken(offerToken)
                .build()
        )
        
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        
        Log.d(TAG, "Launching purchase flow for ${product.productId}")
        val billingResult = client.launchBillingFlow(activity, billingFlowParams)
        
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "Failed to launch billing flow: ${billingResult.debugMessage}")
            _billingState.value = BillingState.Error("Failed to start purchase: ${billingResult.debugMessage}")
        }
    }
    
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled purchase")
                _billingState.value = BillingState.Error("Purchase canceled")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d(TAG, "Item already owned")
                _billingState.value = BillingState.Error("You already own this subscription")
                queryActivePurchases()
            }
            else -> {
                Log.e(TAG, "Purchase failed: ${billingResult.debugMessage}")
                _billingState.value = BillingState.Error("Purchase failed: ${billingResult.debugMessage}")
            }
        }
    }
    
    private fun handlePurchase(purchase: Purchase) {
        Log.d(TAG, "Processing purchase: ${purchase.products}")
        
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            } else {
                // Purchase already acknowledged
                val productId = purchase.products.firstOrNull() ?: ""
                _billingState.value = BillingState.PurchaseSuccess(productId)
                queryActivePurchases()
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.d(TAG, "Purchase pending")
            _billingState.value = BillingState.Error("Purchase is pending")
        }
    }
    
    private fun acknowledgePurchase(purchase: Purchase) {
        val client = billingClient ?: return
        
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        client.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged successfully")
                val productId = purchase.products.firstOrNull() ?: ""
                
                // Send purchase token to backend
                CoroutineScope(Dispatchers.IO).launch {
                    sendPurchaseTokenToBackend(purchase.purchaseToken, productId)
                }
                
                _billingState.value = BillingState.PurchaseSuccess(productId)
                queryActivePurchases()
            } else {
                Log.e(TAG, "Failed to acknowledge purchase: ${billingResult.debugMessage}")
                _billingState.value = BillingState.Error("Failed to complete purchase")
            }
        }
    }
    
    private suspend fun sendPurchaseTokenToBackend(purchaseToken: String, subscriptionId: String) {
        try {
            val callback = purchaseTokenCallback
            if (callback == null) {
                Log.w(TAG, "No purchase token callback provided, skipping backend sync")
                return
            }
            
            val apiBaseUrl = callback.getApiBaseUrl()
            val accessToken = callback.getAccessToken()
            
            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "No access token available, cannot send purchase token")
                return
            }
            
            val url = URL("${apiBaseUrl.trimEnd('/')}/play-store-purchase")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 15000
            }
            
            try {
                val payload = """
                    {
                        "purchase_token": "$purchaseToken",
                        "subscription_id": "$subscriptionId"
                    }
                """.trimIndent()
                
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                
                if (code in 200..299) {
                    Log.d(TAG, "Successfully sent purchase token to backend: $body")
                } else {
                    Log.e(TAG, "Failed to send purchase token to backend: HTTP $code - $body")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception sending purchase token to backend", e)
        }
    }
    
    private fun queryActivePurchases() {
        val client = billingClient
        if (client == null || !client.isReady) {
            Log.w(TAG, "Cannot query purchases - client not ready")
            return
        }
        
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        
        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val activeProductIds = purchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .flatMap { it.products }
                    .toSet()
                
                Log.d(TAG, "Active purchases: $activeProductIds")
                _activePurchases.value = activeProductIds
            } else {
                Log.e(TAG, "Failed to query purchases: ${billingResult.debugMessage}")
            }
        }
    }
    
    fun destroy() {
        Log.d(TAG, "Destroying BillingClient")
        billingClient?.endConnection()
        billingClient = null
    }
}
