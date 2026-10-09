package com.nivek.paperwork

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.*

object MarkPainter {
    fun textLayout(m: Mark): StaticLayout {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=m.color; textSize=m.size; typeface=Typeface.create(m.font,Typeface.NORMAL) }
        return StaticLayout.Builder.obtain(m.text,0,m.text.length,tp,m.width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(0f,m.spacing).build()
    }
    fun draw(canvas: Canvas, m: Mark) {
        canvas.save(); canvas.translate(m.x,m.y)
        if(m.kind=="Line") { drawLine(canvas,m); canvas.restore(); return }
        if(m.kind!="Doodle") canvas.clipRect(0f,0f,m.width,m.height)
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        if(!m.isShape && m.background!=0) { p.color=m.background; canvas.drawRect(0f,0f,m.width,m.height,p) }
        if(m.kind=="Text") { textLayout(m).draw(canvas); canvas.restore(); return }
        p.color=m.color; p.strokeWidth=m.strokeWidth.coerceAtLeast(.1f)
        p.strokeCap=Paint.Cap.ROUND; p.strokeJoin=Paint.Join.ROUND
        p.style=Paint.Style.STROKE
        val inset=minOf(p.strokeWidth/2,m.width/2,m.height/2)
        val path=Path()
        when(m.kind) {
            "Circle", "Oval" -> path.addOval(inset,inset,m.width-inset,m.height-inset,Path.Direction.CW)
            "Square" -> {
                val radius=m.cornerRadius.coerceIn(0f,(min(m.width,m.height)/2-inset).coerceAtLeast(0f))
                path.addRoundRect(inset,inset,m.width-inset,m.height-inset,radius,radius,Path.Direction.CW)
            }
            "Check" -> { path.moveTo(m.width*.12f,m.height*.5f); path.lineTo(m.width*.4f,m.height*.8f); path.lineTo(m.width*.9f,m.height*.15f) }
            "X" -> { path.moveTo(inset,inset); path.lineTo(m.width-inset,m.height-inset); path.moveTo(m.width-inset,inset); path.lineTo(inset,m.height-inset) }
            "Signature", "Doodle" -> m.strokes.forEach { s -> if(s.isNotEmpty()) { path.moveTo(s[0].x*m.width,s[0].y*m.height); s.drop(1).forEach { path.lineTo(it.x*m.width,it.y*m.height) } } }
        }
        if(m.isClosedShape && m.fill!=0) {
            p.style=Paint.Style.FILL; p.color=m.fill; canvas.drawPath(path,p)
            p.style=Paint.Style.STROKE; p.color=m.color
        }
        p.pathEffect=when(m.strokeStyle) {
            "Dashed" -> DashPathEffect(floatArrayOf(p.strokeWidth*4,p.strokeWidth*2.5f),0f)
            "Dotted" -> DashPathEffect(floatArrayOf(.01f,p.strokeWidth*2.5f),0f)
            else -> null
        }
        if(m.color!=0) {
            canvas.drawPath(path,p)
            if(m.kind=="Doodle") m.strokes.filter { it.size==1 }.forEach { s ->
                p.style=Paint.Style.FILL; canvas.drawCircle(s[0].x*m.width,s[0].y*m.height,m.strokeWidth/2,p)
            }
        }
        canvas.restore()
    }
    private fun drawLine(canvas: Canvas,m: Mark) {
        if(m.color==0) return
        val a=InkPoint(m.lineStart.x*m.width,m.lineStart.y*m.height)
        val b=InkPoint(m.lineEnd.x*m.width,m.lineEnd.y*m.height)
        val length=hypot(b.x-a.x,b.y-a.y)
        if(length<.001f) return
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=m.color; strokeWidth=m.strokeWidth; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND; style=Paint.Style.STROKE
            pathEffect=when(m.strokeStyle) {
                "Dashed" -> DashPathEffect(floatArrayOf(m.strokeWidth*4,m.strokeWidth*2.5f),0f)
                "Dotted" -> DashPathEffect(floatArrayOf(.01f,m.strokeWidth*2.5f),0f)
                else -> null
            }
        }
        val headLength=min(max(8f,m.strokeWidth*4),length*.4f)
        fun shaftInset(head: String)=when(head) {
            "Triangle", "Circle", "Diamond" -> headLength*.8f
            "Open" -> min(m.strokeWidth/2,headLength*.5f)
            else -> 0f
        }
        val ux=(b.x-a.x)/length; val uy=(b.y-a.y)/length
        val startInset=shaftInset(m.startArrow); val endInset=shaftInset(m.endArrow)
        // Keep the round shaft cap inside the head instead of protruding beyond its tip.
        canvas.drawLine(a.x+ux*startInset,a.y+uy*startInset,b.x-ux*endInset,b.y-uy*endInset,p)
        p.pathEffect=null
        val angle=Math.toDegrees(atan2((b.y-a.y).toDouble(),(b.x-a.x).toDouble())).toFloat()
        drawArrowhead(canvas,p,a,angle+180,m.startArrow,headLength)
        drawArrowhead(canvas,p,b,angle,m.endArrow,headLength)
    }
    private fun drawArrowhead(canvas: Canvas,p: Paint,tip: InkPoint,angle: Float,kind: String,length: Float) {
        if(kind=="None") return
        canvas.save(); canvas.translate(tip.x,tip.y); canvas.rotate(angle)
        val half=length*.5f
        p.style=Paint.Style.STROKE
        when(kind) {
            "Open" -> canvas.drawPath(Path().apply { moveTo(-length,-half); lineTo(0f,0f); lineTo(-length,half) },p)
            "Triangle" -> {
                p.style=Paint.Style.FILL
                canvas.drawPath(Path().apply { moveTo(0f,0f); lineTo(-length,-half); lineTo(-length,half); close() },p)
            }
            "Circle" -> { p.style=Paint.Style.FILL; canvas.drawCircle(-half,0f,half,p) }
            "Diamond" -> {
                p.style=Paint.Style.FILL
                canvas.drawPath(Path().apply { moveTo(0f,0f); lineTo(-half,-half); lineTo(-length,0f); lineTo(-half,half); close() },p)
            }
            "Bar" -> canvas.drawLine(0f,-half,0f,half,p)
        }
        canvas.restore()
    }

}
