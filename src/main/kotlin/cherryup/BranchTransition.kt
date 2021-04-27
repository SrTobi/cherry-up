package cherryup

typealias BranchFlow = List<BranchTransition>

data class BranchTransition(val from: String, val to: String) {
    companion object {
        fun parseFlow(flow: String): BranchFlow =
            flow.split(',')
                .flatMap { part ->
                    part.split("->")
                        .map { it.trim() }
                        .windowed(2)
                        .map { BranchTransition(it[0], it[1]) }
                }
    }
}