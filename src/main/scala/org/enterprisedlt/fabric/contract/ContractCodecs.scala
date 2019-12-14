package org.enterprisedlt.fabric.contract

import org.enterprisedlt.general.codecs.GsonCodec
import org.enterprisedlt.spec.BinaryCodec

/**
 * @author Alexey Polubelov
 */
class ContractCodecs(
    val parametersDecoder: BinaryCodec,
    val ledgerCodec: BinaryCodec,
    val resultEncoder: BinaryCodec,
    val transientDecoder: BinaryCodec
)

object ContractCodecs {
    def apply(): ContractCodecs = apply(GsonCodec())

    def apply(defaultCodec: BinaryCodec): ContractCodecs = {
        apply(defaultCodec, defaultCodec, defaultCodec, defaultCodec)
    }

    def apply(parametersDecoder: BinaryCodec, ledgerCodec: BinaryCodec, resultEncoder: BinaryCodec, transientDecoder: BinaryCodec): ContractCodecs =
        new ContractCodecs(parametersDecoder, ledgerCodec, resultEncoder, transientDecoder)
}
