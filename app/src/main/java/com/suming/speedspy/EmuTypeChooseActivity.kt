package com.suming.speedspy


import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.core.net.toUri


class EmuTypeChooseActivity: DialogFragment(){

    var areaLength = 0f

    companion object {


        fun newInstance(): EmuTypeChooseActivity =
            EmuTypeChooseActivity().apply {
                arguments = bundleOf(

                )
            }
    }


    override fun onStart() {
        super.onStart()
        dialog?.window?.setWindowAnimations(R.style.DialogSlideInOut)
        dialog?.window?.setDimAmount(0.5f)

        dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        dialog?.window?.statusBarColor = Color.TRANSPARENT
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.activity_working_emutypechoose, container, false)

    @SuppressLint("UseGetLayoutInflater", "InflateParams")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        //按钮：退出
        val buttonExit = view.findViewById<ImageButton>(R.id.buttonExit)
        buttonExit.setOnClickListener {
            dismiss()
        }
        //按钮：手动设置
        val buttonManualSet = view.findViewById<Button>(R.id.buttonManualSet)
        buttonManualSet.setOnClickListener {
            val dialog = Dialog(requireContext())
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.activity_working_inputvalue, null)
            dialog.setContentView(dialogView)
            val title: TextView = dialogView.findViewById(R.id.dialog_title)
            title.text = "填写计算区域长度"
            val titleDescription:TextView = dialogView.findViewById(R.id.dialog_description)
            titleDescription.text = ""
            val input: EditText = dialogView.findViewById(R.id.dialog_input)
            input.hint = "单位:米丨如果不填写,默认使用25.0米"
            val button: Button = dialogView.findViewById(R.id.dialog_button)
            val imm = requireContext().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            button.setOnClickListener {
                val userInput = input.text.toString()
                setValue(userInput)
                dialog.dismiss()
            }
            dialog.show()
            CoroutineScope(Dispatchers.Main).launch {
                delay(50)
                input.requestFocus()
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }

        }
        //按钮：跳转
        val buttonGoWeb = view.findViewById<Button>(R.id.buttonGoWeb)
        buttonGoWeb.setOnClickListener {
            val url = "https://china-emu.cn/Trains/ALL/"
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        }
        //按钮：点击空白区域退出
        val topArea = view.findViewById<View>(R.id.topArea)
        topArea.setOnClickListener {
            dismiss()
        }



    }//onCreate END


    //功能：显示通知(做成job形式以应对连续输入)
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

    private fun setValue(content:String){
        if (content.isEmpty()){
            notice("您似乎什么都没有填......",2000)
            return
        }
        val number = content.toFloat()
        if (number > 440){
            notice("设置失败：Bro的车比站台还长",2500)
            return
        }else if (number == 0f){
            notice("设置失败：Bro的车没有长度",2500)
            return
        }else{
            areaLength = number
            Log.d("EmuTypeChooseActivity", "setValue: $areaLength")
            notice("已将计算区域长度设为${areaLength}米",2000)
            val result = bundleOf("value" to areaLength)
            setFragmentResult("requestKey2", result)
            lifecycleScope.launch {
                delay(1000)
                dismiss()
            }
        }



    }

}