package vn.edu.actvn.kstudy.ui.student

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.location.DeviceLocation
import vn.edu.actvn.kstudy.viewmodel.StudentViewModel

class StudentScanFragment : Fragment() {

    private var studentUserId: String = ""
    private lateinit var viewModel: StudentViewModel

    private val barcodeLauncher = registerForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        if (result.contents == null) {
            Toast.makeText(requireContext(), "Đã hủy quét", Toast.LENGTH_SHORT).show()
        } else {
            val qrText = result.contents
            val etCode = view?.findViewById<EditText>(R.id.etScanCode)
            etCode?.setText(qrText)

            val studentId = viewModel.student.value?.id ?: ""
            if (studentId.isNotEmpty()) {
                viewModel.markAttendance(studentId, qrText, System.currentTimeMillis())
            }
        }
    }

    // What to carry on with once the student has answered the location permission dialog
    private var afterLocationPermission: (() -> Unit)? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Carry on whatever the answer: a session without a location check still works, and one
        // with a check explains what is missing when it refuses the scan.
        afterLocationPermission?.invoke()
        afterLocationPermission = null
    }

    companion object {
        fun newInstance(userId: String): StudentScanFragment {
            return StudentScanFragment().apply {
                arguments = Bundle().apply {
                    putString("userId", userId)
                }
            }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        arguments?.let {
            studentUserId = it.getString("userId") ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_student_scan, container, false)

        viewModel = ViewModelProvider(this)[StudentViewModel::class.java]

        val etCode = view.findViewById<EditText>(R.id.etScanCode)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitScanCode)
        val btnStartScan = view.findViewById<Button>(R.id.btnStartCameraScan)

        val btnBack = view.findViewById<View>(R.id.btnBackStudentScan)
        btnBack.setOnClickListener {
            (activity as? StudentMainActivity)?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.studentBottomNav)?.selectedItemId = R.id.menu_student_classes
        }

        btnStartScan.setOnClickListener {
            // The permission is asked before the camera opens, so nothing stands between
            // scanning the code and sending it while the code is still valid
            withLocationPermission {
                val options = com.journeyapps.barcodescanner.ScanOptions().apply {
                    setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                    setPrompt("Đưa mã QR điểm danh của giáo viên vào khung quét")
                    setCameraId(0)
                    setBeepEnabled(true)
                    setBarcodeImageEnabled(false)
                    setOrientationLocked(true)
                }
                barcodeLauncher.launch(options)
            }
        }

        viewModel.student.observe(viewLifecycleOwner) { student ->
            if (student != null) {
                // Keep context updated
            }
        }

        btnSubmit.setOnClickListener {
            val studentId = viewModel.student.value?.id ?: ""
            if (studentId.isEmpty()) {
                Toast.makeText(requireContext(), "Lỗi tải thông tin học sinh!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val code = etCode.text.toString().trim()
            val typedAt = System.currentTimeMillis()
            withLocationPermission {
                viewModel.markAttendance(studentId, code, typedAt)
            }
        }

        viewModel.checkingIn.observe(viewLifecycleOwner) { checking ->
            btnSubmit.isEnabled = !checking
            btnStartScan.isEnabled = !checking
            btnSubmit.text = if (checking) "ĐANG KIỂM TRA…" else "XÁC NHẬN ĐIỂM DANH"
        }

        viewModel.attendanceResult.observe(viewLifecycleOwner) { result ->
            if (result != null) {
                val (success, message) = result
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                if (success) {
                    etCode.text.clear()
                }
                viewModel.clearAttendanceResult()
            }
        }

        viewModel.loadStudent(studentUserId)

        return view
    }

    private fun withLocationPermission(action: () -> Unit) {
        if (DeviceLocation.hasPermission(requireContext())) {
            action()
        } else {
            afterLocationPermission = action
            locationPermissionLauncher.launch(DeviceLocation.PERMISSIONS)
        }
    }
}
