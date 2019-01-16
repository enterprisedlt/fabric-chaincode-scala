package com.github.apolubelov.fabric.contract.store

import org.hyperledger.fabric.shim.ledger.CompositeKey

/**
  * @author Alexey Polubelov
  */
case class Key(
    parts: String*
)

object Key {
    private[this] val _empty = Key()

    def empty: Key = _empty

    implicit def StringToKey: String => Key = s => Key(s)
}