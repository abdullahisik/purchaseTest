package dima.testapp.testprojectpurschase

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat.recreate
import com.android.billingclient.api.*
import java.io.IOException
import java.util.ArrayList

class TestFragment : Fragment(), PurchasesUpdatedListener {

    companion object {
        const val PREF_FILE = "MyPref"
        const val PURCHASE_KEY = "purchase"
        const val PRODUCT_ID = "remove_ad_test"
    }

    private lateinit var purchaseStatus: TextView
    private lateinit var purchaseButton: AppCompatButton
    private var billingClient: BillingClient? = null

    private val preferenceObject: SharedPreferences
        get() = requireContext().getSharedPreferences(PREF_FILE, 0)

    private val preferenceEditObject: SharedPreferences.Editor
        get() {
            val pref: SharedPreferences =
                requireContext().getSharedPreferences(PREF_FILE, 0)
            return pref.edit()
        }

    private val purchaseValueFromPref: Boolean
        get() = preferenceObject.getBoolean(PURCHASE_KEY, false)


    private var ackPurchase = AcknowledgePurchaseResponseListener { billingResult ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            //if purchase is acknowledged
            // Grant entitlement to the user. and restart activity
            savePurchaseValueToPref(true)
            Toast.makeText(requireContext(), "Item Purchased", Toast.LENGTH_SHORT).show()
            recreate(requireActivity())
        }
    }

    private fun savePurchaseValueToPref(value: Boolean) {
        preferenceEditObject.putBoolean(MainActivity.PURCHASE_KEY, value).commit()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_test, container, false)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        purchaseButton = view.findViewById(R.id.purchase_button)
        purchaseStatus = view.findViewById(R.id.purchase_status)

        billingClient = BillingClient.newBuilder(requireContext())
            .enablePendingPurchases().setListener(this).build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val queryPurchase = billingClient!!.queryPurchases(BillingClient.SkuType.INAPP)
                    val queryPurchases: MutableList<Purchase>? = queryPurchase.purchasesList
                    if (queryPurchases != null && queryPurchases.isNotEmpty()) {
                        handlePurchases(queryPurchases)
                    }
                    //if purchase list is empty that means item is not purchased
                    //Or purchase is refunded or canceled
                    else {
                        savePurchaseValueToPref(false);
                    }
                }
            }

            override fun onBillingServiceDisconnected() {}

        })

        //item Purchased
        if (purchaseValueFromPref) {
            purchaseButton.visibility = View.GONE
            purchaseStatus.text = "Purchase Status : Purchased"
            purchaseStatus.setTextColor(Color.GREEN)
        }
        //item not Purchased
        else {
            purchaseButton.visibility = View.VISIBLE
            purchaseStatus.text = "Purchase Status : Not Purchased"
            purchaseStatus.setTextColor(Color.RED)
        }

        purchaseButton.setOnClickListener {
            purchase(it)
            Toast.makeText(requireContext(), "ahhhaah", Toast.LENGTH_LONG).show()
        }



    }

    private fun purchase(view: View) {
        //check if service is already connected
        if (billingClient!!.isReady) {
            initiatePurchase()
            //else reconnect service
        } else {
            billingClient =
                BillingClient.newBuilder(requireContext()).enablePendingPurchases().setListener(this).build()
            billingClient!!.startConnection(object : BillingClientStateListener {

                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        initiatePurchase()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Error " + billingResult.debugMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onBillingServiceDisconnected() {}
            })
        }
    }

    private fun initiatePurchase() {
        val skuList = ArrayList<String>()
        skuList.add(PRODUCT_ID)
        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP)

        billingClient!!.querySkuDetailsAsync(params.build()) { billingResult, skuDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (skuDetailsList != null && skuDetailsList.size > 0) {
                    val flowParams = BillingFlowParams.newBuilder()
                        .setSkuDetails(skuDetailsList[0])
                        .build()

                    billingClient!!.launchBillingFlow(requireActivity(), flowParams)
                } else {
                    Toast.makeText(requireContext(), "Purchase Item not Found", Toast.LENGTH_SHORT)
                        .show()
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    " Error " + billingResult.debugMessage, Toast.LENGTH_SHORT
                ).show()
            }
        }

    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchaseList: MutableList<Purchase>?
    ) {

        when (billingResult.responseCode) {
            //if item newly purchased
            BillingClient.BillingResponseCode.OK -> {
                if (purchaseList != null) {
                    handlePurchases(purchaseList)
                }
            }
            //if item already purchased then check and reflect changes
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                val queryAlreadyPurchasesResult =
                    billingClient!!.queryPurchases(BillingClient.SkuType.INAPP)
                val alreadyPurchases: MutableList<Purchase>? =
                    queryAlreadyPurchasesResult.purchasesList
                if (alreadyPurchases != null) {
                    handlePurchases(alreadyPurchases)
                }
            }
        }
    }

    private fun handlePurchases(purchaseList: MutableList<Purchase>?) {
        if (purchaseList != null) {
            for (purchase in purchaseList)
                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> {
                        if (!verifyValidSignature(purchase.originalJson, purchase.signature)) {
                            // Invalid purchase
                            // show error to user
                            Toast.makeText(
                                requireContext(),
                                "Error : Invalid Purchase",
                                Toast.LENGTH_SHORT
                            ).show()
                            return
                        }
                        // if purchase is valid
                        //if item is purchased and not acknowledged
                        if (!purchase.isAcknowledged) {
                            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()
                            billingClient!!.acknowledgePurchase(
                                acknowledgePurchaseParams,
                                ackPurchase
                            )
                        }
                        //else item is purchased and also acknowledged
                        // Grant entitlement to the user on item purchase
                        // restart activity
                        else if (!purchaseValueFromPref) {
                            savePurchaseValueToPref(true)
                            Toast.makeText(
                                requireContext(),
                                "Item Purchased",
                                Toast.LENGTH_SHORT
                            ).show()
                            recreate(requireActivity())
                        }
                    }
                    Purchase.PurchaseState.PENDING -> {
                        Toast.makeText(
                            requireContext(),
                            "Purchase is Pending. Please complete Transaction", Toast.LENGTH_SHORT
                        ).show()
                    }
                    //if purchase is refunded or unknown
                    Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                        savePurchaseValueToPref(false)
                        purchaseStatus.text = "Purchase Status : Not Purchased"
                        purchaseButton.visibility = View.VISIBLE
                        Toast.makeText(
                            requireContext(),
                            "Purchase Status Unknown",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }

    /**
     * Verifies that the purchase was signed correctly for this developer's public key.
     *
     * Note: It's strongly recommended to perform such check on your backend since hackers can
     * replace this method with "constant true" if they decompile/rebuild your app.
     *
     */
    private fun verifyValidSignature(signedData: String, signature: String): Boolean {
        return try {
            val base64Key =
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4C6zVdSlk9hxVuKmixcQTaJ+oBPsOnBv7u5Wd/OaXMjeZnivgXm6+mc8uoG6puwtLLLzatiY86a+Qf9JLYfkPCKtmVzFsZ0rSDxzWhQHj3sgGorscN/tmK+7YRA0orGs43FkM7FR9Hj1YDZUq6ZF5zJ3bR1jn5plt6vs6Xri1kDt5QLs5b7NblW4uZfh7u/5AWsxU6+7zTjMv8tLAYIo85GDuC7mwId/BGAzuc4W7qcKGWK+v3+ntaOr2abN/pOcmNceBi6Ht9q/RFFoichcQ7QFawdhxaCBhSPv92b6ZJ7C0zAxppxI59mFisn+zGeVT2NGnP8cptb6nR987wBnsQIDAQAB"
            Security.verifyPurchase(base64Key, signedData, signature)
        } catch (e: IOException) {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingClient!!.endConnection()
    }
}

