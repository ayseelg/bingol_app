package com.example.bingol.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bingol.R
import com.example.bingol.api.ChatRequest
import com.example.bingol.api.RetrofitClient
import kotlinx.coroutines.launch

class TumorAboutFragment : Fragment() {

    private lateinit var chatBox: LinearLayout
    private lateinit var questionInput: EditText
    private lateinit var sendBtn: ImageButton
    private var selectedModel: String = "gemini"

    companion object {
        private const val TAG = "TumorAboutFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tumor_about, container, false)

        chatBox = view.findViewById(R.id.chatBox)
        questionInput = view.findViewById(R.id.questionInput)
        sendBtn = view.findViewById(R.id.sendBtn)

        val btnGemini: Button = view.findViewById(R.id.btnGemini)
        val btnGroq: Button = view.findViewById(R.id.btnGroq)
        val btnCohere: Button = view.findViewById(R.id.btnCohere)
        val activeModelText: TextView = view.findViewById(R.id.activeModelText)

        btnGemini.setOnClickListener {
            selectedModel = "gemini"
            activeModelText.text = "Aktif Model: Gemini Pro"
        }
        btnGroq.setOnClickListener {
            selectedModel = "groq"
            activeModelText.text = "Aktif Model: Groq Cloud"
        }
        btnCohere.setOnClickListener {
            selectedModel = "cohere"
            activeModelText.text = "Aktif Model: Cohere AI"
        }

        sendBtn.setOnClickListener {
            val question = questionInput.text.toString().trim()
            if (question.isNotEmpty()) {
                addMessageToChat("Sen: $question")
                questionInput.text.clear()
                sendQuestionToServer(question)
            }
        }

        return view
    }

    private fun addMessageToChat(message: String) {
        Log.d(TAG, "Chat mesajı ekleniyor: $message")
        val textView = TextView(requireContext())
        textView.text = message
        textView.setPadding(8, 8, 8, 8)
        chatBox.addView(textView)

        val scrollView: ScrollView = view?.findViewById(R.id.chatScroll)!!
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun sendQuestionToServer(question: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.chatApi.sendQuestion(ChatRequest(question, selectedModel))
                if (response.isSuccessful) {
                    val answer = response.body()?.answer ?: "Cevap alınamadı."
                    addMessageToChat("AI: $answer")
                } else {
                    addMessageToChat("Hata: ${response.code()}")
                }
            } catch (e: Exception) {
                addMessageToChat("Bağlantı hatası: ${e.message}")
            }
        }
    }
}
