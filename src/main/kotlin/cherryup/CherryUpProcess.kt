package cherryup

import cherryup.ui.Step
import cherryup.ui.StepProgress
import cherryup.ui.UiModel
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.Constants.HEAD
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.nio.file.Path
import java.util.*
import javax.swing.DefaultListModel
import javax.swing.ListModel
import kotlin.collections.ArrayList

data class Repo(val git: Git, val name: String)

class CherryUpProcess(val repos: List<Repo>, branchFlow: List<BranchTransition>): UiModel(), AutoCloseable {
    private val sections = ArrayList<Section>()
    private var curListModel: ListModel<Step> = DefaultListModel()

    init {
        val stashInfos = repos.map { StashInfo() }
        sections.addAll(repos.indices.map { StashSection(repos[it], stashInfos[it]) })

        for (bf in branchFlow) {
            sections.add(BranchSection(bf))
        }

        sections.addAll(repos.indices.map { UnStashSection(repos[it], stashInfos[it]) })

        updateSteps()
    }

    private fun updateSteps() {
        val newModel = DefaultListModel<Step>()
        newModel.addAll(sections.flatMap { it.createSteps() })
        curListModel = newModel
        update()
    }

    override fun proceed(): Boolean = sections.all { it.proceed() }

    override fun stop(): Boolean = sections.all { it.stop() }

    override fun close() {
        repos.forEach { it.git.close() }
    }

    override fun listModel(): ListModel<Step> = curListModel

    companion object Factory {
        fun create(paths: Map<String, Path>, branchFlow: List<BranchTransition>): CherryUpProcess {
            return CherryUpProcess(paths.map { openGit(it.value, it.key) }, branchFlow)
        }

        private fun openGit(path: Path, name: String): Repo {
            return Repo(
                Git(FileRepositoryBuilder()
                    .setGitDir(path.resolve(".git").toFile())
                    .findGitDir()
                    .setMustExist(true)
                    .build()),
                name
            )
        }
    }

    private class StashInfo(var hadStash: Optional<RevCommit>? = null)
    private class BranchSwitchInfo(var originalBranch: Optional<Ref>? = null)

    private interface Section {
        fun createSteps(): List<Step>
        fun proceed(): Boolean
        fun stop(): Boolean
    }

