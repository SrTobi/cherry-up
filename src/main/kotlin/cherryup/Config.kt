package cherryup

import java.util.prefs.Preferences

data class Config(val startDir: String, val branchFlow: String, val authorFilter: String) {
    companion object Factory {
        private const val startDirKey = "startDir"
        private const val branchFlowKey = "branchFlow"
        private const val authorFilterKey = "authorFilter"

        private val defaultProto = Config(System.getProperty("user.dir"), "main -> dev", "")

        private fun preferenceNode(): Preferences =
            Preferences.userRoot().node("cherryUp")

        fun save(config: Config, pref: Preferences = preferenceNode()) {
            pref.put(startDirKey, config.startDir)
            pref.put(branchFlowKey, config.branchFlow)
            pref.put(authorFilterKey, config.authorFilter)
        }

        fun load(pref: Preferences = preferenceNode()): Config = Config(
            startDir =     pref.get(startDirKey, defaultProto.startDir),
            branchFlow =   pref.get(branchFlowKey, defaultProto.branchFlow),
            authorFilter = pref.get(authorFilterKey, defaultProto.authorFilter)
        )
    }
}