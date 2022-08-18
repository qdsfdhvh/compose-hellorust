package com.seiko.transform.action.rust

import com.android.build.gradle.LibraryExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.getting
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMultiplatformPlugin

class RustKotlinPlugin : Plugin<Project> {
    override fun apply(target: Project) {
         with(target) {
            val cargoExtension = extensions.create("cargo", CargoExtension::class.java)
            afterEvaluate {
                if (plugins.hasPlugin(KotlinMultiplatformPlugin::class.java)) {
                    throw GradleException("Kotlin multiplatform plugin is not resolved.")
                }
                val kmpExtension = extensions.getByType(KotlinMultiplatformExtension::class.java)
                configurePlugin(
                    kmpExtension = kmpExtension,
                    cargoExtension = cargoExtension,
                )
            }
        }
    }

    private fun Project.configurePlugin(
        kmpExtension: KotlinMultiplatformExtension,
        cargoExtension: CargoExtension,
    ) {
        check(cargoExtension.module.isNotEmpty()) { "module cannot be empty" }
        check(cargoExtension.libName.isNotEmpty()) { "libName cannot be empty" }

        val toolchains = mutableSetOf<Toolchain>()
        kmpExtension.targets.forEach {
            println(it.name)
            when (it.name) {
                "android" -> {
                    val androidExtension = extensions.getByType(LibraryExtension::class.java)
                    val abiFilters = androidExtension.defaultConfig.ndk.abiFilters
                    if (abiFilters.isEmpty()) {
                        // if not config, support all android targets
                        abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
                    }
                    toolchains.add(
                        Toolchain(
                            type = ToolchainType.Android,
                            targets = abiFilters,
                        )
                    )
                }
                "jvm", "desktop" -> {
                    toolchains.add(
                        Toolchain(
                            type = ToolchainType.Jvm,
                            targets = setOf(
                                getCurrentOsTargetTriple(),
                                // "x86_64-apple-darwin",
                                // "aarch64-apple-darwin",
                                // "x86_64-pc-windows-msvc",
                                // "aarch64-pc-windows-msvc",
                                // "x86_64-unknown-linux-gnu",
                                // "aarch64-unknown-linux-gnu",
                            ),
                        )
                    )
                }
                "iosX64", "iosArm64" -> {

                }
                "macosX64", "macosArm64" -> {

                }
            }
        }

        val buildTask = project.tasks.maybeCreate(
            "cargoBuild",
            DefaultTask::class.java
        ).apply {
            group = RUST_TASK_GROUP
            description = "Build library (all targets)"
        }

        toolchains.forEach {
            val targetBuildTask = project.tasks.maybeCreate(
                "cargoBuild${it.type.name}",
                CargoBuildTask::class.java,
            ).apply {
                group = RUST_TASK_GROUP
                description = "Build library (${it.type.name})"
                toolchain = it
            }
            when (it.type) {
                ToolchainType.Android -> {
                    val javaPreCompileDebug by tasks.getting
                    javaPreCompileDebug.dependsOn(targetBuildTask)
                    val javaPreCompileRelease by tasks.getting
                    javaPreCompileRelease.dependsOn(targetBuildTask)
                }
                ToolchainType.Jvm -> {
                    val compileKotlinDesktop by tasks.getting
                    compileKotlinDesktop.dependsOn(targetBuildTask)
                }
                else -> Unit
            }
            buildTask.dependsOn(targetBuildTask)
        }
    }

    companion object {
        const val RUST_TASK_GROUP = "rust"
    }
}
