package com.sephylon.foodfitscan.data.mapper

import com.sephylon.foodfitscan.data.local.ScanHistoryEntity
import com.sephylon.foodfitscan.domain.model.LookupStatus
import com.sephylon.foodfitscan.domain.model.ProductLookupResult
import com.sephylon.foodfitscan.domain.model.ScanHistoryItem

internal class ScanHistoryMapper {

    fun toEntity(barcode: String, result: ProductLookupResult): ScanHistoryEntity =
        when (result) {
            is ProductLookupResult.Found -> ScanHistoryEntity(
                barcode = result.product.barcode,
                productName = result.product.name,
                brand = result.product.brand,
                imageFrontUrl = result.product.imageFrontUrl,
                scannedAtMillis = System.currentTimeMillis(),
                lookupStatus = LookupStatus.FOUND.name,
                shortMessage = null,
            )
            is ProductLookupResult.NotFound -> ScanHistoryEntity(
                barcode = barcode,
                productName = null,
                brand = null,
                imageFrontUrl = null,
                scannedAtMillis = System.currentTimeMillis(),
                lookupStatus = LookupStatus.NOT_FOUND.name,
                shortMessage = null,
            )
            is ProductLookupResult.NetworkError -> ScanHistoryEntity(
                barcode = barcode,
                productName = null,
                brand = null,
                imageFrontUrl = null,
                scannedAtMillis = System.currentTimeMillis(),
                lookupStatus = LookupStatus.NETWORK_ERROR.name,
                shortMessage = result.message.take(100),
            )
            is ProductLookupResult.UnknownError -> ScanHistoryEntity(
                barcode = barcode,
                productName = null,
                brand = null,
                imageFrontUrl = null,
                scannedAtMillis = System.currentTimeMillis(),
                lookupStatus = LookupStatus.UNKNOWN_ERROR.name,
                shortMessage = result.message.take(100),
            )
        }

    fun toHistoryItem(entity: ScanHistoryEntity): ScanHistoryItem = ScanHistoryItem(
        id = entity.id,
        barcode = entity.barcode,
        productName = entity.productName,
        brand = entity.brand,
        imageFrontUrl = entity.imageFrontUrl,
        scannedAtMillis = entity.scannedAtMillis,
        lookupStatus = try {
            LookupStatus.valueOf(entity.lookupStatus)
        } catch (e: IllegalArgumentException) {
            LookupStatus.UNKNOWN_ERROR
        },
        shortMessage = entity.shortMessage,
    )
}
