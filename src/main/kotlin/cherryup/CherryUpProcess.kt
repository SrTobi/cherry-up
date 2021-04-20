package cherryup

import cherryup.ui.Step
import cherryup.ui.StepProgress
import cherryup.ui.UiModel
import org.eclipse.jgit.api.CherryPickResult
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.nio.file.Path
import java.util.*
import javax.swing.DefaultListModel
import javax.swing.ListModel

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
    private class BranchSwitchInfo(var originalBranch: Optional<String>? = null)

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
                    val email = it.git.repository.config.getString("user", null, "email")
                        ?: throw Exception("No user email set for ${it.name}.")
                    val author = email.split('@').first()
                    val targetBranchName = "${branchTransition.to}-${author}-cherryup/from-${branchTransition.from}"
                    val originToBranchName = "origin/${branchTransition.to}"
                    val originBranch = it.git.repository.findRef(originToBranchName)
                        ?: throw Exception("Expected to find an upstream branch $originToBranchName for ${it.name}")

                    PrepareBranch(
                        it,
                        targetBranchName,
                        originBranch,
                        BranchSwitchInfo()
                    )
                }
                newSteps.addAll(branchSetupSteps)

                val cherryPickSteps = branchSetupSteps.flatMap {
                    val repo = it.repo
                    val originFromBranchName = "origin/${branchTransition.from}"
                    val originFromBranch = repo.git.repository.findRef(originFromBranchName)
                        ?: throw Exception("Expected to find an upstream branch $originFromBranchName for ${repo.name}")
                    BranchDiff.diff(repo.git, originFromBranch, it.originToBranch).map {
                        CherryPick(repo, it)
                    }
                }

                if (cherryPickSteps.isEmpty()) {
                    steps = cherryPickSteps
                    state = BranchState.Done
                    true
                } else {
                    val sortedCherryPickSteps = cherryPickSteps.sortedBy { it.commit.commitTime }
                    newSteps.addAll(sortedCherryPickSteps)

                    newSteps.add(Wait())

                    branchSetupSteps.forEach {
                        newSteps.add(Push(it.repo, it.targetBranchName))
                    }

                    branchSetupSteps.forEach {
                        newSteps.add(FinishBranch(it.repo, it.targetBranchName, it.branchSwitchInfo))
                    }

                    steps = newSteps
                    state = BranchState.Analyzed
                    false
                }
            }
            BranchState.Analyzed, BranchState.Running -> {
                state = BranchState.Running
                val steps = this.steps
                require(steps != null)
                val done = steps.all { it.run() }
                if (done) {
                    state = BranchState.Done
                }
                done
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
                val done = steps!!.all { it.stop() }
                if (done) {
                    state = BranchState.Stopped
                }
                done
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
            protected var progress = StepProgress.None
                set(value) {
                    field = value
                    update()
                }

            final override fun progress(): StepProgress = progress
            final override fun isSection(): Boolean = false

            abstract fun run(): Boolean
            abstract fun stop(): Boolean
        }

        private inner class PrepareBranch(val repo: Repo,
                                          val targetBranchName: String,
                                          val originToBranch: Ref,
                                          val branchSwitchInfo: BranchSwitchInfo): BranchStep() {
            override fun run(): Boolean = when(progress) {
                StepProgress.None, StepProgress.Processing -> {
                    progress = StepProgress.Processing

                    val currentBranch = repo.git.repository.branch
                        ?: throw Exception("Couldn't get current branch??? in ${repo.name}")

                    val saveBranch =
                        if (currentBranch != targetBranchName) {
                            repo.git.checkout()
                                .setCreateBranch(true)
                                .setName(targetBranchName)
                                .setStartPoint(originToBranch.name)
                                .call()
                            Optional.of(currentBranch)
                        } else {
                            Optional.empty()
                        }

                    if (branchSwitchInfo.originalBranch == null) {
                        branchSwitchInfo.originalBranch = saveBranch
                    }

                    repo.git.reset()
                        .setMode(ResetCommand.ResetType.HARD)
                        .setRef(originToBranch.name)
                        .call()

                    progress = StepProgress.Done
                    true
                }
                StepProgress.Done, StepProgress.Stopped -> true
            }

            override fun stop(): Boolean = when(progress) {
                StepProgress.None, StepProgress.Processing -> {
                    progress = StepProgress.Stopped
                    true
                }
                StepProgress.Stopped, StepProgress.Done -> true
            }

            override fun text(): String = "${repo.name}: Prepare Branch $targetBranchName"
        }

        private inner class FinishBranch(val repo: Repo,
                                         val targetBranchName: String,
                                         val branchSwitchInfo: BranchSwitchInfo): BranchStep() {
            override fun run(): Boolean {
                progress = StepProgress.Processing

                val originalBranch = branchSwitchInfo.originalBranch
                require(originalBranch != null)
                originalBranch.ifPresent {
                    repo.git.checkout()
                        .setName(it)
                        .call()

                    repo.git.branchDelete()
                        .setBranchNames(targetBranchName)
                        .setForce(true)
                        .call()
                }

                progress = StepProgress.Done
                return true
            }

            override fun stop(): Boolean = when(progress) {
                StepProgress.None, StepProgress.Processing -> {
                    if (branchSwitchInfo.originalBranch != null) {
                        run()
                    } else {
                        progress = StepProgress.Stopped
                        true
                    }
                }
                StepProgress.Stopped, StepProgress.Done -> true
            }

            override fun text(): String {
                val postfix =
                    branchSwitchInfo.originalBranch?.map { "(back to ${it})" }?.orElseGet { "" } ?: ""
                return "${repo.name}: Finalize Branch $targetBranchName$postfix"
            }
        }

        private inner class Wait: BranchStep() {
            private var didWait = false

            override fun run(): Boolean =
                if (didWait) {
                    progress = StepProgress.Done
                    true
                } else {
                    didWait = true
                    false
                }

            override fun stop(): Boolean = when(progress) {
                StepProgress.None, StepProgress.Processing -> {
                    progress = StepProgress.Stopped
                    true
                }
                StepProgress.Stopped, StepProgress.Done -> true
            }

            override fun text(): String = "-------- Ok? ----------"
        }

        private inner class Push(val repo: Repo, val targetBranchName: String): BranchStep() {
            override fun run(): Boolean {
                // push to branch
                progress = StepProgress.Done
                return true
            }

            override fun stop(): Boolean = when(progress) {
                StepProgress.None, StepProgress.Processing -> {
                    progress = StepProgress.Stopped
                    true
                }
                StepProgress.Stopped, StepProgress.Done -> true
            }

            override fun text(): String = "${repo.name}: Push to origin/${targetBranchName}"
        }

        private inner class CherryPick(val repo: Repo, val commit: RevCommit): BranchStep() {
            private var hadMergeConflict = false

            override fun run(): Boolean = when(progress) {
                StepProgress.None, StepProgress.Processing -> {
                    progress = StepProgress.Processing
                    if (!hadMergeConflict) runCherryPicking()
                    else runContinuing()
                }
                StepProgress.Stopped, StepProgress.Done -> true
            }

            private fun runCherryPicking(): Boolean {
                val result = repo.git.cherryPick()
                    .include(commit)
                    .call()
                val hash = commit.id.abbreviate(6).name()

                return when (result.status!!) {
                    CherryPickResult.CherryPickStatus.OK -> {
                        progress = StepProgress.Done
                        true
                    }
                    CherryPickResult.CherryPickStatus.CONFLICTING -> {
                        hadMergeConflict = true
                        throw Exception("Cherry picking $hash has merge conflicts! Please Resolve")
                    }
                    CherryPickResult.CherryPickStatus.FAILED -> {
                        throw Exception("Failed to cherry-pick $hash!")
                    }
                }
            }

            private fun runContinuing(): Boolean {
                val status = repo.git.status().call()
                val changedPaths = ArrayList<String>()
                changedPaths.addAll(status.conflicting)
                changedPaths.addAll(status.modified)
                changedPaths.addAll(status.missing)
                changedPaths.addAll(status.untracked)
                changedPaths.addAll(status.untrackedFolders)

                if (changedPaths.isNotEmpty()) {
                    throw Exception(
                        "Cannot continue cherry picking. Following paths are dirty:\n"
                            + changedPaths.joinToString("\n")
                    )
                } else {
                    repo.git.commit().setAmend(true).call()
                    progress = StepProgress.Done
                    return true
                }
            }

            override fun stop(): Boolean = when (progress) {
                StepProgress.None -> {
                    progress = StepProgress.Stopped
                    true
                }
                StepProgress.Processing -> {
                    if (repo.git.status().call().isClean) {
                        progress = StepProgress.Stopped
                        true
                    } else {
                        throw Exception("Couldn't stop cherry-picking because repo is not clean.")
                    }
                }
                StepProgress.Done, StepProgress.Stopped -> true
            }

            override fun text(): String = "${repo.name}: Cherry pick ${commit.id.abbreviate(6).name()} - ${commit.shortMessage}"
        }
    }
}

