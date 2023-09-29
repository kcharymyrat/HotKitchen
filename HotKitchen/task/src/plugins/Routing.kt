package hotkitchen.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import hotkitchen.models.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*


@Serializable
data class UserSignUp(val email: String, val userType: String, val password:String)

@Serializable
data class UserSignIn(val email: String, @EncodeDefault val userType: String="client", val password:String)


@Serializable
data class UserInfoSerializer(
    val name: String, val userType: String, val phone: String, val email: String, val address: String
)

// {"mealId":-658960748,"title":"1695853121172 title","price":-48.0,"imageUrl":"image 1695853121172 url","categoryIds":[1,4,3]}
@Serializable
data class MealPayload(
    val mealId: Int,
    @EncodeDefault val title: String = "",
    @EncodeDefault val price: Float = -1f,
    @EncodeDefault val imageUrl: String = "",
    @EncodeDefault val categoryIds: List<Int> = listOf()
)

@Serializable
data class CategoryPayload(
    @EncodeDefault val categoryId: Int = 0,
    @EncodeDefault val title: String = "",
    @EncodeDefault val description: String = "",
)

@Serializable
data class OrderPayload(
    var orderId: Int,
    val userEmail: String,
    val mealsIds: List<Int>,
    val price: Float,
    val address: String,
    val status: String
)

fun isValidEmail(email: String): Boolean {
    val emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    return email.matches(emailRegex.toRegex())
}

fun isValidPassword(password: String): Boolean {
    val passwordRegex = "^(?=.+[A-Za-z])(?=.+\\d)[A-Za-z\\d]{6,}\$".toRegex()
    return password.matches(passwordRegex)
}


