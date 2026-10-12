package vn.edu.actvn.kstudy.data.model

data class Teacher(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val subject: String = "",
    val phone: String = "",
    val avatarUrl: String = "",
    val department: String = "",
    val degree: String = "",
    val personal: PersonalInfo = PersonalInfo()
)
