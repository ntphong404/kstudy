package vn.edu.actvn.kstudy.ui.common

import android.view.View
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout

// Inline validation shared by the forms: the message appears under the field it is about
object FormFields {

    private const val REQUIRED = "Vui lòng nhập thông tin này"

    // Shows the message under the field; null removes it together with the space it took
    fun setError(field: TextInputLayout, message: String?) {
        field.error = message
        field.isErrorEnabled = message != null
    }

    // Looks up the required fields of a form; an error under one of them disappears when it is edited
    fun required(form: View, vararg ids: Int): Array<TextInputLayout> {
        val fields = ids.map { form.findViewById<TextInputLayout>(it) }.toTypedArray()
        clearErrorOnEdit(*fields)
        return fields
    }

    // Marks every empty field and returns true when all of them are filled in
    fun requireFilled(vararg fields: TextInputLayout): Boolean {
        var allFilled = true
        for (field in fields) {
            val empty = field.editText?.text.isNullOrBlank()
            setError(field, if (empty) REQUIRED else null)
            if (empty) allFilled = false
        }
        return allFilled
    }

    // An error goes away as soon as the user changes the field it belongs to
    fun clearErrorOnEdit(vararg fields: TextInputLayout) {
        for (field in fields) {
            field.editText?.doAfterTextChanged { setError(field, null) }
        }
    }
}