fun Application.configureRouting() {
    val secret = "secret"
    val issuer = "http://localhost:28852/"
    val audience = "http://localhost:28852/hotkitchen"
    val realm = "Access to 'hotkitchen'"

    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        post("/signup") {
            println()
            println("--------------------------------------")
            println("post(\"/signup\") ")
            val user = Json.decodeFromString(UserSignUp.serializer(), call.receiveText())
            println("user = $user")

            if (!isValidEmail(user.email) && !isValidPassword(user.password)) {
                println(mapOf("status" to "Invalid email or password"))
                call.respondText(
                    text = Json.encodeToString(mapOf("status" to "Invalid email or password")),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.Forbidden
                )
            } else if (!isValidEmail(user.email)) {
                println(mapOf("status" to "Invalid email"))
                call.respondText(
                    text = Json.encodeToString(mapOf("status" to "Invalid email")),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.Forbidden
                )
            } else if(!isValidPassword(user.password)) {
                println(mapOf("status" to "Invalid password"))
                call.respondText(
                    text = Json.encodeToString(mapOf("status" to "Invalid password")),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.Forbidden
                )
            } else {
                var userExists : ResultRow? = null
                transaction {
                    val existingUser = Users.select { Users.email eq user.email }.singleOrNull()
                    userExists = existingUser
                }
                println("userExists = $userExists")
                if (userExists == null) {
                    transaction {
                        Users.insert {
                            it[email] = user.email
                            it[userType] = user.userType
                            it[password] = user.password
                        }
                    }
                    val token = JWT.create()
                        .withAudience(audience)
                        .withIssuer(issuer)
                        .withClaim("email", user.email)
                        .withClaim("userType", user.userType)
                        .withExpiresAt(Date(System.currentTimeMillis() + 24 * 60 * 60000))
                        .sign(Algorithm.HMAC256(secret))
                    println(Json.encodeToString(hashMapOf("token" to token)))
                    call.respondText(
                        text = Json.encodeToString(hashMapOf("token" to token)),
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.OK
                    )
                } else {
                    println(mapOf("status" to "User already exists"))
                    call.respondText(
                        text = Json.encodeToString(mapOf("status" to "User already exists")),
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.Forbidden
                    )
                }
            } // end of else {}
        }  // end of post("/singup")

        post("/signin") {
            println()
            println("post(\"/signin\")")
            val user = Json.decodeFromString(UserSignIn.serializer(), call.receiveText())
            println("user = $user")

            try {
                transaction {
                    val userDb = Users.select { (Users.email eq user.email) and (Users.password eq user.password) }.singleOrNull() ?: throw Exception()
                    println("in try: userDb = $userDb")
                }
                val token = JWT.create()
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .withClaim("email", user.email)
                    .withClaim("userType", user.userType)
                    .withExpiresAt(Date(System.currentTimeMillis() + 24 * 60 * 60000))
                    .sign(Algorithm.HMAC256("secret"))
                call.respondText(
                    text = Json.encodeToString(hashMapOf("token" to token)),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            } catch (e: Exception) {
                call.respondText(
                    text = Json.encodeToString(mapOf("status" to "Invalid email or password")),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.Forbidden
                )
            }
        }  // end of post("/signin")


        authenticate("myAuth") {

            get("/validate") {
                println()
                println("/validate")
                println("Received: ${call.receiveText()}")

                val principal = call.principal<JWTPrincipal>()
                val email = principal!!.payload.getClaim("email").asString()
                val expiresAt = principal.expiresAt?.time?.minus(System.currentTimeMillis())

                println("email = $email")
                var existingUser: ResultRow? = null
                transaction {
                    existingUser =  Users.select { Users.email eq email }.singleOrNull()
                    println("existingUser = $existingUser")
                }
                val userEmail = existingUser!![Users.email]
                val userType = existingUser!![Users.userType]
                println("userEmail = $userEmail, userType = $userType")

                call.respondText("Hello, $userType $userEmail", status = HttpStatusCode.OK)
            }  // enf of get("/validate")

            get("/me") {
                println()
                println("get(/me)")
                println("call.receiveText()= ${call.receiveText()}")

                val principal = call.principal<JWTPrincipal>()
                println("principal = $principal")
                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                }
                val name = principal!!.payload.getClaim("name").asString()
                val userType = principal!!.payload.getClaim("userType").asString()
                val phone = principal!!.payload.getClaim("phone").asString()
                val email = principal!!.payload.getClaim("email").asString()
                val address = principal!!.payload.getClaim("address").asString()
                println("name = $name, userType = $userType, phone = $phone, email = $email, address = $address")

                var existingUser: ResultRow? = null
                transaction {
                    existingUser = try {
                        UserInfo.select { UserInfo.email eq email }.singleOrNull()
                    } catch (e: Exception) {
                        null
                    }
                }
                println("existingUser = $existingUser")
                if (existingUser == null) {
                    call.respond(HttpStatusCode.BadRequest, "Bad Request")
                } else {
                    val userInfoMap = mapOf(
                        "name" to existingUser!![UserInfo.name],
                        "userType" to existingUser!![UserInfo.userType],
                        "phone" to existingUser!![UserInfo.phone],
                        "email" to existingUser!![UserInfo.email],
                        "address" to existingUser!![UserInfo.address]
                    )
                    println("userInfoMap = $userInfoMap")
                    call.respondText(
                        text = Json.encodeToString(userInfoMap),
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.OK
                    )
                }
            }  // end of get("/me")

            put("/me") {
                println()
                println("put(/me)")
                val userInfo = Json.decodeFromString(UserInfoSerializer.serializer(), call.receiveText())
                println("userInfo= $userInfo")

                val principal = call.principal<JWTPrincipal>()
                println("principal = $principal")
                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                }
                val name = principal!!.payload.getClaim("name").asString()
                val userType = principal!!.payload.getClaim("userType").asString()
                val phone = principal!!.payload.getClaim("phone").asString()
                val email = principal!!.payload.getClaim("email").asString()
                val address = principal!!.payload.getClaim("address").asString()
                println("name = $name, userType = $userType, phone = $phone, email = $email, address = $address")

                var existingUser: ResultRow? = null
                transaction {
                    existingUser = try {
                        UserInfo.select { UserInfo.email eq email }.singleOrNull()
                    } catch (e: Exception) {
                        null
                    }
                }
                println("existingUser = $existingUser")
                if (existingUser == null) {
                    println("in UserInfo.insert {}")
                    addUserInfo(userInfo)
                    transaction {
                        val newUserInfo = try {
                            Users.select { UserInfo.email eq email }.singleOrNull()
                        } catch (e: Exception) {
                            null
                        }
                        println("newUserInfo = $newUserInfo")
                    }
                    call.respondText(
                        text = Json.encodeToString(userInfo),
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.OK
                    )
                } else {
                    if (userInfo.email == email) {
                        updateUserInfo(userInfo)
                        call.respondText(
                            text = Json.encodeToString(userInfo),
                            contentType = ContentType.Application.Json,
                            status = HttpStatusCode.OK
                        )
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "Bad Request")
                    }
                }
            }  // end of put("/me")

            delete("/me") {
                println()
                println("delete(/me)")
                println("call.receiveText()= ${call.receiveText()}")

                val principal = call.principal<JWTPrincipal>()
                println("principal = $principal")
                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                }
                val name = principal!!.payload.getClaim("name").asString()
                val userType = principal!!.payload.getClaim("userType").asString()
                val phone = principal!!.payload.getClaim("phone").asString()
                val email = principal!!.payload.getClaim("email").asString()
                val address = principal!!.payload.getClaim("address").asString()
                println("name = $name, userType = $userType, phone = $phone, email = $email, address = $address")

                var existingUser: ResultRow? = null
                transaction {
                    existingUser = try {
                        UserInfo.select { UserInfo.email eq email }.singleOrNull()
                    } catch (e: Exception) {
                        null
                    }
                }
                println("existingUser = $existingUser")
                if (existingUser == null) {
                    call.respond(HttpStatusCode.NotFound, "Not Found")
                } else {
                    transaction {
                        UserInfo.deleteWhere { UserInfo.email eq email }
                        Users.deleteWhere { Users.email eq email }
                    }
                    call.respond(HttpStatusCode.OK, "Successfully Deleted")
                }
            }  // end of delete("/me")

            post("/meals") {
                println()
                println("post(\"/meals\")")
                val meal = Json.decodeFromString(MealPayload.serializer(), call.receiveText())
                println("meal = $meal")

                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userType = principal?.payload?.getClaim("userType")?.asString()
                    println("userType = $userType")
                    if (userType?.trim()?.lowercase() != "staff") {
                        call.respondText(
                            text = Json.encodeToString(mapOf("status" to "Access denied")),
                            contentType = ContentType.Application.Json,
                            status = HttpStatusCode.Forbidden
                        )
                    } else {
                        addMeal(meal)
                        call.respondText(
                            text = Json.encodeToString(meal),
                            contentType = ContentType.Application.Json,
                            status = HttpStatusCode.OK
                        )
                    }
                } catch (e: Exception) {
                    call.respondText(
                        text = "",
                        status = HttpStatusCode.BadRequest
                    )
                }
            }  // end of post("/meals")

            post("/categories") {
                println()
                println("post(\"/categories\")")
                val category = Json.decodeFromString(CategoryPayload.serializer(), call.receiveText())
                println("category = $category")

                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userType = principal?.payload?.getClaim("userType")?.asString()
                    println("userType = $userType")
                    if (userType?.trim()?.lowercase() != "staff") {
                        call.respondText(
                            text = Json.encodeToString(mapOf("status" to "Access denied")),
                            contentType = ContentType.Application.Json,
                            status = HttpStatusCode.Forbidden
                        )
                    } else {
                        var existingCategory: ResultRow? = null
                        transaction {
                            existingCategory = Categories.select { Categories.externalId eq category.categoryId }.singleOrNull()
                        }
                        if (existingCategory == null) {
                            addCategory(category)
                            call.respondText(
                                text = "Success",
                                status = HttpStatusCode.OK
                            )
                        } else {
                            call.respondText(
                                text = "",
                                status = HttpStatusCode.BadRequest
                            )
                        }
                    }
                } catch (e: Exception) {
                    call.respondText(
                        text = "",
                        status = HttpStatusCode.BadRequest
                    )
                }
            }  // end of post("/categories")

            get("/meals") {
                println()
                println("get(\"/meals\")")
                println("call.receiveText() = ${call.receiveText()}")
                val mealIdInt = call.request.queryParameters["id"]?.toInt()
                println("mealIdInt = $mealIdInt")

                val principal = call.principal<JWTPrincipal>()
                val userType = principal?.payload?.getClaim("userType")?.asString()
                println("userType = $userType")

                if (userType != null) {
                    println("userType != null -> True")
                    if (mealIdInt != null) {
                        println("mealIdInt != null -> True")
                        var meal: MealPayload? = null
                        transaction {
                            meal = Meals.select { Meals.externalId eq mealIdInt }.singleOrNull()?.let { res ->
                                val categoryIds = MealCategories.innerJoin(Categories)
                                    .select { MealCategories.mealId eq res[Meals.id] }
                                    .map { it[Categories.externalId] }

                                MealPayload(
                                    mealId = res[Meals.externalId],
                                    title = res[Meals.title],
                                    price = res[Meals.price],
                                    imageUrl = res[Meals.imageUrl],
                                    categoryIds = categoryIds
                                )
                            }
                        }

                        if (meal != null) {
                            println("meal = $meal")
                            call.respondText(
                                text = Json.encodeToString(meal),
                                contentType = ContentType.Application.Json,
                                status = HttpStatusCode.OK
                            )
                        } else {
                            println("meal = $meal")
                            call.respondText(
                                text = "",
                                status = HttpStatusCode.BadRequest
                            )
                        }
                    } else {
                        println("mealIdInt != null -> False")
                        var meals: List<MealPayload>? = null
                        transaction {
                            meals = Meals.selectAll().map { meal ->
                                MealPayload(
                                    mealId = meal[Meals.externalId],
                                    title = meal[Meals.title],
                                    price = meal[Meals.price],
                                    imageUrl = meal[Meals.imageUrl],
                                    categoryIds = MealCategories.select { MealCategories.mealId eq meal[Meals.id] }
                                        .map { it[MealCategories.categoryId].value }
                                )
                            }
                        }
                        println("meals = $meals")
                        call.respondText(
                            text = Json.encodeToString(meals),
                            contentType = ContentType.Application.Json,
                            status = HttpStatusCode.OK
                        )
                    }
                } else {
                    call.respondText(
                        text = "",
                        status = HttpStatusCode.BadRequest
                    )
                }

            }  // end of get("/meals")

            get("/categories") {
                println()
                println("get(\"/categories\")")
                println("call.receiveText() = ${call.receiveText()}")
                val categoryIdInt = call.request.queryParameters["id"]?.toInt()
                println("categoryIdInt = $categoryIdInt")

                val principal = call.principal<JWTPrincipal>()
                val userType = principal?.payload?.getClaim("userType")?.asString()
                println("userType = $userType")

                if (userType != null) {
                    println("userType != null -> True")
                    if (categoryIdInt != null) {
                        println("categoryIdInt != null -> True")
                        var category: CategoryPayload? = null
                        transaction {
                            category = Categories.select { Categories.externalId eq categoryIdInt }.singleOrNull()?.let { res ->
                                CategoryPayload(
                                    categoryId = res[Categories.externalId],
                                    title = res[Categories.title],
                                    description = res[Categories.description]
                                )
                            }
                        }

                        if (category != null) {
                            println("category = $category")
                            call.respondText(
                                text = Json.encodeToString(category),
                                contentType = ContentType.Application.Json,
                                status = HttpStatusCode.OK
                            )
                        } else {
                            println("category = $category")
                            call.respondText(
                                text = "",
                                status = HttpStatusCode.BadRequest
                            )
                        }
                    } else {
                        println("categoryIdInt != null -> False")
                        var categories: List<CategoryPayload>? = null
                        transaction {
                            categories = Categories.selectAll().map { cat ->
                                CategoryPayload(
                                    categoryId = cat[Categories.externalId],
                                    title = cat[Categories.title],
                                    description = cat[Categories.description]
                                )

                            }
                        }
                        println("categories = $categories")
                        call.respondText(
                            text = Json.encodeToString(categories),
                            contentType = ContentType.Application.Json,
                            status = HttpStatusCode.OK
                        )
                    }
                } else {
                    call.respondText(
                        text = "",
                        status = HttpStatusCode.BadRequest
                    )
                }
            }  // end of get("/categories")

            post("/order") {
                println()
                println("post(\"/order\")")
                val orderMealString = call.receiveText()
                println("orderMealString = $orderMealString")
                val orderMealList = orderMealString.removeSurrounding("[", "]")
                                            .split(",")
                                            .map { it.trim().toInt() }
                println("orderMealList = $orderMealList")

                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userType = principal?.payload?.getClaim("userType")?.asString()
                    val email = principal?.payload?.getClaim("email")?.asString()
                    val address: String = email?.split("@")?.get(0).toString() + "address"
                    val areAllMealsExist = areAllMeals(orderMealList)

                    println("email = $email")
                    println("address = $address")
                    println("userType = $userType")
                    println("areAllMealsExist = $areAllMealsExist")

                    if (!areAllMealsExist) {
                        println("if (!areAllMealsExist)")
                        call.respondText(
                            text = "",
                            contentType = ContentType.Application.Json,
                            status = HttpStatusCode.BadRequest
                        )
                    } else {
                        println("else for -> if (!areAllMealsExist)")
                        if (userType == null) {
                            call.respondText(
                                text = Json.encodeToString(mapOf("status" to "Access denied")),
                                contentType = ContentType.Application.Json,
                                status = HttpStatusCode.Forbidden
                            )
                        } else {
                            val orderPrice = calculatePrice(orderMealList)
                            println("orderPrice = $orderPrice")
                            val orderId = addOrder(email, address, orderPrice, orderMealList)
                            println("orderId = $orderId")
                            val orderPayload = createOrderPayload(
                                orderId,
                                email.toString(),
                                orderMealList,
                                orderPrice,
                                address,
                                "COOK"
                            )
                            println("orderPayload = $orderPayload")
                            call.respondText(
                                text = Json.encodeToString(orderPayload),
                                contentType = ContentType.Application.Json,
                                status = HttpStatusCode.OK
                            )
                        }
                    }
                } catch (e: Exception) {
                    call.respondText(
                        text = "",
                        status = HttpStatusCode.BadRequest
                    )
                }
            }  // end of post("/orders")

            post("/order/{orderId}/markReady") {
                val orderId = call.parameters["orderId"]?.toIntOrNull()
                println()
                println("orderId = $orderId")
                if (orderId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing orderId")
                    return@post
                } else {
                    val principal = call.principal<JWTPrincipal>()
                    val userType = principal?.payload?.getClaim("userType")?.asString()
                    println("userType = $userType")
                    if (userType != "staff") {
                        call.respondText(
                            text = Json.encodeToString(mapOf("status" to "Access denied")),
                            contentType = ContentType.Application.Json,
                            status = HttpStatusCode.Forbidden
                        )
                    } else {
                        if (!isOrderUpdated(orderId)) {
                            call.respond(HttpStatusCode.BadRequest, "Invalid or missing orderId")
                        } else {
                            val orderPayload = getOrderPayloadFromId(orderId)
                            call.respondText(
                                text = Json.encodeToString(orderPayload),
                                contentType = ContentType.Application.Json,
                                status = HttpStatusCode.OK
                            )
                        }
                    }
                }

            }  // end of post("/order/{orderId}/markReady")

            get("/orderHistory") {
                println()
                println("post(\"/orderHistory\")")
                val principal = call.principal<JWTPrincipal>()
                val userType = principal?.payload?.getClaim("userType")?.asString()
                val email = principal?.payload?.getClaim("email")?.asString()
                println("email = $email, userType = $userType")
                if (userType != null) {
                    val orderPaylodList = getAllOrdersByEmail(email.toString())
                    println("orderPaylodList = $orderPaylodList")
                    call.respondText(
                        text = Json.encodeToString(orderPaylodList),
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.OK
                    )
                } else {
                    call.respondText(
                        text = "",
                        status = HttpStatusCode.BadRequest
                    )
                }
            }  // end of get("/orderHistory")

            get("/orderIncomplete") {
                println()
                println("post(\"/orderHistory\")")
                val principal = call.principal<JWTPrincipal>()
                val userType = principal?.payload?.getClaim("userType")?.asString()
                val email = principal?.payload?.getClaim("email")?.asString()
                println("email = $email, userType = $userType")
                if (userType != null) {
                    val orderPaylodList = getOrdersByEmailAndStatus(email.toString(), "COOKS")
                    println("orderPaylodList = $orderPaylodList")
                    call.respondText(
                        text = Json.encodeToString(orderPaylodList),
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.OK
                    )
                } else {
                    call.respondText(
                        text = "",
                        status = HttpStatusCode.BadRequest
                    )
                }
            }  // end of get("/orderIncomplete")

        }  // end of authenticate("myAuth")

    }  // END of Routing
}  // End of function

