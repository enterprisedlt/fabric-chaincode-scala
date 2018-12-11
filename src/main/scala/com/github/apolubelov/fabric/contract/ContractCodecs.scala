package com.github.apolubelov.fabric.contract

import com.github.apolubelov.fabric.contract.codec.{BinaryCodec, GsonCodec, TextCodec, Utf8Codec}

/**
  * @author Alexey Polubelov
  */
class ContractCodecs(
    val parametersDecoder: TextCodec,
    val ledgerCodec: BinaryCodec,
    val resultEncoder: BinaryCodec
)

object ContractCodecs {
    def apply(): ContractCodecs = apply(GsonCodec())

    def apply(defaultTextCodec: TextCodec): ContractCodecs = {
        val binaryCodec = Utf8Codec(defaultTextCodec)
        apply(defaultTextCodec, binaryCodec, binaryCodec)
    }

    def apply(parametersDecoder: TextCodec, ledgerCodec: BinaryCodec, resultEncoder: BinaryCodec): ContractCodecs =
        new ContractCodecs(parametersDecoder, ledgerCodec, resultEncoder)
}
