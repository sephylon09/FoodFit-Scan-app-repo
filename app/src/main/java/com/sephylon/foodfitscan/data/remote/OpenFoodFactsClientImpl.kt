package com.sephylon.foodfitscan.data.remote

import com.sephylon.foodfitscan.BuildConfig
import com.sephylon.foodfitscan.core.network.OpenFoodFactsClient
import com.sephylon.foodfitscan.data.mapper.OpenFoodFactsProductMapper
import com.sephylon.foodfitscan.domain.model.ProductDetails
import com.sephylon.foodfitscan.domain.model.ProductLookupResult
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class OpenFoodFactsClientImpl(
    private val api: OpenFoodFactsApi,
    private val mapper: OpenFoodFactsProductMapper = OpenFoodFactsProductMapper(),
) : OpenFoodFactsClient {

    override suspend fun getProduct(barcode: String): ProductLookupResult {
        return try {
            val response = api.getProduct(barcode, OpenFoodFactsConfig.FIELDS)
            when (response.status) {
                1 -> {
                    val productDto = response.product
                    if (productDto != null) {
                        ProductLookupResult.Found(mapper.map(barcode, productDto))
                    } else {
                        ProductLookupResult.NotFound(barcode)
                    }
                }
                0 -> ProductLookupResult.NotFound(barcode)
                else -> ProductLookupResult.UnknownError("Unexpected API status: ${response.status}")
            }
        } catch (e: IOException) {
            ProductLookupResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            ProductLookupResult.UnknownError(e.message ?: "Unknown error")
        }
    }

    override suspend fun searchByCategory(categoryTag: String, pageSize: Int): List<ProductDetails> {
        val response = api.searchProductsByCategory(
            categoryTag = categoryTag,
            fields = OpenFoodFactsConfig.SEARCH_FIELDS,
            pageSize = pageSize,
        )
        return response.products.orEmpty().mapNotNull { dto ->
            val barcode = dto.code?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            mapper.map(barcode, dto)
        }
    }

    companion object {
        fun create(): OpenFoodFactsClientImpl {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", OpenFoodFactsConfig.USER_AGENT)
                        .build()
                    chain.proceed(request)
                }
                .addInterceptor(loggingInterceptor)
                .connectTimeout(OpenFoodFactsConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(OpenFoodFactsConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(OpenFoodFactsConfig.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return OpenFoodFactsClientImpl(api = retrofit.create(OpenFoodFactsApi::class.java))
        }
    }
}
