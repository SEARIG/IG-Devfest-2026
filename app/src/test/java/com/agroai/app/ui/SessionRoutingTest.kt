package com.agroai.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRoutingTest {
    @Test fun `missing Firebase or signed out opens welcome`() {
        assertEquals(Destination.Welcome, sessionDestination(false, false, null))
        assertEquals(Destination.Welcome, sessionDestination(true, false, null))
    }

    @Test fun `authenticated incomplete farmer resumes onboarding`() {
        assertEquals(Destination.Onboarding, sessionDestination(true, true, false))
        assertEquals(Destination.Onboarding, sessionDestination(true, true, null))
    }

    @Test fun `authenticated complete farmer opens dashboard`() {
        assertEquals(Destination.Home, sessionDestination(true, true, true))
    }
}
