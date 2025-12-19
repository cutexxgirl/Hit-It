import toni.blahaj.setup.modCompileOnly

plugins {
	id("toni.blahaj")
}

blahaj {
	config {
		// yarn()
		// versionedAccessWideners()
	}
	setup {
		txnilib("1.0.23")
		forgeConfig()
		conditionalMixin()

		deps.modImplementation("com.github.ben-manes.caffeine:caffeine:3.1.2")
		deps.modImplementation("toni.immersivemessages:${mod.loader}-${mod.mcVersion}:1.0.18") {
			isTransitive = false
		}

		modloaderRequired("immersivemessages")
		markRequiredCurseforge("immersive-messages-api")
		markRequiredModrinth("immersive-messages-api")

		caxton()
	}
}

fun BlahajSettings.caxton() {
	when (mod.projectName) {
		"1.21.1-fabric" -> {
			deps.modCompileOnly(modrinth("caxton", "0.6.0-alpha.2+1.21.1-FABRIC"))
			deps.modCompileOnly(deps.include("com.github.Chocohead:Fabric-ASM:v2.3") {
				exclude(group = "net.fabricmc", module = "fabric-loader")
			})
		}

		"1.21.1-neoforge" -> {
			deps.modImplementation(modrinth("caxton", "0.6.0-alpha.2+1.21.1-NEOFORGE"))
			deps.minecraftRuntimeLibraries("com.github.ben-manes.caffeine:caffeine:3.1.2")
		}

		"1.20.1-fabric" -> {
			deps.modImplementation(modrinth("caxton", "0.6.0-alpha.2.1+1.20.1-FABRIC"))
			deps.include(deps.implementation(deps.annotationProcessor("io.github.llamalad7:mixinextras-fabric:0.4.1")!!)!!)
			deps.modImplementation(deps.include("com.github.Chocohead:Fabric-ASM:v2.3") {
				exclude(group = "net.fabricmc", module = "fabric-loader")
			})
		}

		"1.20.1-forge" -> {
			deps.modImplementation(modrinth("caxton", "0.6.0-alpha.2.1+1.20.1-FORGE"))
			deps.minecraftRuntimeLibraries("com.github.ben-manes.caffeine:caffeine:3.1.2")

			deps.compileOnly(deps.annotationProcessor("io.github.llamalad7:mixinextras-common:0.4.1")!!)
			deps.implementation(deps.include("io.github.llamalad7:mixinextras-forge:0.4.1")!!)
		}
	}
}