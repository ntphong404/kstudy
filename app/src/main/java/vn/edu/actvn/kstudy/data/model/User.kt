package vn.edu.actvn.kstudy.data.model

data class User(
    val id: String = "",
    val username: String = "",
    val passwordHash: String = "",
    val fullName: String = "",
    val role: Role = Role.STUDENT,
    val phone: String = "",
    val avatarUrl: String = "",
    // The password was chosen by an admin and has to be replaced before the app can be used
    val mustChangePassword: Boolean = false,
    val personal: PersonalInfo = PersonalInfo(),
    // Filled in for a student
    val studentCode: String = "",
    val className: String = "",
    val major: String = "",
    // Filled in for a teacher
    val subject: String = "",
    val department: String = "",
    val degree: String = ""
)
