package com.agroai.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncModelsTest {
    @Test fun `payload codec preserves supported Firestore values`() {
        val source = mapOf<String, Any?>(
            "name" to "North | field, 1",
            "count" to 3L,
            "area" to 2.5,
            "active" to true,
            "optional" to null,
            "items" to listOf("wheat", "mustard, local"),
        )
        assertEquals(source, CloudPayloadCodec.decode(CloudPayloadCodec.encode(source)))
    }

    @Test fun `account scope never invents a Firebase owner`() {
        val scope = AccountScope()
        assertEquals(LOCAL_OWNER, scope.current)
        scope.useAuthenticated("firebase-uid")
        assertEquals("firebase-uid", scope.current)
        scope.useLocal()
        assertEquals(LOCAL_OWNER, scope.current)
    }
}
