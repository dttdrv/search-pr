package app.pane.browser.debug

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.util.Log
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews

/**
 * Logs what a password manager is given for each fill and save request, and offers one dataset
 * so the smoke test can check filling end to end. Only present in debug builds.
 */
class ProbeAutofillService : AutofillService() {

    private data class Field(val id: AutofillId, val kind: String)

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.last().structure
        val fields = mutableListOf<Field>()
        val domains = linkedSetOf<String>()
        structure.forEachNode { node ->
            node.webDomain?.let { domains += it }
            val kind = kindOf(node) ?: return@forEachNode
            val id = node.autofillId ?: return@forEachNode
            fields += Field(id, kind)
            Log.i(TAG, "FIELD kind=$kind hints=${node.autofillHints?.joinToString()} html=${htmlOf(node)} focused=${node.isFocused}")
        }
        Log.i(TAG, "FILL_REQUEST activity=${structure.activityComponent?.shortClassName} domains=$domains fields=${fields.map { it.kind }}")
        if (fields.isEmpty()) logTree(structure)

        val user = fields.firstOrNull { it.kind == "username" }
        val pass = fields.firstOrNull { it.kind == "password" }
        if (user == null && pass == null) {
            callback.onSuccess(null)
            return
        }
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, PROBE_USER)
        }
        @Suppress("DEPRECATION")
        val dataset = Dataset.Builder(presentation).apply {
            user?.let { setValue(it.id, AutofillValue.forText(PROBE_USER)) }
            pass?.let { setValue(it.id, AutofillValue.forText(PROBE_PASSWORD)) }
        }.build()
        val ids = listOfNotNull(user?.id, pass?.id).toTypedArray()
        val response = FillResponse.Builder()
            .addDataset(dataset)
            .setSaveInfo(SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD, ids).build())
            .build()
        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.last().structure
        val domains = linkedSetOf<String>()
        val values = mutableListOf<String>()
        structure.forEachNode { node ->
            node.webDomain?.let { domains += it }
            val kind = kindOf(node) ?: return@forEachNode
            val text = node.autofillValue?.takeIf { it.isText }?.textValue?.toString().orEmpty()
            values += if (kind == "password") "$kind=${"*".repeat(text.length)}" else "$kind=$text"
        }
        Log.i(TAG, "SAVE_REQUEST domains=$domains values=$values")
        callback.onSuccess()
    }

    private fun kindOf(node: AssistStructure.ViewNode): String? {
        if (node.autofillType != View.AUTOFILL_TYPE_TEXT) return null
        val hints = node.autofillHints.orEmpty().map { it.lowercase() }
        val attrs = node.htmlInfo?.attributes.orEmpty().associate { it.first.lowercase() to it.second.orEmpty().lowercase() }
        val type = attrs["type"]
        val name = listOfNotNull(attrs["name"], attrs["id"], attrs["autocomplete"]).joinToString(" ")
        return when {
            type == "password" || hints.any { "password" in it } -> "password"
            type == "email" || hints.any { "username" in it || "email" in it } -> "username"
            listOf("user", "login", "email").any { it in name } -> "username"
            else -> "text"
        }
    }

    /** The view tree as the service sees it, for working out why no web fields arrived. */
    private fun logTree(structure: AssistStructure) {
        var lines = 0
        fun walk(node: AssistStructure.ViewNode, depth: Int) {
            if (lines++ > 150) return
            Log.i(
                TAG,
                "TREE ${" ".repeat(depth)}${node.className?.substringAfterLast('.')} children=${node.childCount} " +
                    "type=${node.autofillType} domain=${node.webDomain} html=${node.htmlInfo?.tag} visible=${node.visibility}",
            )
            for (i in 0 until node.childCount) walk(node.getChildAt(i), depth + 1)
        }
        for (i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode, 0)
    }

    private fun htmlOf(node: AssistStructure.ViewNode): String? = node.htmlInfo?.let { info ->
        info.tag + info.attributes.orEmpty().joinToString(prefix = "[", postfix = "]") { "${it.first}=${it.second}" }
    }

    private fun AssistStructure.forEachNode(block: (AssistStructure.ViewNode) -> Unit) {
        fun walk(node: AssistStructure.ViewNode) {
            block(node)
            for (i in 0 until node.childCount) walk(node.getChildAt(i))
        }
        for (i in 0 until windowNodeCount) walk(getWindowNodeAt(i).rootViewNode)
    }

    private companion object {
        const val TAG = "PaneAutofillProbe"
        const val PROBE_USER = "pane-probe-user"
        const val PROBE_PASSWORD = "pane-probe-pass"
    }
}
