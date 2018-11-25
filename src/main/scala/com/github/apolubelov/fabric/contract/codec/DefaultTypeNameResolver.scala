package com.github.apolubelov.fabric.contract.codec

/**
  * @author Alexey Polubelov
  */
object DefaultTypeNameResolver extends TypeNameResolver {

    override def resolveTypeByName(name: String): Class[_] = Class.forName(name)

    override def resolveNameByType(clazz: Class[_]): String = clazz.getCanonicalName
}
