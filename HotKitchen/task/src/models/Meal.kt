package hotkitchen.models

import hotkitchen.models.Meals.uniqueIndex
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*


object Meals : IntIdTable() {
    val externalId = integer("externalId").uniqueIndex()
    val title = varchar("title", 255)
    val price = float("price")
    val imageUrl = varchar("imageUrl", 255)
    // categoryIds would be handled in a separate junction table
}

object Categories : IntIdTable() {
    val externalId = integer("externalId").uniqueIndex()
    val title = varchar("title", 255)
    val description = varchar("description", 255)
}

// A junction table for the many-to-many relationship between Meals and Categories
object MealCategories : IntIdTable() {
    val mealId = reference("mealId", Meals)
    val categoryId = reference("categoryId", Categories)
//    override val primaryKey = PrimaryKey(mealId, categoryId)
}