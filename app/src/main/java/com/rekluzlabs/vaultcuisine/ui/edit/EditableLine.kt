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
