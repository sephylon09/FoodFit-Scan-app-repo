package com.sephylon.foodfitscan.data.remote

internal object OpenFoodFactsConfig {
    const val BASE_URL = "https://world.openfoodfacts.org/"

    // TODO replace with real contact email before release
    private const val CONTACT_EMAIL = "placeholder@example.com"
    const val USER_AGENT = "FoodFitScan/0.1 ($CONTACT_EMAIL)"

    // Using v2 endpoint; v2 status conventions (status=1 found, status=0 not found) match
    // the rest of the app. Update the @GET path in OpenFoodFactsApi to switch versions.
    const val FIELDS = "code,product_name,generic_name,brands,quantity,categories," +
        "categories_tags,image_front_url,image_ingredients_url,image_nutrition_url," +
        "nutriments,ingredients_text,ingredients_text_en,allergens,allergens_tags," +
        "traces,traces_tags,additives_tags,nutrition_grades,nutriscore_grade," +
        "nutriscore_data,nova_group,misc_tags,states_tags,last_modified_t"

    const val SEARCH_FIELDS = "code,product_name,brands,quantity,image_front_url," +
        "categories_tags,nutriments,allergens_tags,traces_tags,additives_tags," +
        "nutrition_grades,nutriscore_grade,nova_group,misc_tags,states_tags"

    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 30L
}
