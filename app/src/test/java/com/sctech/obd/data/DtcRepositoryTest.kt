package com.sctech.obd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File

/** Guards the hand-written Turkish DTC database against typos and broken entries. */
class DtcRepositoryTest {

    private val json = File("src/main/assets/dtc_tr.json").readText()

    @Test
    fun everyEntryIsComplete() {
        val repo = DtcRepository.parse(json)
        val raw = JSONObject(json).getJSONArray("codes")
        assertEquals("duplicate codes in dtc_tr.json", raw.length(), repo.size)
        assertTrue("MVP needs at least 150 explained codes, has ${repo.size}", repo.size >= 150)

        for (i in 0 until raw.length()) {
            val code = raw.getJSONObject(i).getString("code")
            assertTrue("bad code format: $code", Regex("[PCBU][0-9A-F]{4}").matches(code))
            val info = repo.find(code)
            assertNotNull(code, info)
            info!!
            assertTrue("$code title", info.title.isNotBlank())
            assertTrue("$code meaning", info.meaning.isNotBlank())
            assertTrue("$code causes", info.causes.isNotEmpty() && info.causes.all { it.isNotBlank() })
            assertTrue("$code actions", info.actions.isNotEmpty() && info.actions.all { it.isNotBlank() })
        }
    }

    @Test
    fun lookupIsCaseInsensitive() {
        val repo = DtcRepository.parse(json)
        assertNotNull(repo.find("p0420"))
    }
}
