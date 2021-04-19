package cherryup

import java.nio.file.Path
import java.nio.file.Paths
import java.util.prefs.Preferences

data class Config(val startDir: String, val branchFlow: String) {
    companion object Factory {
        private val startDirKey = "startDir"
        private val branchFlowKey = "branchFlow"
        private val defaultProto = Config(System.getProperty("user.dir"), "main -> dev")

        fun default(): Config = defaultProto.copy()

        fun defaultConfigPath(): Path =
            Paths.get(System.getProperty("user.dir")).resolve("cherryUp.cfg")

        fun preferenceNode(): Preferences =
            Preferences.userRoot().node("cherryUp")

        fun save(config: Config, pref: Preferences = preferenceNode()) {
            pref.put(startDirKey, config.startDir)
            pref.put(branchFlowKey, config.branchFlow)
        }

        fun load(pref: Preferences = preferenceNode()): Config = Config(
            pref.get(startDirKey, defaultProto.startDir),
            pref.get(branchFlowKey, defaultProto.branchFlow)
        )
    }
}