package com.example.bingol.home

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bingol.R
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class TumorDetectFragment : Fragment() {

    private lateinit var previewImage: ImageView
    private lateinit var btnUpload: Button
    private lateinit var btnAnalyze: Button
    private lateinit var resultText: TextView
    private lateinit var resultImage: ImageView
    private lateinit var historyRecycler: RecyclerView
    private lateinit var historyAdapter: AnalysisHistoryAdapter

    private var selectedImageUri: Uri? = null
    private lateinit var interpreter: Interpreter
    private val analysisHistory = mutableListOf<AnalysisResult>()

    // Model bilgileri
    private val inputSize = 800
    private val labels = listOf("Glioma", "Meningioma", "No tumor", "Pituitary")
    private val confidenceThreshold = 0.25f
    private val objectnessThreshold = 0.5f

    data class AnalysisResult(
        val date: String,
        val time: String,
        val detectionCount: Int,
        val detections: List<Detection>,
        val resultBitmap: Bitmap? = null
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_tumor_detect, container, false)

        previewImage = view.findViewById(R.id.previewImage)
        btnUpload = view.findViewById(R.id.btnUpload)
        btnAnalyze = view.findViewById(R.id.btnAnalyze)
        resultText = view.findViewById(R.id.resultText)
        resultImage = view.findViewById(R.id.resultImage)
        historyRecycler = view.findViewById(R.id.historyRecycler)

        setupHistoryRecycler()
        loadModel()

        btnUpload.setOnClickListener { selectImage() }
        btnAnalyze.setOnClickListener {
            if (selectedImageUri != null) analyzeImage(selectedImageUri!!)
            else Toast.makeText(requireContext(), "Lütfen önce bir görüntü seçin.", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun setupHistoryRecycler() {
        historyAdapter = AnalysisHistoryAdapter(analysisHistory)
        historyRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    private fun loadModel() {
        try {
            val model = FileUtil.loadMappedFile(requireContext(), "best_float32.tflite")
            interpreter = Interpreter(model)
            Log.d("TFLite", "Model başarıyla yüklendi.")
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Model yüklenemedi: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("TFLiteError", "Model yükleme hatası", e)
        }
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            previewImage.setImageURI(selectedImageUri)
            previewImage.visibility = View.VISIBLE
            Log.d("TFLite", "Görüntü seçildi: $selectedImageUri")
        }
    }

    private fun analyzeImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return

            val input = preprocessImage(bitmap)
            val outputShape = interpreter.getOutputTensor(0).shape()

            val outputBuffer = when (outputShape.size) {
                3 -> Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
                2 -> Array(1) { Array(outputShape[0]) { FloatArray(outputShape[1]) } }
                else -> return
            }

            interpreter.run(input, outputBuffer)

            val processedOutput = when (outputShape.size) {
                3 -> outputBuffer[0]
                2 -> outputBuffer as Array<FloatArray>
                else -> return
            }

            val detections = parseYOLOv8OutputImproved(processedOutput, bitmap.width, bitmap.height)
            val filteredDetections = applyNMS(detections, 0.4f)

            // 🔴 Sadece ilk tespiti al
            val singleDetection = if (filteredDetections.isNotEmpty()) listOf(filteredDetections[0]) else emptyList()

            val resultBitmap = drawDetections(bitmap, singleDetection)
            resultImage.setImageBitmap(resultBitmap)
            resultImage.visibility = View.VISIBLE

            // Sonuç metnini güncelle
            val resultMessage = if (singleDetection.isEmpty()) {
                "Tespit edilen tümör yok."
            } else {
                val detection = singleDetection[0]
                buildString {
                    append("Tespit edilen tümör sayısı: 1\n\n")
                    append("Tür: ${detection.label}\n")
                    append("Güven: ${"%.1f".format(detection.confidence * 100)}%\n")
                }
            }
            resultText.text = resultMessage

            // Geçmişe ekle
            addToHistory(singleDetection, resultBitmap)

            view?.findViewById<androidx.cardview.widget.CardView>(R.id.resultCard)?.visibility = View.VISIBLE

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Analiz hatası: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("TFLiteError", "TensorFlow Lite analiz hatası", e)
            e.printStackTrace()
        }
    }

    private fun addToHistory(detections: List<Detection>, resultBitmap: Bitmap) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = Date()

        val analysisResult = AnalysisResult(
            date = dateFormat.format(currentTime),
            time = timeFormat.format(currentTime),
            detectionCount = if (detections.isNotEmpty()) 1 else 0,
            detections = detections,
            resultBitmap = resultBitmap
        )

        analysisHistory.add(0, analysisResult)
        historyAdapter.notifyItemInserted(0)
        historyRecycler.scrollToPosition(0)
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        resized.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        var pixelIndex = 0
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixelValue = intValues[pixelIndex++]
                val r = (pixelValue shr 16 and 0xFF) / 255f
                val g = (pixelValue shr 8 and 0xFF) / 255f
                val b = (pixelValue and 0xFF) / 255f
                byteBuffer.putFloat(r)
                byteBuffer.putFloat(g)
                byteBuffer.putFloat(b)
            }
        }
        return byteBuffer
    }

    data class Detection(val label: String, val confidence: Float, val rect: RectF)

    private fun parseYOLOv8OutputImproved(
        output: Array<FloatArray>,
        imgWidth: Int,
        imgHeight: Int
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numClasses = labels.size
        val numPredictions = output[0].size

        for (i in 0 until numPredictions) {
            val centerX = output[0][i]
            val centerY = output[1][i]
            val width = output[2][i]
            val height = output[3][i]

            if (width < 0.01f || height < 0.01f || width > 0.9f || height > 0.9f) continue
            if (centerX < 0.05f || centerX > 0.95f || centerY < 0.05f || centerY > 0.95f) continue

            val rawScores = FloatArray(numClasses) { classIdx -> output[4 + classIdx][i] }
            val maxClassIdx = rawScores.indices.maxByOrNull { rawScores[it] } ?: continue
            val maxRawScore = rawScores[maxClassIdx]
            val classConfidence = 1f / (1f + exp(-maxRawScore))

            if (maxClassIdx == 2) continue
            if (classConfidence < confidenceThreshold) continue

            val scaledX = centerX * imgWidth
            val scaledY = centerY * imgHeight
            val scaledW = width * imgWidth
            val scaledH = height * imgHeight

            val rect = RectF(
                max(0f, scaledX - scaledW / 2f),
                max(0f, scaledY - scaledH / 2f),
                min(imgWidth.toFloat(), scaledX + scaledW / 2f),
                min(imgHeight.toFloat(), scaledY + scaledH / 2f)
            )

            detections.add(Detection(labels[maxClassIdx], classConfidence, rect))
        }
        return detections
    }

    private fun applyNMS(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        val sortedDetections = detections.sortedByDescending { it.confidence }.toMutableList()
        val selectedDetections = mutableListOf<Detection>()
        while (sortedDetections.isNotEmpty()) {
            val bestDetection = sortedDetections.removeAt(0)
            selectedDetections.add(bestDetection)
            sortedDetections.removeAll {
                calculateIoU(bestDetection.rect, it.rect) > iouThreshold
            }
        }
        return selectedDetections
    }

    private fun calculateIoU(rect1: RectF, rect2: RectF): Float {
        val intersectionArea = max(0f, min(rect1.right, rect2.right) - max(rect1.left, rect2.left)) *
                max(0f, min(rect1.bottom, rect2.bottom) - max(rect1.top, rect2.top))
        val area1 = rect1.width() * rect1.height()
        val area2 = rect2.width() * rect2.height()
        val unionArea = area1 + area2 - intersectionArea
        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    private fun drawDetections(bitmap: Bitmap, detections: List<Detection>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val colors = arrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)

        for ((index, detection) in detections.withIndex()) {
            val color = colors[index % colors.size]
            val paintBox = Paint().apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = max(4f, min(bitmap.width, bitmap.height) * 0.01f)
            }
            val paintText = Paint().apply {
                this.color = Color.WHITE
                textSize = max(24f, min(bitmap.width, bitmap.height) * 0.04f)
                style = Paint.Style.FILL
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
            }
            val paintBackground = Paint().apply {
                this.color = Color.argb(200, Color.red(color), Color.green(color), Color.blue(color))
                style = Paint.Style.FILL
            }
            canvas.drawRect(detection.rect, paintBox)
            val text = "${detection.label} ${"%.0f".format(detection.confidence * 100)}%"
            val textBounds = Rect()
            paintText.getTextBounds(text, 0, text.length, textBounds)
            val padding = 8f
            val textRect = RectF(
                detection.rect.left,
                detection.rect.top - textBounds.height() - padding * 2,
                detection.rect.left + textBounds.width() + padding * 2,
                detection.rect.top
            )
            canvas.drawRect(textRect, paintBackground)
            canvas.drawText(text, detection.rect.left + padding, detection.rect.top - padding, paintText)
        }
        return mutableBitmap
    }

    inner class AnalysisHistoryAdapter(
        private val historyList: MutableList<AnalysisResult>
    ) : RecyclerView.Adapter<AnalysisHistoryAdapter.HistoryViewHolder>() {

        inner class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val dateText: TextView = view.findViewById(R.id.historyDate)
            val timeText: TextView = view.findViewById(R.id.historyTime)
            val countText: TextView = view.findViewById(R.id.historyCount)
            val detailsText: TextView = view.findViewById(R.id.historyDetails)
            val thumbnailImage: ImageView = view.findViewById(R.id.historyThumbnail)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_analysis_history, parent, false)
            return HistoryViewHolder(view)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val analysis = historyList[position]
            holder.dateText.text = analysis.date
            holder.timeText.text = analysis.time
            holder.countText.text = "Tespit: ${analysis.detectionCount} adet"

            if (analysis.detectionCount > 0) {
                val detection = analysis.detections[0]
                holder.detailsText.text =
                    "${detection.label} (${"%.0f".format(detection.confidence * 100)}%)"
            } else {
                holder.detailsText.text = "Tümör tespit edilmedi"
            }
            holder.detailsText.visibility = View.VISIBLE

            analysis.resultBitmap?.let { bitmap ->
                val thumbnail = Bitmap.createScaledBitmap(bitmap, 80, 80, true)
                holder.thumbnailImage.setImageBitmap(thumbnail)
                holder.thumbnailImage.visibility = View.VISIBLE
            } ?: run {
                holder.thumbnailImage.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                analysis.resultBitmap?.let { bitmap ->
                    resultImage.setImageBitmap(bitmap)
                    resultImage.visibility = View.VISIBLE
                    val detailText = if (analysis.detectionCount > 0) {
                        val detection = analysis.detections[0]
                        "Tespit edilen tümör sayısı: 1\n\n" +
                                "Tür: ${detection.label}\n" +
                                "Güven: ${"%.1f".format(detection.confidence * 100)}%"
                    } else {
                        "Tespit edilen tümör yok."
                    }
                    resultText.text = detailText
                    view?.findViewById<androidx.cardview.widget.CardView>(R.id.resultCard)?.visibility = View.VISIBLE
                }
            }
        }

        override fun getItemCount(): Int = historyList.size
    }
}
