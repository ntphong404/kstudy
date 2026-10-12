package vn.edu.actvn.kstudy.data.model

// Personal details every account can have; all of them are optional
data class PersonalInfo(
    val email: String = "",
    val dateOfBirth: String = "", // dd/MM/yyyy
    val gender: String = "",
    val address: String = ""
)
