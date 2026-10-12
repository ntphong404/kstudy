package vn.edu.actvn.kstudy.ui.common

import android.view.View
import android.widget.TextView
import vn.edu.actvn.kstudy.R

// The spinner and the message of view_list_state.xml, shown in place of a list that has no rows yet.
// The spinner is visible from the start and stays until the first result arrives.
class ListState(root: View) {

    private val progress: View = root.findViewById(R.id.pbListLoading)
    private val message: TextView = root.findViewById(R.id.tvListEmpty)

    // Call with every result: hides the spinner and shows the message only when nothing is listed
    fun show(itemCount: Int, emptyMessage: String) {
        progress.visibility = View.GONE
        message.text = emptyMessage
        message.visibility = if (itemCount == 0) View.VISIBLE else View.GONE
    }
}
