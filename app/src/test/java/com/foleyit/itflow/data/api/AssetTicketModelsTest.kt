package com.foleyit.itflow.data.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AssetTicketModelsTest {
    private val gson = Gson()

    @Test fun createRequestSendsAssetIdNextToClientId() {
        val json = JsonParser.parseString(gson.toJson(CreateTicketRequest(subject = "Printer jam", clientId = 7, assetId = 42))).asJsonObject
        assertEquals(42, json["asset_id"].asInt)
        assertEquals(7, json["client_id"].asInt)
    }

    @Test fun createRequestOmitsAssetIdWhenNoAsset() {
        val json = JsonParser.parseString(gson.toJson(CreateTicketRequest(subject = "Printer jam", clientId = 7))).asJsonObject
        assertFalse(json.has("asset_id"))
    }

    @Test fun assetDetailDecodesClientId() {
        val a = gson.fromJson("""{"id":42,"name":"LT-001","client":"Acme","client_id":7}""", AssetDetail::class.java)
        assertEquals(7, a.clientId)
    }

    @Test fun oldServerWithoutClientIdDecodesAsNull() {
        val a = gson.fromJson("""{"id":42,"name":"LT-001","client":"Acme"}""", AssetDetail::class.java)
        assertNull(a.clientId)
    }
}
