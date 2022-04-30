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
package de.fraunhofer.aisec.cpg.passes

import de.fraunhofer.aisec.cpg.InferenceConfiguration
import de.fraunhofer.aisec.cpg.TranslationConfiguration
import de.fraunhofer.aisec.cpg.frontends.LanguageFrontend
import de.fraunhofer.aisec.cpg.graph.Node
import de.fraunhofer.aisec.cpg.graph.NodeBuilder.newFieldDeclaration
import de.fraunhofer.aisec.cpg.graph.NodeBuilder.newFunctionDeclaration
import de.fraunhofer.aisec.cpg.graph.NodeBuilder.newMethodDeclaration
import de.fraunhofer.aisec.cpg.graph.NodeBuilder.newMethodParameterIn
import de.fraunhofer.aisec.cpg.graph.NodeBuilder.newRecordDeclaration
import de.fraunhofer.aisec.cpg.graph.TypeManager
import de.fraunhofer.aisec.cpg.graph.declarations.*
import de.fraunhofer.aisec.cpg.graph.statements.expressions.CallExpression
import de.fraunhofer.aisec.cpg.graph.statements.expressions.DeclaredReferenceExpression
import de.fraunhofer.aisec.cpg.graph.statements.expressions.MemberCallExpression
import de.fraunhofer.aisec.cpg.graph.statements.expressions.MemberExpression
import de.fraunhofer.aisec.cpg.graph.types.ObjectType
import de.fraunhofer.aisec.cpg.passes.scopes.ValueDeclarationScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * This class does all the inference magic. Since we are a fuzzy parser, we make certain assumptions
 * on the source code. One of the assumptions is that if we discover that we cannot resolve a
 * reference, it does not necessarily mean that there was a programming error, but rather that we
 * did not "see" parts of the program that contains the declaration. Therefore, we try to "infer"
 * what the original declaration was. Since this might not be desired by the user, e.g., if the user
 * wants to check for errors rather than follow our assumption, one can use the
 * [InferenceConfiguration] class using the
 * [TranslationConfiguration.Builder.inferenceConfiguration] builder to fine-tune or disable
 * inference.
 *
 * To quickly identify nodes that were inferred, the property [Node.isInferred] is set to true.
 * Note, that this is fundamentally different from nodes that are "implicit" (see [Node.isImplicit]
 * ), which are also not part the original AST, but are implicitly there. This is mostly referred to
 * as syntactic sugar, e.g., one can usually omit a `this` when calling methods of the current
 * class.
 */
class Inference(var lang: LanguageFrontend) {

    var log: Logger = LoggerFactory.getLogger(Inference::class.java)

    /**
     * Infers a [RecordDeclaration] out of an [ObjectType] that could not be resolved to its
     * declaration. This function will check, if [InferenceConfiguration.inferRecords] is set to
     * true, otherwise null will be returned.
     *
     * The [source] node contains the node that was originally responsible for the symbol lookup.
     * This could for example be a [MemberExpression], if we discover an access to a field of a
     * non-resolved class.
     */
    fun inferRecordDeclaration(
        type: ObjectType,
        source: MemberExpression,
        tu: TranslationUnitDeclaration
    ): RecordDeclaration? {
        // Check, if we are configured to infer records
        if (!lang.config.inferenceConfiguration.inferRecords) {
            return null
        }

        log.debug(
            "Encountered an unknown record type {}. We are going to infer that record",
            type.typeName
        )

        // We start out with a simple struct. We can later adjust the kind when we discover a member
        // call expression
        val declaration = newRecordDeclaration(type.typeName, "struct", "")
        declaration.isInferred = true

        // Update the type
        TypeManager.getInstance()
            .firstOrderTypes
            .filter { it is ObjectType && it == type }
            .map { it as ObjectType }
            .forEach { it.recordDeclaration = declaration }

        // Try to find the variable that this member expression is referring to
        // TODO: We probably also need to refresh the type when it is used somewhere else as well.
        // The type system
        //  doesn't really help here
        val variableDeclaration =
            (source.base as? DeclaredReferenceExpression)?.refersTo as? VariableDeclaration
        (variableDeclaration?.type as? ObjectType)?.recordDeclaration = declaration
        variableDeclaration?.refreshType()

        // We are adding record declarations to the global scope. We need to update the scope
        // manager to include the record on the global scope. and update the scope of the
        // declaration. As a special case for the global scope, we need to "reset" the global scope
        // to our current translation unit, otherwise the associated AST node will be null and we
        // cannot add the record to anything.
        // TODO: Actually, we should place it in the "nearest" NameScope from the original
        //  node, because otherwise we run into problems if the original node was in a namespace.
        val scope = lang.scopeManager.globalScope
        lang.scopeManager.resetToGlobal(tu)
        lang.scopeManager.inject(scope, declaration)
        lang.scopeManager.enterScope(declaration)
        lang.scopeManager.leaveScope(declaration)

        return declaration
    }

    /**
     * Infers either a [FunctionDeclaration] or a [MethodDeclaration] from a [CallExpression] which
     * could not be resolved. The [source] node contains the node that was originally responsible
     * for the symbol lookup. This is most likely a [MemberExpression] for a [MemberCallExpression]
     * and a [DeclaredReferenceExpression] or a regular [CallExpression].
     */
    fun inferFunctionDeclaration(
        call: CallExpression,
        source: DeclaredReferenceExpression
    ): FunctionDeclaration? {
        val (scope, func) =
            if (source is MemberExpression) {
                val record = source.record
                if (record != null) {
                    val func =
                        newMethodDeclaration(call.name, null, false, source.record, lang = lang)
                    func.isInferred = true

                    // Inject our inferred node at the scope that belongs to the record declaration
                    val scope = lang.scopeManager.lookupScope(record) as? ValueDeclarationScope
                    Pair(scope, func)
                } else {
                    log.error(
                        "Cannot to infer a method from a member expression, whose record declaration is not known."
                    )
                    return null
                }
            } else {
                val func = newFunctionDeclaration(call.name, null, lang = lang)

                // We can only inject the function on the global scope
                val scope = lang.scopeManager.globalScope
                Pair(scope, func)
            }

        lang.scopeManager.inject(scope, func)

        lang.scopeManager.withScope(func) {
            // Construct inferred function parameters according to the call's signature
            for ((i, type) in call.signature.withIndex()) {
                val param = newMethodParameterIn("${type.name}$i", type, false)
                param.isInferred = true

                lang.scopeManager.addDeclaration(param)
            }
        }

        return func
    }

    /** Infers a [FieldDeclaration] out of a member expression in [source]. */
    fun inferFieldDeclaration(
        source: MemberExpression,
        tu: TranslationUnitDeclaration
    ): FieldDeclaration? {
        val record = source.record
        return if (record != null) {
            val field = newFieldDeclaration(source.name)
            field.isInferred = true

            // Inject our inferred node at the scope that belongs to the record declaration
            val scope = lang.scopeManager.lookupScope(record) as? ValueDeclarationScope
            lang.scopeManager.inject(scope, field)

            field
        } else {
            log.error(
                "Cannot to infer a field from a member expression, whose record declaration is not known."
            )
            null
        }
    }
}
