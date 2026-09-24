package com.example.videotool

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var downloadButton: Button
    private lateinit var extractButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private var downloadId: Long = -1
    private var downloadedFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        downloadButton = findViewById(R.id.downloadButton)
        extractButton = findViewById(R.id.extractButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        extractButton.isEnabled = false
        progressBar.visibility = View.GONE

        downloadButton.setOnClickListener { startDownload() }
        extractButton.setOnClickListener { extractAudioFromVideo() }

        registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    // الخطوة 1: تحميل الفيديو من رابط مباشر باستخدام DownloadManager الجاهز في أندرويد
    private fun startDownload() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "من فضلك أدخل رابط الفيديو", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "video_${System.currentTimeMillis()}.mp4"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("تحميل الفيديو")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_MOVIES, fileName)
            .setAllowedOverMetered(true)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        downloadedFilePath = getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.path + "/" + fileName

        progressBar.visibility = View.VISIBLE
        statusText.text = "جاري التحميل..."
        downloadButton.isEnabled = false
    }

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                progressBar.visibility = View.GONE
                statusText.text = "اكتمل التحميل ✅"
                downloadButton.isEnabled = true
                extractButton.isEnabled = true
            }
        }
    }

    // الخطوة 2: تحويل الفيديو إلى ملف موسيقى (استخراج مسار الصوت) بدون إعادة ترميز
    // بيستخدم MediaExtractor + MediaMuxer المدمجين في أندرويد - مفيش حاجة نزودها
    private fun extractAudioFromVideo() {
        val inputPath = downloadedFilePath ?: return
        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            Toast.makeText(this, "الملف غير موجود", Toast.LENGTH_SHORT).show()
            return
        }

        val outputPath = inputFile.parent + "/" + inputFile.nameWithoutExtension + "_music.m4a"
        statusText.text = "جاري استخراج الموسيقى..."
        extractButton.isEnabled = false

        Thread {
            try {
                val extractor = MediaExtractor()
                extractor.setDataSource(inputPath)

                var audioTrackIndex = -1
                var audioFormat: MediaFormat? = null

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        audioFormat = format
                        break
                    }
                }

                if (audioTrackIndex == -1 || audioFormat == null) {
                    runOnUiThread {
                        statusText.text = "الفيديو ده مفيهوش مسار صوت"
                        extractButton.isEnabled = true
                    }
                    return@Thread
                }

                extractor.selectTrack(audioTrackIndex)

                val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val muxerAudioTrack = muxer.addTrack(audioFormat)
                muxer.start()

                val bufferSize = 1024 * 1024
                val buffer = ByteBuffer.allocate(bufferSize)
                val bufferInfo = MediaCodec.BufferInfo()

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                    extractor.advance()
                }

                muxer.stop()
                muxer.release()
                extractor.release()

                runOnUiThread {
                    statusText.text = "تم استخراج الموسيقى ✅\n$outputPath"
                    extractButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "حصل خطأ: ${e.message}"
                    extractButton.isEnabled = true
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onDownloadComplete)
    }
}
