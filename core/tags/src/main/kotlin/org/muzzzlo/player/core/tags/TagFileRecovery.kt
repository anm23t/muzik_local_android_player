package org.muzzzlo.player.core.tags

/**
 * Cleans up after a process that died while it read or wrote tags. Runs once per process, before the first
 * native call of [TagLibTagReader] or [SafeTagWriter]:
 * - files left "in progress" by a native crash go to quarantine ([NativeCallGuard.recoverAfterCrash]);
 * - originals left half-replaced by a tag write are restored from their backups ([WriteJournal.recoverAll]),
 *   so the scanner never indexes a damaged file.
 *
 * An original that cannot be restored yet stays in the journal; [SafeTagWriter] retries it before the next
 * write to the same file.
 */
internal class TagFileRecovery(
    private val guard: NativeCallGuard,
    private val journal: WriteJournal,
    private val files: OriginalFiles,
) {
    private val recovery =
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            guard.recoverAfterCrash()
            journal.recoverAll(files)
        }

    /** Runs the recovery if it has not run in this process yet. Does file I/O. */
    fun ensureRecovered() {
        recovery.value
    }
}
