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
package de.fraunhofer.aisec.cpg.frontends.cpp

import de.fraunhofer.aisec.cpg.TranslationConfiguration
import de.fraunhofer.aisec.cpg.frontends.*
import de.fraunhofer.aisec.cpg.passes.scopes.ScopeManager

/** The C language. */
class CLanguage :
    Language<CXXLanguageFrontend>(),
    HasStructs,
    HasFunctionPointers,
    HasQualifier,
    HasElaboratedTypeSpecifier {
    // TODO: Shouldn't there also be the "h" ending?
    override val fileExtensions: List<String>
        get() = listOf("c", "h")
    override val namespaceDelimiter: String
        get() = "::"
    override val frontend: Class<CXXLanguageFrontend>
        get() = CXXLanguageFrontend::class.java
    override val qualifiers: List<String>
        get() = listOf("const", "volatile", "restrict", "atomic")
    override val elaboratedTypeSpecifier: List<String>
        get() = listOf("struct", "union", "enum")

    override fun newFrontend(
        config: TranslationConfiguration,
        scopeManager: ScopeManager
    ): CXXLanguageFrontend {
        return CXXLanguageFrontend(this, config, scopeManager)
    }
}
