package com.sephylon.foodfitscan.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

internal interface OpenFoodFactsApi {
    // Endpoint path is isolated here — update to switch API versions without touching other layers.
    // v3.6 was evaluated but v2 is used because its status=1/0 conventions match the rest of the app.
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String,
    ): ProductResponseDto

    // Structured category search — NOT /cgi/search.pl (legacy) or /api/v3/search (unimplemented).
    @GET("api/v2/search")
    suspend fun searchProductsByCategory(
        @Query("categories_tags") categoryTag: String,
        @Query("fields") fields: String,
        @Query("page_size") pageSize: Int,
        @Query("page") page: Int = 1,
    ): SearchResponseDto
}