fun doesOrdersByUserEmailExist(email: String): Boolean {
    var answer = false
    transaction {
        val order = Orders.select { (Orders.userEmail eq email) }.any()
        if (order) {
            answer = true
        }
    }
    return answer
}

fun getAllOrdersByEmail(email: String): List<OrderPayload?> {
    println()
    println("in getOrdersByEmailAndStatus(email: String, status: String): List<OrderPayload?>")
    return transaction {
        Orders.select { (Orders.userEmail eq email) }
            .map { rowToOrder(it) }
    }
}

fun getOrdersByEmailAndStatus(email: String, status: String): List<OrderPayload?> {
    println()
    println("in getOrdersByEmailAndStatus(email: String, status: String): List<OrderPayload?>")
    return transaction {
        Orders.select { (Orders.userEmail eq email) and (Orders.status eq status) }
            .map { rowToOrder(it) }
    }
}

fun rowToOrder(row: ResultRow): OrderPayload? {
    println()
    println("in rowToOrder(row: ResultRow): OrderPayload?")
    var orderPayload: OrderPayload? = null
    transaction {
        row.let { res ->
            val mealIds = OrderMeals.innerJoin(Meals)
                .select { OrderMeals.orderId eq res[Orders.id] }
                .map { it[Meals.externalId] }

            orderPayload = createOrderPayload(
                res[Orders.externalId],
                res[Orders.userEmail],
                mealIds,
                res[Orders.price],
                res[Orders.address],
                res[Orders.status]
            )
        }
    }
    println("orderPayload = $orderPayload")
    return orderPayload
}