    private inner class StashSection(val repo: Repo, val stashInfo: StashInfo): Section, Step {
        private var progress = StepProgress.None
            set(value) {
                field = value
                update()
            }

        override fun createSteps(): List<Step> = listOf(this)

        override fun proceed(): Boolean =
            when (progress) {
                StepProgress.Done, StepProgress.Stopped -> true
                StepProgress.None, StepProgress.Processing -> {
                    progress = StepProgress.Processing
                    val needsStashing = !repo.git.status().call().isClean
                    stashInfo.hadStash = if (needsStashing) {
                        Optional.of(
                            repo.git.stashCreate()
                            .setIncludeUntracked(true)
                            .setWorkingDirectoryMessage("CherryUp: Stash current WIP on {0}: {1} {2}")
                            .call()
                        )
                    } else Optional.empty()
                    progress = StepProgress.Done
                    true
                }
            }

        override fun stop(): Boolean {
            if (progress != StepProgress.Done) {
                progress = StepProgress.Stopped
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
            return "Stash ${repo.name} $postfix"
        }
        override fun isSection(): Boolean = true
        override fun progress(): StepProgress = progress
    }

    private inner class UnStashSection(val repo: Repo, val stashInfo: StashInfo): Section, Step {
        private var progress = StepProgress.None
            set(value) {
                field = value
                update()
            }

        override fun createSteps(): List<Step> = listOf(this)

        override fun proceed(): Boolean =
            when (progress) {
                StepProgress.Done -> true
                StepProgress.Stopped -> throw Exception("Cannot be Stopped?")
                StepProgress.None, StepProgress.Processing -> {
                    progress = StepProgress.Processing
                    stashInfo.hadStash!!.orElse(null)?.let {
                        repo.git.stashDrop().setStashRef(0).call()
                        repo.git.stashApply().setStashRef(it.name).call()
                    }
                    progress = StepProgress.Done
                    true
                }
            }

        override fun stop(): Boolean = proceed()

        override fun text(): String {
            val postfix = when(val stash = stashInfo.hadStash) {
                null -> ""
                else -> stash.map { "(stashed as ${it.id.abbreviate(6).name()})" }.orElse("(not needed)")
            }
            return "Restore ${repo.name} $postfix"
        }
        override fun isSection(): Boolean = true
        override fun progress(): StepProgress = progress
    }

    /*private inner class TestThrowException: Section, Step {
        private var progress = StepProgress.None

        override fun createSteps(): List<Step> = listOf(this)

        override fun proceed(): Boolean {
            progress = StepProgress.Processing
            throw java.lang.Exception("Noooooo!")
        }

        override fun stop(): Boolean {
            progress = StepProgress.Stopped
            return true
        }

        override fun text(): String = "throw test"

        override fun isSection(): Boolean {
            return false
        }

        override fun progress(): StepProgress = progress
    }*/

    private enum class BranchState {
        None,
        Analyzing,
        Analyzed,
        Running,
        Done,
        Stopped,
    }

    private inner class BranchSection(val branchTransition: BranchTransition): Section, Step {
        private var state: BranchState = BranchState.None
            set(value) {
                field = value
                update()
            }
        private var steps: List<BranchStep>? = null
            set(value) {
                field = value
                updateSteps()
            }

        override fun createSteps(): List<Step> {
            val builder = ArrayList<Step>()
            builder.add(this)

            steps?.let { builder.addAll(it) }

            return builder
        }

        override fun proceed(): Boolean = when (state) {
            BranchState.None, BranchState.Analyzing -> {
                state = BranchState.Analyzing
                val newSteps = ArrayList<BranchStep>()
                val branchSetupSteps = repos.map {
                    PrepareBranch(
                        it,
                        branchTransition.to,
                        "origin/${branchTransition.to}",
                        BranchSwitchInfo()
                    )
                }
                newSteps.addAll(branchSetupSteps)


                steps = newSteps
                state = BranchState.Analyzed
                false
            }
            BranchState.Analyzed, BranchState.Running -> {
                val steps = this.steps
                require(steps != null)
                steps.all { it.run() }
            }
            BranchState.Done -> true
            BranchState.Stopped -> throw Exception("Should not happen")
        }

        override fun stop(): Boolean = when(state) {
            BranchState.None, BranchState.Analyzing, BranchState.Analyzed -> {
                steps = listOf()
                state = BranchState.Stopped
                true
            }
            BranchState.Running -> {
                TODO("Not yet implemented")
            }
            BranchState.Done -> true
            BranchState.Stopped -> true
        }

        override fun text(): String = "${branchTransition.from} -> ${branchTransition.to}"
        override fun isSection(): Boolean = true

        override fun progress(): StepProgress = when(state) {
            BranchState.None -> StepProgress.None
            BranchState.Analyzing -> StepProgress.Processing
            BranchState.Analyzed -> StepProgress.None
            BranchState.Running -> StepProgress.Processing
            BranchState.Done -> StepProgress.Done
            BranchState.Stopped -> StepProgress.Stopped
        }

        private abstract inner class BranchStep: Step {
            abstract fun run(): Boolean
        }

        private inner class PrepareBranch(val repo: Repo,
                                          val targetBranchName: String,
                                          val originBranchName: String,
                                          val branchSwitchInfo: BranchSwitchInfo): BranchStep() {
            private var progress = StepProgress.None
                set(value) {
                    field = value
                    update()
                }

            override fun run(): Boolean {
                progress = StepProgress.Processing

                val originBranch = repo.git.repository.exactRef(originBranchName)
                    ?: throw Exception("Expected to find an upstream branch $originBranchName for ${repo.name}")

                val head = repo.git.repository.exactRef(HEAD)
                    ?: throw Exception("Couldn't get HEAD??? in ${repo.name}")
                val currentBranch = head.leaf

                branchSwitchInfo.originalBranch = Optional.of(currentBranch)
                if (currentBranch.name != targetBranchName) {
                    repo.git.checkout()
                        .setCreateBranch(true)
                        .setName(targetBranchName)
                        .call()
                }

                repo.git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef(originBranch.name)
                    .call()

                progress = StepProgress.Done
                return true
            }

            override fun text(): String = "${repo.name}: Prepare Branch ${targetBranchName}"
            override fun isSection(): Boolean = false
            override fun progress(): StepProgress = progress
        }

        private inner class FinishBranch(val repo: Repo,
                                         val targetBranchName: String,
                                         val branchSwitchInfo: BranchSwitchInfo): BranchStep() {
            private var progress = StepProgress.None
                set(value) {
                    field = value
                    update()
                }

            override fun run(): Boolean {
                progress = StepProgress.Processing


                progress = StepProgress.Done
                return true
            }

            override fun text(): String = "${repo.name}: Prepare Branch ${targetBranchName}"
            override fun isSection(): Boolean = false
            override fun progress(): StepProgress = progress
        }
    }
}

