package com.foleyit.itflow.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarInitialTest {
    @Test fun usesFirstLetterUppercased() = assertEquals("E", avatarInitial("engineering"))
    @Test fun skipsLeadingWhitespace() = assertEquals("F", avatarInitial("  finance"))
    @Test fun emptyNameDoesNotCrash() = assertEquals("?", avatarInitial(""))
    @Test fun blankNameDoesNotCrash() = assertEquals("?", avatarInitial("   "))
    @Test fun nullNameDoesNotCrash() = assertEquals("?", avatarInitial(null))
}
