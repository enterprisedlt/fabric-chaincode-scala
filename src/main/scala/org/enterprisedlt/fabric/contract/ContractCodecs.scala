package org.enterprisedlt.fabric.contract

import org.enterprisedlt.fabric.contract.codec.{GsonCodec, TextCodec, Utf8Codec}
import org.enterprisedlt.fabric.contract.codec.{BinaryCodec, GsonCodec, TextCodec}

/**
 * @author Alexey Polubelov
 */
class ContractCodecs(
    val parametersDecoder: TextCodec,
    val ledgerCodec: BinaryCodec,
    val resultEncoder: BinaryCodec,
    val transientDecoder: BinaryCodec
)

object ContractCodecs {
    def apply(): ContractCodecs = apply(GsonCodec())

    def apply(defaultTextCodec: TextCodec): ContractCodecs = {
        val binaryCodec = Utf8Codec(defaultTextCodec)
        apply(defaultTextCodec, binaryCodec, binaryCodec, binaryCodec)
    }

    def apply(parametersDecoder: TextCodec, ledgerCodec: BinaryCodec, resultEncoder: BinaryCodec, transientDecoder: BinaryCodec): ContractCodecs =
        new ContractCodecs(parametersDecoder, ledgerCodec, resultEncoder, transientDecoder)
}
