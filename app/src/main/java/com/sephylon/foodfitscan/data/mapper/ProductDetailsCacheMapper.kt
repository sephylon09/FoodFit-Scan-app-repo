package com.sephylon.foodfitscan.data.mapper

import com.google.gson.Gson
import com.sephylon.foodfitscan.data.local.CachedProductEntity
import com.sephylon.foodfitscan.domain.model.ProductDetails

internal class ProductDetailsCacheMapper(private val gson: Gson) {

    fun toEntity(product: ProductDetails): CachedProductEntity = CachedProductEntity(
        barcode = product.barcode,
        productName = product.name,
        brand = product.brand,
        quantity = product.quantity,
        imageFrontUrl = product.imageFrontUrl,
        nutriScore = product.nutriScore,
        novaGroup = product.novaGroup,
        cachedAtMillis = System.currentTimeMillis(),
        serializedProductJson = gson.toJson(product),
    )

    fun fromEntity(entity: CachedProductEntity): ProductDetails? =
        try {
            gson.fromJson(entity.serializedProductJson, ProductDetails::class.java)
        } catch (e: Exception) {
            null
        }
}
