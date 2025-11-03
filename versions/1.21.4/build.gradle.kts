// Minecraft 1.21.4 specific module

version = "${rootProject.properties["mod_version"]}+mc1.21.4"

dependencies {
    // Minecraft and Fabric for 1.21.4
    minecraft("com.mojang:minecraft:1.21.4")
    mappings("net.fabricmc:yarn:1.21.4+build.1:v2")
    modImplementation("net.fabricmc:fabric-loader:0.16.9")

    // Include common module source
    implementation(project(":common"))
}

base {
    archivesName.set("${rootProject.properties["archives_base_name"]}-1.21.4")
}
