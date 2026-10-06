package com.example.salim.core.model

enum class PacketType(val code: Byte) {
    HELLO(0x01),
    ANNOUNCE(0x02),
    MESSAGE(0x03),
    ACK(0x04),
    FRAGMENT(0x05),
    GROUP_KEY(0x06),
    SOS(0x07),
    LEAVE(0x08);

    companion object {
        fun fromCode(code: Byte): PacketType? = entries.find { it.code == code }
    }
}

object PacketFlags {
    const val ENCRYPTED: Byte = 0x01
    const val SIGNED: Byte = 0x02
    const val NEEDS_ACK: Byte = 0x04
    const val IS_FRAGMENT: Byte = 0x08
    const val BROADCAST: Byte = 0x10

    fun hasFlag(flags: Byte, flag: Byte): Boolean = (flags.toInt() and flag.toInt()) != 0
    fun setFlag(flags: Byte, flag: Byte): Byte = (flags.toInt() or flag.toInt()).toByte()
}