fun getOrderPayloadFromId(externalOrderId: Int): OrderPayload? {
    println()
    println("in getOrderPayloadFromId(externalOrderId: Int): OrderPayload?")
    var orderPayload: OrderPayload? = null

    transaction {
        Orders.select { Orders.externalId eq externalOrderId }.singleOrNull()?.let { res ->
            val mealIds = OrderMeals.innerJoin(Meals)
                .select { OrderMeals.orderId eq res[Orders.id] }
                .map { it[Meals.externalId] }

            orderPayload = createOrderPayload(
                res[Orders.externalId],
                res[Orders.userEmail],
                mealIds,
                res[Orders.price],
                res[Orders.address],
                res[Orders.status]
            )
        }
    }
    println("orderPayload = $orderPayload")
    return orderPayload
}

fun isOrderUpdated(externalOrderId: Int): Boolean {
    println()
    var isUpdated = false
    transaction {
        val existingOrder = Orders.select { Orders.externalId eq externalOrderId }.singleOrNull()
        println("existingOrder = $existingOrder")
        if (existingOrder != null) {
            Orders.update({ Orders.externalId eq externalOrderId }) {
                it[status] = "COMPLETE"
                isUpdated = true
                println("in Orders.update({ Orders.externalId eq externalOrderId })")
            }
        }
    }

    println("isUpdated = $isUpdated")
    return isUpdated
}

