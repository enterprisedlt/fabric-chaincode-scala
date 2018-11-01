package com.github.apolubelov.fabric.contract

/*
 * @author Alexey Polubelov
 */
trait Resolvable {
    def resolve[T](clz: Class[T]): T
}
