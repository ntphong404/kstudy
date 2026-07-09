package vn.edu.actvn.kstudy.data.model

data class User(
    val id: String = "",
    val username: String = "",
    val passwordHash: String = "",
    val fullName: String = "",
    val role: Role = Role.STUDENT,
    val phone: String = "",
    val avatarUrl: String = ""
)
