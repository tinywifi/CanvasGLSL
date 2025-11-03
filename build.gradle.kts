plugins {
    id("fabric-loom") version "1.11-SNAPSHOT" apply false
    java
}

subprojects {
    apply(plugin = "java")

    // Only apply fabric-loom to version modules, not common
    if (project.path.startsWith(":versions:")) {
        apply(plugin = "fabric-loom")

        base {
            archivesName = properties["archives_base_name"] as String
            version = properties["mod_version"] as String
            group = properties["maven_group"] as String
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            val imguiVersion = rootProject.properties["imgui_version"] as String

            // ImGui dependencies (common across all versions)
            "modImplementation"("io.github.spair:imgui-java-binding:$imguiVersion")?.let { "include"(it) }
            "modImplementation"("io.github.spair:imgui-java-lwjgl3:$imguiVersion")?.let { "include"(it) }
            "modImplementation"("io.github.spair:imgui-java-natives-windows:$imguiVersion")?.let { "include"(it) }
            "modImplementation"("io.github.spair:imgui-java-natives-linux:$imguiVersion")?.let { "include"(it) }
            "modImplementation"("io.github.spair:imgui-java-natives-macos:$imguiVersion")?.let { "include"(it) }

            // Media decoding (GIF / MP4 support)
            "modImplementation"("org.jcodec:jcodec:0.2.5")?.let { "include"(it) }
            "modImplementation"("org.jcodec:jcodec-javase:0.2.5")?.let { "include"(it) }
        }
    }

    tasks {
        withType<ProcessResources> {
            val propertyMap = mapOf(
                "version" to project.version,
                "mc_version" to project.property("minecraft_version"),
            )

            inputs.properties(propertyMap)
            filteringCharset = "UTF-8"

            filesMatching("fabric.mod.json") {
                expand(propertyMap)
            }
        }

        withType<Jar> {
            from(rootProject.file("LICENSE")) {
                rename { "${it}_${project.base.archivesName.get()}" }
            }
        }

        withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.release = 21
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
