package hotkitchen.models

import hotkitchen.models.Meals.uniqueIndex
import org.jetbrains.exposed.dao.id.IntIdTable

object Orders : IntIdTable() {
    var externalId = integer("externalId").uniqueIndex()
    val userEmail = varchar("userEmail", 255)
    // mealsIds would be handled in a separate junction table
    val price = float("price")
    val address = varchar("address", 255)
    val status = varchar("status", 255)
}

object OrderMeals : IntIdTable() {
    val orderId = reference("orderId", Orders)
    val mealId = reference("mealId", Meals)
    // override val primaryKey = PrimaryKey(orderId, mealId)
}