fun createOrderPayload(
    orderId: Int,
    userEmail: String,
    mealsIds: List<Int>,
    price: Float,
    address: String,
    status: String
): OrderPayload {
    return OrderPayload(
        orderId = orderId,
        userEmail = userEmail,
        mealsIds = mealsIds,
        price = price,
        address = address,
        status = status
    )
}

fun calculatePrice(orderMealList: List<Int>): Float {
    var totalPrice = 0.00
    transaction {
        orderMealList.forEach { mealId ->
            println("mealId = $mealId")
            val existingMeal = Meals.select { Meals.externalId eq mealId }.singleOrNull()
            println("existingMeal = $existingMeal")
            if (existingMeal != null) {
                totalPrice += existingMeal[Meals.price]
            }
        }
    }
    return totalPrice.toFloat()
}

fun areAllMeals(orderMealList: List<Int>): Boolean {
    println()
    var allExists = true
    transaction {
        orderMealList.forEach { mealId ->
            println("mealId = $mealId")
            val existingMeal = Meals.select { Meals.externalId eq mealId }.singleOrNull()
            println("existingMeal = $existingMeal")
            if (existingMeal == null) {
                allExists = false
            }
        }
    }
    println("allExist = $allExists")
    return allExists
}

fun addOrder(customerEmail: String?, customerAddress: String?, orderPrice: Float, orderMealList: List<Int>): Int {
    var externalOrderId = orderMealList[0]
    transaction {
        // Insert the meal into the Meals table
        val orderId = Orders.insertAndGetId {
            it[externalId] = orderMealList[0]
            it[userEmail] = customerEmail.toString()
            it[price] = orderPrice
            it[address] = customerAddress.toString()
            it[status] = "IN PROGRESS"
        }
        println("orderId = $orderId, orderRequest")

        orderMealList.forEach { mealExternalId ->
            val meal = Meals.select { Meals.externalId eq mealExternalId }.singleOrNull()
            if (meal != null) {
                OrderMeals.insert {
                    it[OrderMeals.orderId] = orderId
                    it[OrderMeals.mealId] = meal[Meals.id]
                }
            }
        }
    }
    return externalOrderId
}

