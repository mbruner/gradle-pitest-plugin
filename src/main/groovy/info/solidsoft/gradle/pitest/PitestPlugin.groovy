/* Copyright (c) 2012 Marcin Zajączkowski
 * All rights reserved.
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
 */
package info.solidsoft.gradle.pitest

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.UnionFileCollection
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.tasks.TaskInstantiationException

/**
 * The main class for Pitest plugin.
 */
class PitestPlugin implements Plugin<Project> {
    final static DEFAULT_PITEST_VERSION = '1.1.3'
    final static PITEST_TASK_GROUP = "Report"
    final static PITEST_TASK_NAME = "pitest"
    final static PITEST_CONFIGURATION_NAME = 'pitest'

    private final static Logger log =  Logging.getLogger(PitestPlugin)

    @VisibleForTesting
    final static String PIT_HISTORY_DEFAULT_FILE_NAME = 'pitHistory.txt'

    Project project
    PitestPluginExtension extension

    void apply(Project project) {
        this.project = project
        verifyThatRequiredPluginsWereAlreadyApplied()
        createConfigurations()
        createExtension(project)
        project.plugins.withType(JavaBasePlugin) {
            PitestTask task = project.tasks.create(PITEST_TASK_NAME, PitestTask)
            task.with {
                description = "Run PIT analysis for java classes"
                group = PITEST_TASK_GROUP
            }
            configureTaskDefault(task)
        }
    }

    private void verifyThatRequiredPluginsWereAlreadyApplied() {
        //MZA: Pitest currently works only with Java project and it is required to applied Java plugin first.
        //     This clarify the error message (is better than "missing sourceSets property")
        //Fully qualified name in required with new plugin mechanism:
        //http://forums.gradle.org/gradle/topics/project-plugins-hasplugin-java-returns-false-when-the-new-plugin-mechanism-is-used#reply_14943890
        if (!project.plugins.hasPlugin("java") && !project.plugins.hasPlugin("org.gradle.java")) {
            log.debug("Already applied plugins: {}", project.plugins)
            throw new TaskInstantiationException("Java plugin has to be applied before Pitest plugin")
        }
    }

    private void createConfigurations() {
        project.rootProject.buildscript.configurations.maybeCreate(PITEST_CONFIGURATION_NAME).with {
            visible = false
            description = "The Pitest libraries to be used for this project."
        }
    }

    //TODO: MZA: Maybe move it to the constructor of an extension class?
    private void createExtension(Project project) {
        extension = project.extensions.create("pitest", PitestPluginExtension)
        extension.reportDir = new File("${project.reporting.baseDir.path}/pitest")
        extension.pitestVersion = DEFAULT_PITEST_VERSION
        extension.testSourceSets = [project.sourceSets.test] as Set
        extension.mainSourceSets = [project.sourceSets.main] as Set
    }

    private void configureTaskDefault(PitestTask task) {
        task.conventionMapping.with {
            taskClasspath = {
                List<FileCollection> testRuntimeClasspath = extension.testSourceSets*.runtimeClasspath

                FileCollection combinedTaskClasspath = new UnionFileCollection(testRuntimeClasspath)
                FileCollection combinedTaskClasspathWithoutPomFiles = combinedTaskClasspath.filter { File file -> !file.name.endsWith(".pom") }
                combinedTaskClasspathWithoutPomFiles += project.rootProject.buildscript.configurations[PITEST_CONFIGURATION_NAME]
                combinedTaskClasspathWithoutPomFiles
            }
            mutableCodePaths = { extension.mainSourceSets*.output.classesDir.flatten() as Set }
            sourceDirs = { extension.mainSourceSets*.allSource.srcDirs.flatten() as Set }

            reportDir = { extension.reportDir }
            targetClasses = {
                log.debug("Setting targetClasses. project.getGroup: {}, class: {}", project.getGroup(), project.getGroup()?.class)
                if (extension.targetClasses) {
                    return extension.targetClasses
                }
                if (project.getGroup()) {   //Assuming it is always a String class instance
                    return [project.getGroup() + ".*"] as Set
                }
                null
            }
            targetTests = { extension.targetTests }
            dependencyDistance = { extension.dependencyDistance }
            threads = { extension.threads }
            mutateStaticInits = { extension.mutateStaticInits }
            includeJarFiles = { extension.includeJarFiles }
            mutators = { extension.mutators }
            excludedMethods = { extension.excludedMethods }
            excludedClasses = { extension.excludedClasses }
            avoidCallsTo = { extension.avoidCallsTo }
            verbose = { extension.verbose }
            timeoutFactor = { extension.timeoutFactor }
            timeoutConstInMillis = { extension.timeoutConstInMillis }
            maxMutationsPerClass = { extension.maxMutationsPerClass }
            childProcessJvmArgs = { extension.jvmArgs }
            outputFormats = { extension.outputFormats }
            failWhenNoMutations = { extension.failWhenNoMutations }
            includedGroups = { extension.includedGroups }
            excludedGroups = { extension.excludedGroups }
            configFile = { extension.configFile }
            detectInlinedCode = { extension.detectInlinedCode }
            timestampedReports = { extension.timestampedReports }
            historyInputLocation = { extension.historyInputLocation }
            historyOutputLocation = { extension.historyOutputLocation }
            enableDefaultIncrementalAnalysis = { extension.enableDefaultIncrementalAnalysis }
            defaultFileForHistoryDate = { new File(project.buildDir, PIT_HISTORY_DEFAULT_FILE_NAME) }
            mutationThreshold = { extension.mutationThreshold }
            mutationEngine = { extension.mutationEngine }
            coverageThreshold = { extension.coverageThreshold }
            exportLineCoverage = { extension.exportLineCoverage }
            jvmPath = { extension.jvmPath }
            mainProcessJvmArgs = { extension.mainProcessJvmArgs }
        }

        project.afterEvaluate {
            task.dependsOn(calculateTasksToDependOn())

            addPitDependencies()
        }
    }

    private Set<String> calculateTasksToDependOn() {
        Set<String> tasksToDependOn = extension.testSourceSets.collect { it.name + "Classes" }
        log.debug("pitest tasksToDependOn: $tasksToDependOn")
        tasksToDependOn
    }

    private void addPitDependencies() {
        project.rootProject.buildscript.dependencies {
            log.info("Using PIT: $extension.pitestVersion")
            pitest "org.pitest:pitest-command-line:$extension.pitestVersion"
        }
    }
}
