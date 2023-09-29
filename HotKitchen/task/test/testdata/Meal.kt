package testdata

import kotlinx.serialization.Serializable

@Serializable
data class Meal(
    val mealId: Int,
    val title: String,
    val price: Float,
    val imageUrl: String,
    val categoryIds: List<Int>
)