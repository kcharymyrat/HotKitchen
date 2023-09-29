package hotkitchen.models

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*


object Users: IntIdTable() {
    val email: Column<String> = varchar("email", 50)
    val userType: Column<String> = varchar("userType", 50)
    val password: Column<String> = varchar("password", 50)
}


object UserInfo: IntIdTable() {
    val name: Column<String> = varchar("name", 50)
    val userType: Column<String> = varchar("userType", 50)
    val phone: Column<String> = varchar("phone", 50)
    val email: Column<String> = varchar("email", 50)
    val address: Column<String> = varchar("address", 50)
}