package com.agroai.app.data

import com.agroai.app.data.model.FarmerDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    @Test fun `normalizes valid Indian mobile numbers`() {
        assertEquals("+919876543210", Validation.normalizeIndianPhone("98765 43210"))
        assertEquals("+919876543210", Validation.normalizeIndianPhone("+91-98765-43210"))
    }

    @Test fun `rejects invalid mobile numbers`() {
        assertEquals(null, Validation.normalizeIndianPhone("1234567890"))
        assertEquals(null, Validation.normalizeIndianPhone("98765"))
    }

    @Test fun `profile validation covers all required groups`() {
        val errors = Validation.profile(FarmerDraft(phoneNumber = "+919876543210"))
        assertTrue(ValidationError.Name in errors)
        assertTrue(ValidationError.State in errors)
        assertTrue(ValidationError.Area in errors)
        assertTrue(ValidationError.Crops in errors)
        assertTrue(ValidationError.Goals in errors)
    }

    @Test fun `complete profile is valid and preserves bigha`() {
        val draft = FarmerDraft(
            name = "Kisan Lal", phoneNumber = "+919876543210", state = "Rajasthan", district = "Udaipur",
            area = "2.5", areaUnit = "bigha", soilType = "loamy", irrigationType = "seasonal",
            currentCrops = setOf("wheat"), goals = setOf("improve_yield"),
        )
        assertTrue(Validation.profile(draft).isEmpty())
        assertEquals("bigha", draft.toProfile("uid").areaUnit)
        assertEquals(2.5, draft.toProfile("uid").area, 0.0)
    }
}
