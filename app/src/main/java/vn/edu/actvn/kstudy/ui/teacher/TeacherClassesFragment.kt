package vn.edu.actvn.kstudy.ui.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.ui.adapter.ClassSelectionAdapter
import vn.edu.actvn.kstudy.viewmodel.TeacherViewModel

class TeacherClassesFragment : Fragment() {

    private var teacherUserId: String = ""
    private lateinit var viewModel: TeacherViewModel
    private lateinit var adapter: ClassSelectionAdapter

    companion object {
        fun newInstance(userId: String): TeacherClassesFragment {
            return TeacherClassesFragment().apply {
                arguments = Bundle().apply {
                    putString("userId", userId)
                }
            }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        arguments?.let {
            teacherUserId = it.getString("userId") ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_teacher_classes, container, false)

        viewModel = ViewModelProvider(this)[TeacherViewModel::class.java]

        val tvWelcome = view.findViewById<TextView>(R.id.tvTeacherWelcome)
        val tvDetails = view.findViewById<TextView>(R.id.tvTeacherSubDetails)
        val rv = view.findViewById<RecyclerView>(R.id.rvTeacherClasses)

        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = ClassSelectionAdapter(emptyList()) { courseClass ->
            val fragment = TeacherClassDetailFragment.newInstance(
                courseClass.id, courseClass.name, courseClass.academicYear, courseClass.schedule
            )
            parentFragmentManager.beginTransaction()
                .replace(R.id.teacherFragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }
        rv.adapter = adapter

        viewModel.teacher.observe(viewLifecycleOwner) { teacher ->
            if (teacher != null) {
                tvWelcome.text = "Xin chào, ${teacher.name}"
                tvDetails.text = "Môn học phụ trách: ${teacher.subject} • SĐT: ${teacher.phone}"
            }
        }

        viewModel.classes.observe(viewLifecycleOwner) { classes ->
            adapter.updateData(classes)
        }

        viewModel.loadTeacher(teacherUserId)

        return view
    }
}
