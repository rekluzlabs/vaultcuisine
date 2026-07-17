package com.rekluzlabs.vaultcuisine.print

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.rekluzlabs.vaultcuisine.data.Recipe

/**
 * Sends a saved recipe to the system print dialog, which also covers
 * "Save as PDF" as one of its destinations — no separate PDF export
 * code path needed.
 *
 * Print is only ever called on a SAVED recipe (see RecipeDetailScreen),
 * never mid-edit, so uncorrected OCR mistakes never make it to paper.
 */
object RecipePrinter {

    fun print(context: Context, recipe: Recipe) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager

        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val adapter = view.createPrintDocumentAdapter(recipe.title)
                printManager.print(
                    recipe.title,
                    adapter,
                    PrintAttributes.Builder().build()
                )
            }
        }
        webView.loadDataWithBaseURL(null, buildPrintHtml(recipe), "text/html", "UTF-8", null)
    }

    private fun buildPrintHtml(recipe: Recipe): String {
        val ingredientsHtml = recipe.ingredients.joinToString("\n") { ing ->
            val amountStr = ing.amount?.let { raw ->
                raw.toDoubleOrNull()?.let { num ->
                    if (num.rem(1.0) == 0.0) num.toInt().toString() else num.toString()
                } ?: raw
            } ?: ""
            "<li>${escape("$amountStr ${ing.unit.orEmpty()} ${ing.name}".trim())}</li>"
        }
        val stepsHtml = recipe.steps.joinToString("\n") { step ->
            "<li>${escape(step.text)}</li>"
        }
        val notesHtml = recipe.notes?.let { "<p><em>${escape(it)}</em></p>" }.orEmpty()

        return """
            <html>
            <head>
                <style>
                    body { font-family: sans-serif; padding: 24px; color: #1B1F1D; }
                    h1 { border-bottom: 2px solid #3D8361; padding-bottom: 8px; }
                    h2 { color: #3D8361; margin-top: 24px; }
                    li { margin-bottom: 6px; }
                </style>
            </head>
            <body>
                <h1>${escape(recipe.title)}</h1>
                <p>Servings: ${recipe.servings}</p>
                <h2>Ingredients</h2>
                <ul>$ingredientsHtml</ul>
                <h2>Instructions</h2>
                <ol>$stepsHtml</ol>
                $notesHtml
            </body>
            </html>
        """.trimIndent()
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
