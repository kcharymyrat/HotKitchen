package testdata

import kotlinx.serialization.Serializable

@Serializable
data class Credentials(var email: String, var userType: String, var password: String)
