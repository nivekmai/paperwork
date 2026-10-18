package com.nivek.paperwork

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class BackupRulesTest {
    private fun rules(id: Int): List<Triple<String,String,String?>> {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val result=mutableListOf<Triple<String,String,String?>>()
        app.resources.getXml(id).use { xml ->
            var section=""
            while(xml.next()!=XmlPullParser.END_DOCUMENT) {
                if(xml.eventType!=XmlPullParser.START_TAG) continue
                if(xml.name in listOf("cloud-backup","device-transfer")) section=xml.name
                if(xml.name in listOf("include","exclude")) result.add(Triple(section+":"+xml.name,xml.getAttributeValue(null,"path"),xml.getAttributeValue(null,"requireFlags")))
            }
        }
        return result
    }
    @Test @Config(sdk=[34]) fun modernRulesAllowOnlyDirectTransfer() {
        val rules=rules(R.xml.data_extraction_rules)
        assertTrue(rules.none { it.first=="cloud-backup:include" })
        assertEquals(5,rules.count { it.first=="cloud-backup:exclude" && it.second=="." })
        assertEquals(setOf("signatures.json","saved-text.json","documents/"),rules.filter { it.first=="device-transfer:include" }.map { it.second }.toSet())
    }
    @Test @Config(sdk=[28]) fun legacyRulesRequireDeviceTransferForEveryIncludedFile() {
        val rules=rules(R.xml.backup_rules)
        assertEquals(3,rules.size)
        assertTrue(rules.all { it.first==":include" && it.third=="deviceToDeviceTransfer" })
    }
    @Test @Config(sdk=[26]) fun androidEightExcludesAllAutomaticBackupDomains() {
        val rules=rules(R.xml.backup_rules)
        assertEquals(5,rules.size)
        assertTrue(rules.all { it.first==":exclude" && it.second=="." })
    }
}
