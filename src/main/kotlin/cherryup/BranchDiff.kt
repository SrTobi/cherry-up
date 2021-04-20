package cherryup

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit

object BranchDiff {
    private data class CommitFingerPrint(val time: Long, val msg: String)

    private fun RevCommit.toFingerPrint(): CommitFingerPrint =
        CommitFingerPrint(this.authorIdent.`when`.time, this.shortMessage)

    private fun RevCommit.isMergeCommit(): Boolean =
        this.parentCount > 1

    fun diff(git: Git, from: Ref, to: Ref): List<RevCommit> {
        val fromLog = git.log().add(from.objectId).call()
        val toLog = git.log().add(to.objectId).call()

        val isInTo = toLog.map { it.toFingerPrint() }.toSet()
        val inFromButNotInTo = fromLog.filterNot { isInTo.contains(it.toFingerPrint()) }
        return inFromButNotInTo.filterNot { it.isMergeCommit() }
    }
}