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
import de.fraunhofer.aisec.cpg.graph.declarations.TranslationUnitDeclaration
import de.fraunhofer.aisec.cpg.graph.declarations.VariableDeclaration
import de.fraunhofer.aisec.cpg.graph.statements.CompoundStatement
import de.fraunhofer.aisec.cpg.graph.statements.DeclarationStatement
import de.fraunhofer.aisec.cpg.graph.statements.ReturnStatement
import de.fraunhofer.aisec.cpg.graph.statements.expressions.BinaryOperator
import de.fraunhofer.aisec.cpg.graph.statements.expressions.Literal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DslTest {
    @Test
    fun test() {
        val tu = TranslationUnitDeclaration()

        tu.function("main") {
            body {
                declare { variable("a") { literal(1) } }
                returnStmt { literal(1) + literal(2) }
            }
        }

        // Let's assert that we did this correctly
        val main = tu.byNameOrNull<FunctionDeclaration>("main")
        assertNotNull(main)

        val body = main.body as? CompoundStatement
        assertNotNull(body)

        val declarationStatement = main.bodyOrNull<DeclarationStatement>(0)
        assertNotNull(declarationStatement)

        val variable = declarationStatement.singleDeclaration as? VariableDeclaration
        assertNotNull(variable)
        assertEquals("a", variable.name)

        var lit1 = variable.initializer as? Literal<*>
        assertNotNull(lit1)
        assertEquals(1, lit1.value)

        val returnStatement = main.bodyOrNull<ReturnStatement>(0)
        assertNotNull(returnStatement)

        val binOp = returnStatement.returnValue as? BinaryOperator
        assertNotNull(binOp)
        assertEquals("+", binOp.operatorCode)

        lit1 = binOp.lhs as? Literal<*>
        assertNotNull(lit1)
        assertEquals(1, lit1.value)

        val lit2 = binOp.rhs as? Literal<*>
        assertNotNull(lit2)
        assertEquals(2, lit2.value)
    }
}
