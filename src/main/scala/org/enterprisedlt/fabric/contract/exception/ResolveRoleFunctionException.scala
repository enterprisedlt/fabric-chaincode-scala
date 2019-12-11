package org.enterprisedlt.fabric.contract.exception

import scala.util.control.NoStackTrace

/**
 * @author Andrew Pudovikov
 */
case class ResolveRoleFunctionException(messages: String) extends NoStackTrace {
    override def getMessage: String = {
        messages
    }
}