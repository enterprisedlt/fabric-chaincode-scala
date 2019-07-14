package org.enterprisedlt.fabric.contract.store

/**
  * @author Alexey Polubelov
  */
case class KeyValue[+T](
    key: String, //TODO: Key
    value: T
)