fun addCategory(category: CategoryPayload) {
    transaction {
        val existingCategory = Categories.select { Categories.externalId eq category.categoryId }.singleOrNull()

        // If the category does not exist, create it
        val validCategoryId = if (existingCategory == null) {
            Categories.insertAndGetId {
                it[externalId] = category.categoryId
                it[title] = category.title
                it[description] = category.description
            }
        } else {
            existingCategory[Categories.id]
        }
        println("validCategoryId = $validCategoryId")
    }
}

fun addMeal(meal: MealPayload) {
    transaction {
        // Insert the meal into the Meals table
        val mealId = Meals.insertAndGetId {
            it[externalId] = meal.mealId
            it[title] = meal.title
            it[price] = meal.price
            it[imageUrl] = meal.imageUrl
        }
        println("mealId = $mealId, meal.mealId = ${meal.mealId}, externalId= ${Meals.externalId}")

        // Insert the category associations into the MealCategories table
        meal.categoryIds.forEach { categoryId ->
            // Check if the category already exists
            val existingCategory = Categories.select { Categories.externalId eq categoryId }.singleOrNull()

            // If the category does not exist, create it
            val validCategoryId = if (existingCategory == null) {
                Categories.insertAndGetId {
                    it[externalId] = categoryId
                    it[title] = "Default Title" // Replace with the actual title
                    it[description] = "Default Description" // Replace with the actual description
                }
            } else {
                existingCategory[Categories.id]
            }
            println("validCategoryId = $validCategoryId")
            // Insert the association into the MealCategories table
            MealCategories.insert {
                it[MealCategories.mealId] = mealId
                it[MealCategories.categoryId] = validCategoryId
            }
        }
    }
}

fun addUserInfo(userInfo: UserInfoSerializer) {
    transaction {
        UserInfo.insert {
            it[name] = userInfo.name
            it[userType] = userInfo.userType
            it[phone] = userInfo.phone
            it[email] = userInfo.email
            it[address] = userInfo.address
        }
    }
}

fun updateUserInfo(userInfo: UserInfoSerializer) {
    transaction {
        UserInfo.update({UserInfo.email eq userInfo.email}) {
            it[name] = userInfo.name
            it[userType] = userInfo.userType
            it[phone] = userInfo.phone
            it[address] = userInfo.address
        }
    }
}

fun checkAllUsers() {
    transaction {
        Users.selectAll().forEach {
            println("User ${it[Users.id]}:  ${it[Users.email]}, ${it[Users.userType]}, ${it[Users.password]},")
        }
        UserInfo.selectAll().forEach {
            println("UserInfo ${it[UserInfo.id]}: ${it[UserInfo.name]}, ${it[UserInfo.email]}")
        }
    }
}