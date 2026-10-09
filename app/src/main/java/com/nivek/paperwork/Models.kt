package com.nivek.paperwork

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class InkPoint(val x: Float, val y: Float)
data class Mark(
    val id: String = UUID.randomUUID().toString(), val page: Int = 0,
    val kind: String = "Text", val x: Float = 40f, val y: Float = 60f,
    val width: Float = 220f, val height: Float = 60f,
    val text: String = "", val font: String = "sans-serif", val size: Float = 16f,
    val color: Int = 0xff172c27.toInt(), val background: Int = 0,
    val spacing: Float = 1.2f, val strokes: List<List<InkPoint>> = emptyList(),
    val strokeWidth: Float = (size / 8f).coerceIn(1f, 8f),
    val strokeStyle: String = "Solid", val fill: Int = 0,
    val lineStart: InkPoint = InkPoint(.05f,.5f), val lineEnd: InkPoint = InkPoint(.95f,.5f),
    val startArrow: String = "None", val endArrow: String = "None",
    val cornerRadius: Float = 0f
) {
    val isShape: Boolean get() = kind in listOf("Circle", "Square", "Oval", "Check", "X", "Line")
    val isClosedShape: Boolean get() = kind in listOf("Circle", "Square", "Oval")
    fun json() = JSONObject().put("id", id).put("page", page).put("kind", kind)
        .put("x", x).put("y", y).put("width", width).put("height", height)
        .put("text", text).put("font", font).put("size", size).put("color", color)
        .put("background", background).put("spacing", spacing).put("strokes", inkJson(strokes))
        .put("strokeWidth", strokeWidth).put("strokeStyle", strokeStyle).put("fill", fill)
        .put("lineStartX",lineStart.x).put("lineStartY",lineStart.y).put("lineEndX",lineEnd.x).put("lineEndY",lineEnd.y)
        .put("startArrow",startArrow).put("endArrow",endArrow).put("cornerRadius",cornerRadius)
    companion object {
        fun from(j: JSONObject) = Mark(j.getString("id"), j.getInt("page"), j.getString("kind"),
            j.getDouble("x").toFloat(), j.getDouble("y").toFloat(), j.getDouble("width").toFloat(),
            j.getDouble("height").toFloat(), j.optString("text"), j.optString("font", "sans-serif"),
            j.getDouble("size").toFloat(), j.getInt("color"), j.getInt("background"),
            j.getDouble("spacing").toFloat(), readInk(j.getJSONArray("strokes")),
            j.optDouble("strokeWidth", (j.getDouble("size") / 8).coerceIn(1.0, 8.0)).toFloat(),
            j.optString("strokeStyle", "Solid"),
            j.optInt("fill", if (j.getString("kind") in listOf("Circle", "Square", "Oval")) j.getInt("background") else 0),
            InkPoint(j.optDouble("lineStartX",.05).toFloat(),j.optDouble("lineStartY",.5).toFloat()),
            InkPoint(j.optDouble("lineEndX",.95).toFloat(),j.optDouble("lineEndY",.5).toFloat()),
            j.optString("startArrow","None"),j.optString("endArrow","None"),j.optDouble("cornerRadius",0.0).toFloat())
    }
}
fun inkJson(strokes: List<List<InkPoint>>) = JSONArray().also { a -> strokes.forEach { s ->
    a.put(JSONArray().also { b -> s.forEach { b.put(JSONArray().put(it.x).put(it.y)) } })
} }
fun readInk(a: JSONArray): List<List<InkPoint>> = (0 until a.length()).map { i ->
    val s = a.getJSONArray(i)
    (0 until s.length()).map { n -> val p = s.getJSONArray(n); InkPoint(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()) }
}
data class Signature(val id: String, val name: String, val strokes: List<List<InkPoint>>, val aspect: Float) {
    fun json() = JSONObject().put("id", id).put("name", name).put("strokes", inkJson(strokes)).put("aspect", aspect)
}
data class Draft(val id: String, val name: String, val pageCount: Int, val marks: List<Mark> = emptyList()) {
    fun json() = JSONObject().put("id", id).put("name", name).put("pageCount", pageCount)
        .put("marks", JSONArray().also { a -> marks.forEach { a.put(it.json()) } })
    companion object {
        fun from(j: JSONObject): Draft {
            val a = j.getJSONArray("marks")
            return Draft(j.getString("id"), j.getString("name"), j.getInt("pageCount"), (0 until a.length()).map { Mark.from(a.getJSONObject(it)) })
        }
    }
}
