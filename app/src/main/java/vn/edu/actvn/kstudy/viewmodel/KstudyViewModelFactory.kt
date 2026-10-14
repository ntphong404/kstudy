package vn.edu.actvn.kstudy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import vn.edu.actvn.kstudy.data.ai.AiService
import vn.edu.actvn.kstudy.data.ai.GeminiAiService
import vn.edu.actvn.kstudy.data.repository.AttendanceDataSource
import vn.edu.actvn.kstudy.data.repository.AttendanceStatsRepository
import vn.edu.actvn.kstudy.data.repository.StudentImportRepository
import vn.edu.actvn.kstudy.data.repository.StudentImportStore

// Creates the ViewModels that take their dependencies through the constructor
object KstudyViewModelFactory : ViewModelProvider.Factory {

    // UI tests replace these with in-memory fakes, so the screens can run without Firebase and Gemini
    var dataSourceProvider: () -> AttendanceDataSource = { AttendanceStatsRepository() }
    var aiServiceProvider: () -> AiService = { GeminiAiService() }
    var importStoreProvider: () -> StudentImportStore = { StudentImportRepository() }

    fun useRealServices() {
        dataSourceProvider = { AttendanceStatsRepository() }
        aiServiceProvider = { GeminiAiService() }
        importStoreProvider = { StudentImportRepository() }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(StatsViewModel::class.java) ->
                StatsViewModel(dataSourceProvider(), aiServiceProvider()) as T
            modelClass.isAssignableFrom(AiChatViewModel::class.java) ->
                AiChatViewModel(dataSourceProvider(), aiServiceProvider()) as T
            modelClass.isAssignableFrom(TimetableViewModel::class.java) ->
                TimetableViewModel(dataSourceProvider()) as T
            modelClass.isAssignableFrom(ImportStudentsViewModel::class.java) ->
                ImportStudentsViewModel(importStoreProvider()) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
