/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.impl

import com.google.gson.GsonBuilder
import com.intellij.util.containers.ContainerUtil
import groovy.text.SimpleTemplateEngine
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.LibraryLicense
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
/**
 * @author nik
 */
@CompileStatic
class LibraryLicensesListGenerator {
  private final BuildMessages messages
  private Map<LibraryLicense, String> licensesInModules

  private LibraryLicensesListGenerator(BuildMessages messages,
                                       Map<LibraryLicense, String> licensesInModules) {
    this.messages = messages
    this.licensesInModules = licensesInModules
  }

  static LibraryLicensesListGenerator create(BuildMessages messages,
                                             JpsProject project,
                                             List<LibraryLicense> licensesList,
                                             Set<String> usedModulesNames) {
    Map<LibraryLicense, String> licences = generateLicenses(messages, project, licensesList, usedModulesNames)
    return new LibraryLicensesListGenerator(messages, licences)
  }

  private static Map<LibraryLicense, String> generateLicenses(BuildMessages messages,
                                                              JpsProject project,
                                                              List<LibraryLicense> licensesList,
                                                              Set<String> usedModulesNames) {
    Map<LibraryLicense, String> licenses = [:]
    messages.debug("Generating licenses table")
    messages.debug("Used modules: $usedModulesNames")
    Set<JpsModule> usedModules = project.modules.findAll { usedModulesNames.contains(it.name) } as Set<JpsModule>
    Map<String, String> usedLibraries = [:]
    usedModules.each { JpsModule module ->
      JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).getLibraries().each { item ->
        getLibraryNames(item).forEach { String name ->
          usedLibraries[name] = module.name
        }
      }
    }

    licensesList.findAll { it.license != LibraryLicense.JETBRAINS_OWN }.each { LibraryLicense lib ->
      if (usedModulesNames.contains(lib.attachedTo)) {
        licenses[lib] = lib.attachedTo
      }
      else {
        lib.libraryNames.each {
          String module = usedLibraries[it]
          if (module != null) {
            licenses[lib] = module
          }
        }
      }
    }
    return licenses
  }

  static List<String> getLibraryNames(JpsLibrary lib) {
    def name = lib.name
    if (name.startsWith("#")) {
      return ContainerUtil.map(lib.getFiles(JpsOrderRootType.COMPILED), {f->f.getName()})
    }
    return Collections.singletonList(name)
  }

  void generateHtml(String filePath) {
    messages.debug("Used libraries:")
    List<String> lines = []

    String line = '''
  <tr valign="top">
    <td class="firstColumn">
      $name
      <span class="version">$libVersion</span>
    </td>
    <td class="secondColumn">
      $license
    </td>
  </tr>
      '''.trim()
    def engine = new SimpleTemplateEngine()

    licensesInModules.entrySet().each {
      LibraryLicense lib = it.key
      String moduleName = it.value

      String libKey = (lib.name + "_" + lib.version ?: "").replace(" ", "_")
      // id here is needed because of a bug IDEA-188262
      String name = lib.url != null ? "<a id=\"${libKey}_lib_url\" class=\"name\" href=\"$lib.url\">$lib.name</a>" :
                    "<span class=\"name\">$lib.name</span>"
      String license = lib.libraryLicenseUrl != null ?
                       "<a id=\"${libKey}_license_url\" class=\"licence\" href=\"$lib.libraryLicenseUrl\">$lib.license</a>" :
                       "<span class=\"licence\">$lib.license</span>"

      messages.debug(" $lib.name (in module $moduleName)")
      lines << engine.createTemplate(line).make(["name": name, "libVersion": lib.version ?: "", "license": license]).toString()
    }

    lines.sort(true, String.CASE_INSENSITIVE_ORDER)
    File file = new File(filePath)
    file.parentFile.mkdirs()
    FileWriter out = new FileWriter(file)
    try {
      out.println('''
<style>
  table {
    width: 560px;
  }
  
  th {
    border:0pt;
    text-align: left;
  }
  
  td {
    padding-bottom: 11px;
  }
  
  .firstColumn {
    width: 410px;
    padding-left: 16px;
    padding-right: 50px;
  }
  
  .secondColumn {
    width: 150px;
    padding-right: 28px;
  }
  
  .name {
    color: #4a78c2;
    margin-right: 5px;
  }
    
  .version {
    color: #888888;
    line-height: 1.5em;
    white-space: nowrap;
  }
  
  .licence {
    color: #779dbd;
  }
</style>
'''.trim())
      out.println("<table>")
      out.println("<tr><th class=\"firstColumn\">Software</th><th class=\"secondColumn\">License</th></tr>")
      lines.each {
        out.println(it)
      }
      out.println("</table>")
    }
    finally {
      out.close()
    }
  }

  void generateJson(String filePath) {
    List<LibraryLicenseData> entries = []

    licensesInModules.keySet().sort( {it.name} ).each {
      entries.add(
        new LibraryLicenseData(
          name: it.name,
          url: it.url,
          version: it.version,
          license: it.license,
          licenseUrl: it.libraryLicenseUrl
        )
      )
    }

    File file = new File(filePath)
    file.parentFile.mkdirs()
    file.withWriter {
      new GsonBuilder().setPrettyPrinting().create().toJson(entries, it)
    }
  }
}

