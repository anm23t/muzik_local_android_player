package org.muzzzlo.player.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import org.muzzzlo.player.core.model.AutoBackupFiles
import java.io.IOException
import javax.inject.Inject

/**
 * Saves the automatic copies into the folder chosen with the system picker (a tree URI with a permission kept
 * across restarts). The folder is only used through [DocumentsContract], no extra library is needed.
 */
class BackupFolder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val resolver get() = context.contentResolver

        /**
         * Writes [text] as [name], replacing a file of that name, and then deletes the old automatic copies so that
         * [AutoBackupFiles.KEEP] remain. Throws [IOException] or [SecurityException] when the folder is gone or the
         * permission was taken away; the old copies stay then.
         */
        fun save(
            treeUri: String,
            name: String,
            text: String,
        ) {
            val tree = Uri.parse(treeUri)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val children = children(tree, parent)
            val created =
                DocumentsContract.createDocument(resolver, parent, JSON, name)
                    ?: throw IOException("Cannot create $name")
            try {
                resolver.openOutputStream(created, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    ?: throw IOException("Cannot open $name")
            } catch (e: IOException) {
                DocumentsContract.deleteDocument(resolver, created)
                throw e
            }
            // The provider renames the new file ("name (1).json") when the name is taken: the old file of the same
            // day goes first and the new one gets the plain name back.
            val sameDay = children.filter { it.name == name }
            (sameDay.map { it.uri } + staleCopies(children)).forEach {
                runCatching { DocumentsContract.deleteDocument(resolver, it) }
            }
            if (sameDay.isNotEmpty()) runCatching { DocumentsContract.renameDocument(resolver, created, name) }
        }

        private fun staleCopies(children: List<Child>): List<Uri> {
            val stale = AutoBackupFiles.toDelete(children.map { it.name }).toSet()
            return children.filter { it.name in stale }.map { it.uri }
        }

        private fun children(
            tree: Uri,
            parent: Uri,
        ): List<Child> {
            val childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    tree,
                    DocumentsContract.getDocumentId(parent),
                )
            val columns =
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val result = mutableListOf<Child>()
            resolver.query(childrenUri, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                    result += Child(cursor.getString(1), uri)
                }
            }
            return result
        }

        private class Child(val name: String, val uri: Uri)

        private companion object {
            const val JSON = "application/json"
        }
    }
