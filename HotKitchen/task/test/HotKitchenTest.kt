import com.typesafe.config.ConfigFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.config.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hyperskill.hstest.dynamic.DynamicTest
import org.hyperskill.hstest.stage.StageTest
import org.hyperskill.hstest.testcase.CheckResult
import testdata.*

class HotKitchenTest : StageTest<Any>() {

    private val time = System.currentTimeMillis()
    private val jwtRegex = """^[a-zA-Z0-9]+?\.[a-zA-Z0-9]+?\..+""".toRegex()
    private val currentCredentialsClient = Credentials("$time@client.com", "client", "password$time")
    private var currentUserClient = User(
        time.toString() + "name",
        "client",
        "+79999999999",
        currentCredentialsClient.email,
        time.toString() + "address"
    )
    private val currentCredentialsStaff = Credentials("$time@staff.com", "staff", "password$time")
    private val currentMeals = arrayOf(
        Meal(
            time.toInt(),
            "$time title1",
            (time.toInt() % 100).toFloat(),
            "image $time url",
            listOf((0..10).random(), (0..10).random(), (0..10).random())
        ),
        Meal(
            time.toInt() + 1,
            "$time title1",
            (time.toInt() % 100).toFloat(),
            "image $time url",
            listOf((0..10).random(), (0..10).random(), (0..10).random())
        ),
        Meal(
            time.toInt() + 2,
            "$time title1",
            (time.toInt() % 100).toFloat(),
            "image $time url",
            listOf((0..10).random(), (0..10).random(), (0..10).random())
        )
    )
    private val accessDenied = """{"status":"Access denied"}"""

    private val price = currentMeals[0].price + currentMeals[1].price + currentMeals[2].price
    private val mealsIds = listOf(currentMeals[0].mealId, currentMeals[1].mealId, currentMeals[2].mealId)
    private val currentOrder =
        Order(time.toInt(), currentCredentialsClient.email, mealsIds, price, currentUserClient.address, "COOK")

    private lateinit var signInTokenClient: String
    private lateinit var signInTokenStaff: String

    private var incompleteSize = 0


