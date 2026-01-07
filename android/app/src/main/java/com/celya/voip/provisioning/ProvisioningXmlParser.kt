package com.celya.voip.provisioning

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

data class ProvisioningConfig(val entries: Map<String, String>) {
    fun get(key: String): String? = entries[key]
}

class ProvisioningXmlParser {
    fun parse(input: InputStream): ProvisioningConfig {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        val map = mutableMapOf<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "entry") {
                val key = parser.getAttributeValue(null, "name") ?: ""
                val value = readEntryValue(parser)
                map[key] = value
            }
            event = parser.next()
        }
        return ProvisioningConfig(map)
    }

    private fun readEntryValue(parser: XmlPullParser): String {
        var value = ""
        var event = parser.next()
        if (event == XmlPullParser.TEXT) {
            value = parser.text?.trim().orEmpty()
            event = parser.next()
        }
        while (event != XmlPullParser.END_TAG || parser.name != "entry") {
            event = parser.next()
        }
        return value
    }
}
