package testdata

import kotlinx.serialization.Serializable

@Serializable
data class Order(
    var orderId: Int,
    val userEmail: String,
    val mealsIds: List<Int>,
    val price: Float,
    val address: String,
    val status: String
)