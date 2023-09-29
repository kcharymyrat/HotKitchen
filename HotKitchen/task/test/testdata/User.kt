package testdata

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val name: String, val userType: String, val phone: String, val email: String, val address: String
)