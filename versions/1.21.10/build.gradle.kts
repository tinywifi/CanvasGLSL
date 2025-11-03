// Minecraft 1.21.10 specific module (current working version)

version = "${rootProject.properties["mod_version"]}+mc1.21.10"

dependencies {
    // Minecraft and Fabric for 1.21.10
    minecraft("com.mojang:minecraft:1.21.10")
    mappings("net.fabricmc:yarn:1.21.10+build.2:v2")
    modImplementation("net.fabricmc:fabric-loader:0.17.3")

    // Include common module source
    implementation(project(":common"))
}

base {
    archivesName.set("${rootProject.properties["archives_base_name"]}-1.21.10")
}
