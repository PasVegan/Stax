package com.stax.core.domain

data class Concentration(val amount: Quantity, val per: Quantity) {
    override fun toString(): String = "$amount / $per"
}
