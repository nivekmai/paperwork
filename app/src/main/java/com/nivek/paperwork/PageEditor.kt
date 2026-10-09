package com.nivek.paperwork

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import kotlin.math.*

/** A real Android text input laid directly over the PDF's selected text box. */
class PageEditor(context: Context): FrameLayout(context) {
    val canvas=PageCanvas(context)
    private var editing: Mark?=null
    var activeInput: EditText?=null; private set
    var onBeginText: (String)->Unit={}
    var onTextChange: (Mark)->Unit={}
    var onFinishText: ()->Unit={}
    var onEditingChanged: (Boolean)->Unit={}
    private var finishing=false
    init {
        clipChildren=true; clipToPadding=true
        addView(canvas,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))
        canvas.onEditText={startEditing(it)}
        canvas.onViewportChanged={placeInput()}
    }
    fun startEditing(mark: Mark) {
        if(mark.kind!="Text" || editing?.id==mark.id) return
        finishEditing()
        editing=mark; canvas.editingId=mark.id; onBeginText(mark.id)
        val input=object: EditText(context) {
            override fun onKeyPreIme(keyCode: Int,event: KeyEvent): Boolean {
                if(keyCode==KeyEvent.KEYCODE_BACK) { if(event.action==KeyEvent.ACTION_UP) finishEditing(); return true }
                return super.onKeyPreIme(keyCode,event)
            }
        }.apply {
            contentDescription="Edit text on PDF"
            hint="Type here"
            setPadding(0,0,0,0); includeFontPadding=false; gravity=Gravity.TOP or Gravity.START
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions=EditorInfo.IME_ACTION_DONE
            typeface=Typeface.create(mark.font,Typeface.NORMAL)
            setTextColor(mark.color); setLineSpacing(0f,mark.spacing)
            setText(mark.text); setSelection(text.length)
            background=GradientDrawable().apply { setColor(mark.background); setStroke(max(1,resources.displayMetrics.density.toInt()),0xff1a755c.toInt()) }
            setOnEditorActionListener { _,action,_ -> if(action==EditorInfo.IME_ACTION_DONE) { finishEditing(); true } else false }
            addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
                override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {
                    val old=editing ?: return
                    val changed=old.copy(text=s?.toString().orEmpty())
                    val required=MarkPainter.textLayout(changed).height.toFloat()+2f
                    val updated=changed.copy(height=max(old.height,required).coerceAtMost((canvas.page?.height ?: Float.MAX_VALUE)-old.y))
                    editing=updated; onTextChange(updated); placeInput()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        activeInput=input; addView(input,LayoutParams(1,1)); onEditingChanged(true)
        placeInput()
        input.requestFocus()
        input.post { if(activeInput===input) (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT) }
    }
    fun applyInlineStyle(mark: Mark): Boolean {
        val current=editing ?: return false
        val input=activeInput ?: return false
        if(mark.id!=current.id) return false
        val changed=mark.copy(text=input.text.toString())
        val required=MarkPainter.textLayout(changed).height.toFloat()+2f
        editing=changed.copy(height=max(changed.height,required).coerceAtMost((canvas.page?.height ?: Float.MAX_VALUE)-changed.y))
        input.setTextColor(changed.color); input.typeface=Typeface.create(changed.font,Typeface.NORMAL)
        input.setLineSpacing(0f,changed.spacing)
        onTextChange(editing!!); placeInput()
        return true
    }
    private fun placeInput() {
        val m=editing ?: return; val input=activeInput ?: return
        val bounds=canvas.screenBounds(m)
        val availableHeight=max(32*resources.displayMetrics.density,height-max(0f,bounds.top))
        val lp=input.layoutParams as LayoutParams
        val w=max(1,bounds.width().roundToInt()); val h=max(1,min(bounds.height(),availableHeight).roundToInt())
        val x=bounds.left.roundToInt(); val y=bounds.top.roundToInt()
        if(lp.width!=w || lp.height!=h || lp.leftMargin!=x || lp.topMargin!=y) {
            lp.width=w; lp.height=h; lp.leftMargin=x; lp.topMargin=y; input.layoutParams=lp
        }
        val size=m.size*canvas.pageScale()
        if(abs(input.textSize-size)>.01f) input.setTextSize(TypedValue.COMPLEX_UNIT_PX,size)
    }
    fun finishEditing() {
        if(editing==null || finishing) return
        finishing=true
        val input=activeInput
        editing=null; activeInput=null; canvas.editingId=null
        input?.let {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken,0)
            it.clearFocus(); removeView(it)
        }
        onFinishText(); onEditingChanged(false); finishing=false
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val input=activeInput
        if(event.actionMasked==MotionEvent.ACTION_DOWN && input!=null && (event.x<input.left || event.x>input.right || event.y<input.top || event.y>input.bottom)) finishEditing()
        return super.dispatchTouchEvent(event)
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(w>0 && h>0) post { editing?.let { canvas.ensureTextVisible(it); placeInput() } }
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if(hasWindowFocus) activeInput?.let { input ->
            input.requestFocus()
            input.post { if(activeInput===input) (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT) }
        }
    }
    override fun onDetachedFromWindow() { finishEditing(); super.onDetachedFromWindow() }
}
