package com.nivek.paperwork

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Portable, versioned library backup. PDFs and drafts are intentionally separate. */
object LibraryBackup {
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_ENTRIES = 10_000
    private const val MAX_POINTS = 100_000
    data class Library(val signatures: List<Signature>,val texts: List<SavedText>)

    fun write(output: OutputStream,signatures: List<Signature>,texts: List<SavedText>) {
        val json=JSONObject().put("format","paperwork-library").put("version",1)
            .put("signatures",JSONArray().also { a -> signatures.forEach { a.put(it.json()) } })
            .put("texts",JSONArray().also { a -> texts.forEach { a.put(it.json()) } })
        val bytes=json.toString().toByteArray(Charsets.UTF_8)
        if(bytes.size>MAX_BYTES) throw UserInputException(R.string.backup_too_large)
        // Ensure every exported archive also satisfies import validation.
        read(bytes.inputStream())
        output.write(bytes)
    }

    fun read(input: InputStream): Library {
        val bytes=input.readBytesLimited(MAX_BYTES)
        try {
            val json=JSONObject(bytes.toString(Charsets.UTF_8))
            require(json.getString("format")=="paperwork-library")
            if(json.getInt("version")!=1) throw UserInputException(R.string.backup_version)
            val signatures=json.getJSONArray("signatures")
            val texts=json.getJSONArray("texts")
            require(signatures.length()<=MAX_ENTRIES && texts.length()<=MAX_ENTRIES)
            var points=0
            val s=(0 until signatures.length()).map { i ->
                val j=signatures.getJSONObject(i)
                val ink=readInk(j.getJSONArray("strokes"))
                points+=ink.sumOf { it.size }
                require(points<=MAX_POINTS && ink.isNotEmpty() && ink.all { stroke -> stroke.isNotEmpty() && stroke.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f } })
                val aspect=j.getDouble("aspect").toFloat()
                require(aspect.isFinite() && aspect in .001f..1000f)
                Signature(j.getString("id"),j.getString("name"),ink,aspect).also { require(it.id.isNotBlank() && it.name.isNotBlank()) }
            }
            val t=(0 until texts.length()).map { SavedText.from(texts.getJSONObject(it)).also { entry -> require(entry.id.isNotBlank() && entry.label.isNotBlank() && entry.text.isNotBlank()) } }
            require(s.map { it.id }.distinct().size==s.size && t.map { it.id }.distinct().size==t.size)
            return Library(s,t)
        } catch(e: UserInputException) { throw e }
        catch(e: Exception) { throw UserInputException(R.string.invalid_backup) }
    }

    /** Preserve existing entries; identical contents are not inserted twice, even on retry. */
    fun merge(current: Library,incoming: Library): Library {
        val signatures=current.signatures.toMutableList()
        incoming.signatures.forEach { s ->
            if(signatures.none { it.copy(id=s.id)==s }) signatures.add(if(signatures.any { it.id==s.id }) s.copy(id=UUID.randomUUID().toString()) else s)
        }
        val texts=current.texts.toMutableList()
        incoming.texts.forEach { t ->
            if(texts.none { it.copy(id=t.id)==t }) texts.add(if(texts.any { it.id==t.id }) t.copy(id=UUID.randomUUID().toString()) else t)
        }
        return Library(signatures,texts)
    }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val output=java.io.ByteArrayOutputStream()
        val buffer=ByteArray(8192)
        while(true) {
            val n=read(buffer)
            if(n<0) break
            if(output.size()+n>limit) throw UserInputException(R.string.backup_too_large)
            output.write(buffer,0,n)
        }
        return output.toByteArray()
    }
}
