/*
 * Copyright (c) 2022, Fraunhofer AISEC. All rights reserved.
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
package de.fraunhofer.aisec.cpg.graph

import de.fraunhofer.aisec.cpg.graph.declarations.FunctionDeclaration
import de.fraunhofer.aisec.cpg.graph.declarations.VariableDeclaration
import de.fraunhofer.aisec.cpg.graph.statements.CompoundStatement
import de.fraunhofer.aisec.cpg.graph.statements.DeclarationStatement
import de.fraunhofer.aisec.cpg.graph.statements.ReturnStatement
import de.fraunhofer.aisec.cpg.graph.types.Type
import de.fraunhofer.aisec.cpg.graph.types.UnknownType

/** Creates a new node of type [T]. */
inline fun <reified T : Node> new(
    name: String = "",
    type: Type = UnknownType.getUnknownType(),
    noinline init: ((T) -> Unit)? = null
): T {
    val node = T::class::constructors.get().first().call()
    node.name = name

    init?.let { init(node) }

    if (node is HasType) {
        node.type = type
    }

    return node
}

/** Declares a new function with a [name]. */
fun DeclarationHolder.function(
    name: String = "",
    init: FunctionDeclaration.() -> Unit
): FunctionDeclaration {
    val node = new(name, init = init)

    // TODO(oxisto): Use scope manager instead?
    this += node

    return node
}

fun FunctionDeclaration.body(init: CompoundStatement.() -> Unit): CompoundStatement {
    val node = new(init = init)

    init(node)

    this.body = node

    return node
}

fun StatementHolder.returnStmt(init: ReturnStatement.() -> Unit): ReturnStatement {
    val node = new(init = init)

    init(node)

    this += node

    return node
}

/**
 * Declares a list of variables that can be specified in the [init] block. This returns a
 * [DeclarationStatement].
 */
fun StatementHolder.declare(init: DeclarationStatement.() -> Unit): DeclarationStatement {
    val node = new(init = init)

    this += node

    return node
}

fun DeclarationStatement.variable(
    name: String,
    init: (VariableDeclaration.() -> Unit)? = null
): VariableDeclaration {
    val node = new(name, init = init)

    this.addToPropertyEdgeDeclaration(node)

    return node
}
