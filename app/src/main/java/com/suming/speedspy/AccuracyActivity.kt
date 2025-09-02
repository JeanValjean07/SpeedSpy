package com.suming.speedspy

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class AccuracyActivity: DialogFragment(){

    companion object {
        private const val ARG_VIDEO_URI = "videoUri"
        private const val ARG_MARKING1_VALUE = "marking1Value"
        private const val ARG_MARKING2_VALUE = "marking2Value"

        fun newInstance(videoUri: Uri, marking1Value: Long, marking2Value: Long): AccuracyActivity =
            AccuracyActivity().apply {
                arguments = bundleOf(
                    ARG_VIDEO_URI to videoUri,
                    ARG_MARKING1_VALUE to marking1Value,
                    ARG_MARKING2_VALUE to marking2Value
                )
            }
    }

    var gap2Value = 0f
    var gap1Value = 0f
    var calculated = false


    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setWindowAnimations(R.style.DialogSlideInOut)
            setDimAmount(0.5f)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            setStatusBarColor(Color.TRANSPARENT)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.activity_working_accuracy, container, false)

    @SuppressLint("UseGetLayoutInflater", "InflateParams")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val videoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_VIDEO_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable(ARG_VIDEO_URI)
        } ?: return
        val bmp1 = loadFrameFromInternal("${videoUri.hashCode()}_flag1")
        val bmp2 = loadFrameFromInternal("${videoUri.hashCode()}_flag2")

        val photo1 = view.findViewById<PhotoView>(R.id.photoView1)
        photo1.setImageBitmap(bmp1)
        val photo2 = view.findViewById<PhotoView>(R.id.photoView2)
        photo2.setImageBitmap(bmp2)

        photo1.setOnMatrixChangeListener { _ ->
            val matrix = Matrix()
            photo1.getDisplayMatrix(matrix)
            photo2.setDisplayMatrix(matrix)
        }
        val textView1 = view.findViewById<TextView>(R.id.textView1)
        val textView2 = view.findViewById<TextView>(R.id.textView2)
        val marking1Value = arguments?.getLong(ARG_MARKING1_VALUE) ?: return
        val marking2Value = arguments?.getLong(ARG_MARKING2_VALUE) ?: return
        textView1.text = formatTime3(marking1Value)
        textView2.text = formatTime3(marking2Value)
        var returnValue = 0L
        if (marking1Value ==marking2Value){
            returnValue = marking1Value
        }

        //按钮：退出
        val buttonExit = view.findViewById<ImageButton>(R.id.buttonExit)
        buttonExit.setOnClickListener {
            dismiss()
        }
        //按钮：点击空白区域退出
        val topArea = view.findViewById<View>(R.id.topArea)
        topArea.setOnClickListener {
            dismiss()
        }
        //按钮：立即计算
        val buttonCalculate = view.findViewById<Button>(R.id.buttonCalculate)
        buttonCalculate.setOnClickListener {
            if (marking1Value == marking2Value){
                returnValue = marking1Value
                notice("两帧距离相同,点击\"应用此值\"直接设置",2000)
                calculated = true
                val finalValue = view.findViewById<TextView>(R.id.finalValue)
                finalValue.text = formatTime3(returnValue)
            } else if (gap1Value == 0f || gap2Value == 0f){
                notice("请先填完正向距离和反向距离",2000)
                return@setOnClickListener
            } else {
                if(marking1Value > marking2Value){
                    val ratio: Float = gap2Value/(gap1Value+gap2Value)
                    val gapValue = marking1Value - marking2Value
                    val offsetValue = gapValue * ratio
                    val accurateValue = (marking2Value + offsetValue).toLong()
                    returnValue = accurateValue
                    calculated = true
                } else {
                    val ratio: Float = gap1Value/(gap1Value+gap2Value)
                    val gapValue = marking2Value - marking1Value
                    val offsetValue = gapValue * ratio
                    val accurateValue = (marking1Value + offsetValue).toLong()
                    returnValue = accurateValue
                    calculated = true
                }
                val finalValue = view.findViewById<TextView>(R.id.finalValue)
                finalValue.text = formatTime3(returnValue)
            }
        }
        //按钮：填写正向距离
        val buttonSubmit1 = view.findViewById<Button>(R.id.buttonSubmit1)
        buttonSubmit1.setOnClickListener {
            val dialog = Dialog(requireContext())
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.activity_working_inputvalue, null)
            dialog.setContentView(dialogView)
            val title: TextView = dialogView.findViewById(R.id.dialog_title)
            title.text = "填写正向距离"
            val titleDescription:TextView = dialogView.findViewById(R.id.dialog_description)
            titleDescription.text = ""
            val input: EditText = dialogView.findViewById(R.id.dialog_input)
            val button: Button = dialogView.findViewById(R.id.dialog_button)
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            button.setOnClickListener {
                val userInput = input.text.toString()
                setValue(userInput,1)
                dialog.dismiss()
            }
            dialog.show()
            CoroutineScope(Dispatchers.Main).launch {
                delay(50)
                input.requestFocus()
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        //按钮：填写反向距离
        val buttonSubmit2 = view.findViewById<Button>(R.id.buttonSubmit2)
        buttonSubmit2.setOnClickListener {
            val dialog = Dialog(requireContext())
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.activity_working_inputvalue, null)
            dialog.setContentView(dialogView)
            val title: TextView = dialogView.findViewById(R.id.dialog_title)
            title.text = "填写反向距离"
            val titleDescription:TextView = dialogView.findViewById(R.id.dialog_description)
            titleDescription.text = ""
            val input: EditText = dialogView.findViewById(R.id.dialog_input)
            val button: Button = dialogView.findViewById(R.id.dialog_button)
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            button.setOnClickListener {
                val userInput = input.text.toString()
                setValue(userInput,2)
                dialog.dismiss()
            }
            dialog.show()
            CoroutineScope(Dispatchers.Main).launch {
                delay(50)
                input.requestFocus()
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        //按钮：使用此值
        val buttonUseThisValue = view.findViewById<Button>(R.id.buttonUseThisValue)
        buttonUseThisValue.setOnClickListener {
            if (!calculated){
                notice("请先计算最终值,然后才能应用该值",2000)
                return@setOnClickListener
            }
            val result = bundleOf("value" to returnValue)
            setFragmentResult("requestKey", result)
            dismiss()
        }

    }//onCreate END


    //功能：显示通知
    private fun notice(text: String, duration: Long) {
        showNoticeJob(text, duration)
    }
    private var showNoticeJob: Job? = null
    private fun showNoticeJob(text: String, duration: Long) {
        showNoticeJob?.cancel()
        showNoticeJob = lifecycleScope.launch {
            val notice = view?.findViewById<TextView>(R.id.notice)
            val noticeCard = view?.findViewById<CardView>(R.id.noticeCard)
            noticeCard?.visibility = View.VISIBLE
            notice?.text = text
            delay(duration)
            noticeCard?.visibility = View.GONE
        }
    }
    //功能：设置值
    private fun setValue(content:String,position:Int){
        if (content.isEmpty()){
            notice("您似乎什么都没有填......",2000)
            return
        }
        val number = content.toFloat()
        if (number == 1145f){
            notice("设置失败：车的长度不能是恶臭数字",2500)
            return
        }
        else if (number > 440){
            notice("设置失败：Bro的车似乎有点过长了",2500)
            return
        }else if (number == 0f){
            notice("设置失败：Bro的车没有长度",2500)
            return
        }else{
            calculated = false
            if (position == 1){
                val gap1 = view?.findViewById<TextView>(R.id.gap1)
                gap1?.text = number.toString()
                gap1Value = number
                notice("正向距离已设置",2000)
            }else{
                val gap2 = view?.findViewById<TextView>(R.id.gap2)
                gap2?.text = number.toString()
                gap2Value = number
                notice("反向距离已设置",2000)
            }
        }



    }
    //取图
    private fun loadFrameFromInternal(name: String): Bitmap? {
        val context = context ?: return null
        val file = File(context.filesDir, "$name.jpg")
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            null
        }
    }
    //时间格式化
    private fun formatTime3(raw: Long): String {
        val cent  = raw % 1000
        val totalSec = raw / 1000
        val min  = totalSec / 60
        val sec  = totalSec % 60
        return "%01d分%02d秒%03d毫秒".format(min, sec, cent)
    }
}