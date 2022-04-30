/*
 * Copyright (c) 2019, Fraunhofer AISEC. All rights reserved.
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
package de.fraunhofer.aisec.cpg.passes.scopes

import de.fraunhofer.aisec.cpg.graph.Node
import de.fraunhofer.aisec.cpg.graph.declarations.Declaration
import de.fraunhofer.aisec.cpg.graph.statements.LabelStatement
import java.util.HashMap

/**
 * Represent semantic scopes in the language and only saves information, such as relevant
 * statements. Pre and Postprocessing is done by the passes. Only the passes themselves know the
 * semantics of used edges, but different passes can use the same scope stack concept.
 */
abstract class Scope(
    /**
     * This property represents the AST node in the graph which defines the scope, for example a
     * [FunctionDeclaration] for a [FunctionScope].
     */
    var astNode: Node? = null
) {
    // FQN Name currently valid
    var scopedName: String? = null

    /** The parent scope. */
    var parent: Scope? = null

    /** The list of scope children. */
    var children: MutableList<Scope> = mutableListOf()

    // TODO(oxisto): Remove? Why are they here?
    var labelStatements: MutableMap<String, LabelStatement> = HashMap()

    // TODO(oxisto): Remove? Why are they here?
    fun addLabelStatement(labelStatement: LabelStatement) {
        labelStatements[labelStatement.label] = labelStatement
    }

    /**
     * Resolves a symbol by its name in this current scope (and only in this scope). Since some
     * languages allow overloading of symbols, such as functions, a list of [Declaration]s is
     * returned.
     */
    abstract fun resolveSymbol(name: String): List<Declaration>
}
