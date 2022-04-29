/*
 * Copyright (c) 2020, Fraunhofer AISEC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *                    $$$$$$\  $$$$$$$\   $$$$$$\
 *                   $$  __$$\ $$  __$$\ $$  __$$\
 *                   $$ /  \__|$$ |  $$ |$$ /  \__|
 *                   $$ |      $$$$$$$  |$$ |$$$$\
 *                   $$ |      $$  ____/ $$ |\_$$ |
 *                   $$ |  $$\ $$ |      $$ |  $$ |
 *                   \$$$$$   |$$ |      \$$$$$   |
 *                    \______/ \__|       \______/
 *
 */
package de.fraunhofer.aisec.cpg.graph.statements.expressions

import de.fraunhofer.aisec.cpg.graph.*
import de.fraunhofer.aisec.cpg.graph.types.Type
import de.fraunhofer.aisec.cpg.graph.types.TypeParser
import org.apache.commons.lang3.builder.ToStringBuilder
import org.neo4j.ogm.annotation.Transient

/**
 * A binary operation expression, such as "a + b". It consists of a left hand expression (lhs), a
 * right hand expression (rhs) and an operatorCode.
 */
class BinaryOperator : Expression(), HasType.TypeListener, ArgumentHolder {
    /** The left hand expression. */
    @field:SubGraph("AST")
    var lhs: Expression? = null
        set(value) {
            if (field != null) {
                disconnectOldLhs()
            }
            field = value
            value?.let { connectNewLhs(it) }
        }

    /** The right hand expression. */
    @field:SubGraph("AST")
    var rhs: Expression? = null
        set(value) {
            if (field != null) {
                disconnectOldRhs()
            }
            field = value
            value?.let { connectNewRhs(it) }
        }

    /** The operator code. */
    var operatorCode: String? = null

    /** Required for compound BinaryOperators. This should not be stored in the graph */
    @Transient
    private val compoundOperators =
        listOf("*=", "/=", "%=", "+=", "-=", "<<=", ">>=", "&=", "^=", "|=")

    fun <T : Expression?> getLhsAs(clazz: Class<T>): T? {
        return if (clazz.isInstance(lhs)) clazz.cast(lhs) else null
    }

    private fun connectNewLhs(lhs: Expression) {
        lhs.registerTypeListener(this)
        if ("=" == operatorCode) {
            if (lhs is DeclaredReferenceExpression) {
                // declared reference expr is the left hand side of an assignment -> writing to the
                // var
                lhs.access = AccessValues.WRITE
            } else if (lhs is MemberExpression) {
                lhs.access = AccessValues.WRITE
            }
            if (lhs is HasType.TypeListener) {
                registerTypeListener(lhs as HasType.TypeListener)
                registerTypeListener(this.lhs as HasType.TypeListener?)
            }
            if (rhs != null) {
                lhs.addPrevDFG(rhs!!)
            }
        } else if (compoundOperators.contains(operatorCode)) {
            if (lhs is DeclaredReferenceExpression) {
                // declared reference expr is the left hand side of an assignment -> writing to the
                // var
                lhs.access = AccessValues.READWRITE
            } else if (lhs is MemberExpression) {
                lhs.access = AccessValues.READWRITE
            }
            if (lhs is HasType.TypeListener) {
                registerTypeListener(lhs as HasType.TypeListener)
                registerTypeListener(this.lhs as HasType.TypeListener?)
            }
            addPrevDFG(lhs)
            addNextDFG(lhs)
        } else {
            addPrevDFG(lhs)
        }
    }

    private fun disconnectOldLhs() {
        lhs!!.unregisterTypeListener(this)
        if ("=" == operatorCode) {
            if (lhs is HasType.TypeListener) {
                unregisterTypeListener(lhs as HasType.TypeListener?)
            }
            if (rhs != null) {
                lhs!!.removePrevDFG(lhs)
            }
            if (lhs is HasType.TypeListener) {
                unregisterTypeListener(lhs as HasType.TypeListener?)
            }
        } else if (compoundOperators.contains(operatorCode)) {
            removePrevDFG(lhs)
            removeNextDFG(lhs)
        } else {
            removePrevDFG(lhs)
        }
    }

    fun <T : Expression?> getRhsAs(clazz: Class<T>): T? {
        return if (clazz.isInstance(rhs)) clazz.cast(rhs) else null
    }

    private fun connectNewRhs(rhs: Expression) {
        rhs.registerTypeListener(this)
        if ("=" == operatorCode) {
            if (rhs is HasType.TypeListener) {
                registerTypeListener(rhs as HasType.TypeListener)
            }
            if (lhs != null) {
                lhs!!.addPrevDFG(rhs)
            }
        }
        addPrevDFG(
            rhs
        ) // in C++ we can have a + (b = 1) so the rhs has to connected to the BinOp in all
        // cases
    }

    private fun disconnectOldRhs() {
        rhs!!.unregisterTypeListener(this)
        if ("=" == operatorCode) {
            if (rhs is HasType.TypeListener) {
                unregisterTypeListener(rhs as HasType.TypeListener?)
            }
            if (lhs != null) {
                lhs!!.removePrevDFG(rhs)
            }
        }
        removePrevDFG(
            rhs
        ) // in C++ we can have a + (b = 1) so the rhs has to connected to the BinOp in all
        // cases
    }

    override fun typeChanged(src: HasType, root: Collection<HasType>, oldType: Type) {
        if (!TypeManager.isTypeSystemActive()) {
            return
        }
        val previous = type
        if (operatorCode == "=") {
            setType(src.propagationType, root)
        } else if (lhs != null && "java.lang.String" == lhs!!.getType().toString() ||
                rhs != null && "java.lang.String" == rhs!!.getType().toString()
        ) {
            // String + any other type results in a String
            possibleSubTypes.clear()
            setType(TypeParser.createFrom("java.lang.String", true), root)
        }
        if (previous != type) {
            type.typeOrigin = Type.Origin.DATAFLOW
        }
    }

    override fun possibleSubTypesChanged(
        src: HasType,
        root: Collection<HasType>,
        oldSubTypes: Set<Type>
    ) {
        if (!TypeManager.isTypeSystemActive()) {
            return
        }
        val subTypes: MutableSet<Type> = HashSet(possibleSubTypes)
        subTypes.addAll(src.possibleSubTypes)
        setPossibleSubTypes(subTypes, root)
    }

    override fun toString(): String {
        return ToStringBuilder(this, TO_STRING_STYLE)
            .append("lhs", if (lhs == null) "null" else lhs!!.name)
            .append("rhs", if (rhs == null) "null" else rhs!!.name)
            .append("operatorCode", operatorCode)
            .toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is BinaryOperator) {
            return false
        }
        return (super.equals(other) &&
            lhs == other.lhs &&
            rhs == other.rhs &&
            operatorCode == other.operatorCode)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override fun addArgument(expression: Expression) {
        if (lhs != null) {
            lhs = expression
        } else {
            rhs = expression
        }
    }
}
