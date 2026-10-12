package vn.edu.actvn.kstudy.ui.common

import android.app.DatePickerDialog
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.EditText
import com.google.android.material.textfield.TextInputLayout
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.PersonalInfo
import vn.edu.actvn.kstudy.util.ProfileRules
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// The optional personal details (email, date of birth, gender, address). The profile screen and
// the admin's student and teacher forms hold the same four fields under the same ids.
class PersonalFields(form: View) {

    private val tilEmail = form.findViewById<TextInputLayout>(R.id.tilEmail)
    private val etEmail = form.findViewById<EditText>(R.id.etEmail)
    private val etBirthDate = form.findViewById<EditText>(R.id.etBirthDate)
    private val actvGender = form.findViewById<AutoCompleteTextView>(R.id.actvGender)
    private val etAddress = form.findViewById<EditText>(R.id.etAddress)

    init {
        FormFields.clearErrorOnEdit(tilEmail)
        FormFields.setChoices(actvGender, ProfileRules.GENDERS)
        etBirthDate.setOnClickListener {
            FormFields.hideKeyboard(etBirthDate)
            pickBirthDate()
        }
    }

    fun show(info: PersonalInfo) {
        etEmail.setText(info.email)
        etBirthDate.setText(info.dateOfBirth)
        // Without filtering, otherwise the dropdown would only offer the value it already shows
        actvGender.setText(info.gender, false)
        etAddress.setText(info.address)
    }

    fun read() = PersonalInfo(
        email = etEmail.text.toString().trim(),
        dateOfBirth = etBirthDate.text.toString(),
        gender = actvGender.text.toString(),
        address = etAddress.text.toString().trim()
    )

    // Marks an email that is not an address; returns true when the details can be saved
    fun validate(): Boolean {
        val valid = ProfileRules.isValidEmail(read().email)
        FormFields.setError(tilEmail, if (valid) null else ProfileRules.INVALID_EMAIL)
        return valid
    }

    private fun pickBirthDate() {
        val formatter = SimpleDateFormat(ProfileRules.DATE_PATTERN, Locale.US)
        val day = Calendar.getInstance()
        val chosen = try {
            formatter.parse(etBirthDate.text.toString())
        } catch (e: Exception) {
            null
        }
        if (chosen != null) {
            day.time = chosen
        } else {
            // Opens near the birth year of a student instead of today
            day.add(Calendar.YEAR, -FIRST_PICK_AGE)
        }
        val picker = DatePickerDialog(
            etBirthDate.context,
            { _, year, month, dayOfMonth ->
                day.set(year, month, dayOfMonth)
                etBirthDate.setText(formatter.format(day.time))
            },
            day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH)
        )
        picker.datePicker.maxDate = System.currentTimeMillis()
        picker.show()
    }

    private companion object {
        const val FIRST_PICK_AGE = 20
    }
}
