package com.foleyit.itflow.ui.util

import java.security.SecureRandom

private val secureRandom = SecureRandom()

fun generatePassword(
    length: Int = 16,
    upper: Boolean = true,
    digits: Boolean = true,
    symbols: Boolean = true
): String {
    val lower = "abcdefghijkmnpqrstuvwxyz"
    val up    = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    val nums  = "23456789"
    val syms  = "!@#\$%^&*-_=+"

    var charset = lower
    if (upper) charset += up
    if (digits) charset += nums
    if (symbols) charset += syms

    return (1..length).map { charset[secureRandom.nextInt(charset.length)] }.joinToString("")
}