    @DynamicTest(order = 1)
    fun getSignInJWTToken(): CheckResult {
        var result = CheckResult.correct()
        try {
            testApplication {
                var response = client.post("/signup") {
                    setBody(Json.encodeToString(currentCredentialsClient))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                try {
                    println()
                    println("response.bodyAsText() = ${response.bodyAsText()}")
                    println("currentCredentialsClient = $currentCredentialsClient")
                    val principal = Json.decodeFromString<Token>(response.bodyAsText() ?: "")
                    signInTokenClient = principal.token
                    println("signInTokenClient = $signInTokenClient")
                    if (!signInTokenClient.matches(jwtRegex) || signInTokenClient.contains(currentCredentialsClient.email))
                        result = CheckResult.wrong("Invalid JWT token")
                    // return@testApplication
                } catch (e: Exception) {
                    result = CheckResult.wrong("Cannot get token form /signup request")
                }
                println("After signup!")

                response = client.post("/signin") {
                    println()
                    println("in response = client.post(\"/signin\")")
                    setBody(Json.encodeToString(currentCredentialsClient))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                try {
                    val principal = Json.decodeFromString<Token>(response.bodyAsText() ?: "")
                    signInTokenClient = principal.token
                    if (!signInTokenClient.matches(jwtRegex) || signInTokenClient.contains(currentCredentialsClient.email))
                        result = CheckResult.wrong("Invalid JWT token")
                    // return@testApplication
                } catch (e: Exception) {
                    result = CheckResult.wrong("Cannot get token form /signin request")
                }

                ///////////////////////////////////////////////////////////////////

                response = client.post("/signup") {
                    setBody(Json.encodeToString(currentCredentialsStaff))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                try {
                    println()
                    println("Staff - response.bodyAsText() = ${response.bodyAsText()}")
                    println("Staff - currentCredentialsClient = $currentCredentialsClient")
                    val principal = Json.decodeFromString<Token>(response.bodyAsText() ?: "")
                    signInTokenStaff = principal.token
                    println("signInTokenStaff = $signInTokenStaff")
                    if (!signInTokenStaff.matches(jwtRegex) || signInTokenStaff.contains(currentCredentialsStaff.email))
                        result = CheckResult.wrong("Invalid JWT token")
                    // return@testApplication
                } catch (e: Exception) {
                    result = CheckResult.wrong("Cannot get token form /signup request")
                }

                response = client.post("/signin") {
                    setBody(Json.encodeToString(currentCredentialsStaff))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                try {
                    val principal = Json.decodeFromString<Token>(response.bodyAsText() ?: "")
                    signInTokenStaff = principal.token
                    if (!signInTokenStaff.matches(jwtRegex) || signInTokenStaff.contains(currentCredentialsStaff.email))
                        result = CheckResult.wrong("Invalid JWT token")
                    // return@testApplication
                } catch (e: Exception) {
                    result = CheckResult.wrong("Cannot get token form /signin request")
                }

                /////////////////////////////////////////////////////////////////////
            }
        } catch (e: Exception) {
            result = CheckResult.wrong(e.message)
        }
        println("end of signup!")
        return result
    }

    @DynamicTest(order = 2)
    fun correctValidation(): CheckResult {
        var result = CheckResult.correct()
        try {
            testApplication {
                var response = client.get("/validate") {
                    header(HttpHeaders.Authorization, "Bearer $signInTokenClient")
                }
                if (response.status != HttpStatusCode.OK
                    || response.bodyAsText() != "Hello, ${currentCredentialsClient.userType} ${currentCredentialsClient.email}") {
                    result = CheckResult.wrong(
                        "testdata.Token validation with signin token failed.\nStatus code should be \"200 OK\"\n" +
                                "Message should be \"Hello, ${currentCredentialsClient.userType} ${currentCredentialsClient.email}\"")
                    return@testApplication
                }
                response = client.get("/validate") {
                    header(HttpHeaders.Authorization, "Bearer $signInTokenStaff")
                }
                if (response.status != HttpStatusCode.OK
                    || response.bodyAsText() != "Hello, ${currentCredentialsStaff.userType} ${currentCredentialsStaff.email}") {
                    result = CheckResult.wrong(
                        "testdata.Token validation with signin token failed.\nStatus code should be \"200 OK\"\n" +
                                "Message should be \"Hello, ${currentCredentialsStaff.userType} ${currentCredentialsStaff.email}\"")
                    return@testApplication
                }
            }
        } catch (e: Exception) {
            result = CheckResult.wrong(e.message)
        }
        return result
    }

    @DynamicTest(order = 3)
    fun createUser(): CheckResult {
        var result = CheckResult.correct()
        try {
            testApplication {
                val response = client.put("/me") {
                    setBody(Json.encodeToString(currentUserClient))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header(HttpHeaders.Authorization, "Bearer $signInTokenClient")
                }
                if (response.status != HttpStatusCode.OK)
                    result = CheckResult.wrong("Cannot add user by put method")
            }
        } catch (e: Exception) {
            result = CheckResult.wrong(e.message)
        }
        return result
    }

    @DynamicTest(order = 4)
    fun successAdditionMeal(): CheckResult {
        var result = CheckResult.correct()
        try {
            testApplication {
                lateinit var response: HttpResponse
                println()
                println("currentMeals = ${currentMeals.toList()}")
                for (meal in currentMeals){
                    println("meal = $meal")
                    response = client.post("/meals") {
                        setBody(Json.encodeToString(meal))
                        header(HttpHeaders.Authorization, "Bearer $signInTokenStaff")
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                }
                println()
                if (response.status != HttpStatusCode.OK) {
                    result = CheckResult.wrong("The meal was not added. Wrong status code.")
                    return@testApplication
                }
            }
        } catch (e: Exception) {
            result = CheckResult.wrong(e.message)
        }
        return result
    }

    @DynamicTest(order = 5)
    fun invalidOrderCreation(): CheckResult {
        var result = CheckResult.correct()
        try {
            println("\nTest5")
            println("signInTokenClient = $signInTokenClient")

            testApplication {
                val response = client.post("/order") {
                    setBody(Json.encodeToString(listOf(1, 2, (-9999999..-9999).random())))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header(HttpHeaders.Authorization, "Bearer $signInTokenClient")
                }

                if (response.status != HttpStatusCode.BadRequest)
                    result = CheckResult.wrong("Created an order with the wrong meal id. Wrong status code.")
            }
        } catch (e: Exception) {
            result = CheckResult.wrong(e.message)
        }
        return result
    }

    @DynamicTest(order = 6)
    fun validOrderCreation(): CheckResult {
        var result = CheckResult.correct()
        try {
            testApplication {
                println()
                println("mealsIds = $mealsIds")
                println("currentMeals = ${currentMeals.toList()}")
                val response = client.post( "/order") {
                    setBody(Json.encodeToString(mealsIds))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header(HttpHeaders.Authorization, "Bearer $signInTokenClient")
                }
                if (response.status != HttpStatusCode.OK) {
                    result = CheckResult.wrong("Unable to create order. Wrong status code.")
                    return@testApplication
                }
                val order = Json.decodeFromString<Order>(response.bodyAsText() ?: "")
                println("order = $order")
                println("currentOrder = $currentOrder")
                if (order.userEmail != currentOrder.userEmail || order.price != currentOrder.price || order.address != currentOrder.address || order.status != currentOrder.status) {
                    result = CheckResult.wrong("Wrong order.")
                    return@testApplication
                }
                else currentOrder.orderId = order.orderId
            }
        } catch (e: Exception) {
            result = CheckResult.wrong(e.message)
        }
        return result
    }

    @DynamicTest(order = 7)
    fun invalidMarkAsReady(): CheckResult {
        var result = CheckResult.correct()
        try {
            testApplication {
                val response = client.post("/order/${currentOrder.orderId}/markReady") {
                    setBody(Json.encodeToString(mealsIds))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header(HttpHeaders.Authorization, "Bearer $signInTokenClient")
                }
                if (response.status != HttpStatusCode.Forbidden || response.bodyAsText() != accessDenied)
                    result = CheckResult.wrong("Only staff can mark order as COMPLETE. Wrong status code.")
            }
        } catch (e: Exception) {
            result = CheckResult.wrong(e.message)
        }
        return result
    }

    @DynamicTest(order = 8)
    fun validMarkAsReady(): CheckResult {
        var result = CheckResult.correct()
        try {
            testApplication {
                val response = client.post("/order/${currentOrder.orderId}/markReady") {
                    setBody(Json.encodeToString(mealsIds))
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header(HttpHeaders.Authorization, "Bearer $signInTokenStaff")
                }
                if (response.status != HttpStatusCode.OK)
                    result = CheckResult.wrong("Unable to mark order as COMPLETE. Wrong status code.")
            }
        } catch (e: Exception) {
            result = CheckResult.wrong(e.message)
        }
        return result
    }

    @DynamicTest(order = 9)
    fun getOrders(): CheckResult {
        var result = CheckResult.correct()
        try {
            testApplication {
                val response = client.get("/orderHistory") {
                    header(HttpHeaders.Authorization, "Bearer $signInTokenClient")
                }
                val orders: List<Order> = Json.decodeFromString(response.bodyAsText() ?: "")
                var flag = true
                for (order in orders) {
                    if (order.status == "COOK") incompleteSize++
                    if (order.orderId == currentOrder.orderId) flag = false
                }
                if (flag) {
                    result = CheckResult.wrong("Wrong orders list. The newly added order is missing.")
                    return@testApplication
                }
                if (response.status != HttpStatusCode.OK)
                    result = CheckResult.wrong("Wrong status code in /orderHistory")
            }
        } catch (e: Exception) {
            result = CheckResult.wrong(e.message)
        }
        return result
    }

    @DynamicTest(order = 10)
    fun getIncompleteOrders(): CheckResult {
        var result = CheckResult.correct()
        try {
            testApplication {
                val response = client.get("/orderIncomplete") {
                    header(HttpHeaders.Authorization, "Bearer $signInTokenClient")
                }
                val orders: List<Order> = Json.decodeFromString(response.bodyAsText() ?: "")
                for (order in orders)
                    if (order.status != "COOK") {
                        result = CheckResult.wrong("One of the orders is COMPLETE.")
                        return@testApplication
                    }
                if (orders.size != incompleteSize) {
                    result = CheckResult.wrong("Invalid size of Incomplete orders.")
                    return@testApplication
                }
                if (response.status != HttpStatusCode.OK)
                    result = CheckResult.wrong("Wrong status code in /orderHistory")
            }
        } catch (e: Exception) {
            result = CheckResult.wrong(e.message)
        }
        return result
    }

}