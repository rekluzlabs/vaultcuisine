/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.ui.edit

sealed class LineDetail {
    data class Ingredient(val amount: String?, val unit: String?) : LineDetail()
    data class Step(val timerSeconds: Int?) : LineDetail()
}

data class EditableLine(
    val id: String,
    val text: String,
    val detail: LineDetail
)
