package cherryup

import cherryup.ui.Step
import cherryup.ui.StepProgress
import cherryup.ui.UiModel
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.swing.DefaultListModel
import javax.swing.ListModel
import kotlin.collections.ArrayList

class CherryUpProcess(val ultimate: Git, val community: Git, val branchFlow: List<BranchFlow>): UiModel(), AutoCloseable {
    private val sections = ArrayList<Section>()
    private var curListModel: ListModel<Step> = DefaultListModel()

    init {
        val ultimateStash = StashInfo()
        val communityStash = StashInfo()
        sections.add(StashSection(ultimate, ultimateStash))
        sections.add(StashSection(community, communityStash))

        sections.add(UnStashSection(ultimate, ultimateStash))
        sections.add(UnStashSection(community, communityStash))

        updateSteps()
    }

    private fun updateSteps() {
        val newModel = DefaultListModel<Step>()
        newModel.addAll(sections.flatMap { it.createSteps() })
        curListModel = newModel
        update()
    }

    override fun proceed() {
        for (section in sections) {
            if (!section.proceed()) {
                return
            }
            updateSteps()
        }
    }

    override fun stop() {
        for (section in sections) {
            if (!section.stop()) {
                return
            }
            updateSteps()
        }
    }

    override fun close() {
        ultimate.close()
        community.close()
    }

    override fun listModel(): ListModel<Step> = curListModel

    companion object Factory {
        fun create(path: String, branchFlow: List<BranchFlow>): CherryUpProcess {
            val gitPath = Paths.get(path)
            return CherryUpProcess(openGit(gitPath), openGit(gitPath.resolve("community")), branchFlow)
        }

        private fun openGit(path: Path): Git {
            return Git(FileRepositoryBuilder()
                .setGitDir(path.resolve(".git").toFile())
                .findGitDir()
                .setMustExist(true)
                .build())
        }
    }

    private fun repoName(repo: Git): String =
        if (repo == ultimate) "Ultimate" else "Community"

    private class StashInfo(var hadStash: Optional<RevCommit>? = null)

    private interface Section {
        fun createSteps(): List<Step>
        fun proceed(): Boolean
        fun stop(): Boolean
    }

    private inner class StashSection(val repo: Git, val stashInfo: StashInfo): Section, Step {
        private var progress = StepProgress.None

        override fun createSteps(): List<Step> = listOf(this)

        override fun proceed(): Boolean =
            when (progress) {
                StepProgress.Done, StepProgress.Stopped -> true
                StepProgress.None, StepProgress.Processing -> {
                    progress = StepProgress.Processing
                    update()
                    val needsStashing = !repo.status().call().isClean
                    stashInfo.hadStash = if (needsStashing) {
                        Optional.of(
                            repo.stashCreate()
                            .setIncludeUntracked(true)
                            .setWorkingDirectoryMessage("CherryUp: Stash current WIP on {0}: {1} {2}")
                            .call()
                        )
                    } else Optional.empty()
                    progress = StepProgress.Done
                    update()
                    true
                }
            }

        override fun stop(): Boolean {
            if (progress != StepProgress.Done) {
                progress = StepProgress.Stopped
                update()
            }
            if (stashInfo.hadStash == null) {
                stashInfo.hadStash = Optional.empty()
            }
            return true
        }

        override fun text(): String {
            val postfix = when(val stash = stashInfo.hadStash) {
                null -> ""
                else -> stash.map { "(stashed as ${it.id.abbreviate(6).name()})" }.orElse("(not needed)")
            }
            return "Stash ${repoName(repo)} $postfix"
        }
        override fun isSection(): Boolean = true
        override fun progress(): StepProgress = progress
    }

    private inner class UnStashSection(val repo: Git, val stashInfo: StashInfo): Section, Step {
        private var progress = StepProgress.None

        override fun createSteps(): List<Step> = listOf(this)

        override fun proceed(): Boolean =
            when (progress) {
                StepProgress.Done -> true
                StepProgress.Stopped -> throw Exception("Cannot be Stopped?")
                StepProgress.None, StepProgress.Processing -> {
                    progress = StepProgress.Processing
                    update()
                    stashInfo.hadStash!!.orElse(null)?.let {
                        repo.stashDrop().setStashRef(0).call()
                        repo.stashApply().setStashRef(it.name).call()
                    }
                    progress = StepProgress.Done
                    update()
                    true
                }
            }

        override fun stop(): Boolean = proceed()

        override fun text(): String {
            val postfix = when(val stash = stashInfo.hadStash) {
                null -> ""
                else -> stash.map { "(stashed as ${it.id.abbreviate(6).name()})" }.orElse("(not needed)")
            }
            return "Restore ${repoName(repo)} $postfix"
        }
        override fun isSection(): Boolean = true
        override fun progress(): StepProgress = progress
    }
}

