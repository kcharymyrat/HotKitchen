package hotkitchen


import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import hotkitchen.plugins.configureRouting
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import hotkitchen.models.*
import io.ktor.http.*

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*


fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module(testing: Boolean = false) {
    val secret = "secret"
    val issuer = "http://localhost:28852/"
    val audience = "http://localhost:28852/hotkitchen"
    var realm = "Access to 'hotkitchen'"

    install(Authentication) {
        jwt("myAuth") {
            realm = realm
            verifier(JWT
                .require(Algorithm.HMAC256(secret))
                .withAudience(audience)
                .withIssuer(issuer)
                .build())
            validate { credential ->
                println("credential = $credential")
                run {
                    println("credential.payload = ${credential.payload}")
                    JWTPrincipal(credential.payload)
                }
            }
            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
    val database by lazy {
        Database.connect(
            url = "jdbc:postgresql://localhost:5432/postgres",
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = "091189"
        )
    }
    transaction(database) {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(Users, UserInfo, Meals, Categories, MealCategories, Orders, OrderMeals)
    }
    configureRouting()
}