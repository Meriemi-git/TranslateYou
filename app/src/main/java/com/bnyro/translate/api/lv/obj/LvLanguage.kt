package com.bnyro.translate.api.lv.obj

import com.bnyro.translate.db.obj.Language

data class LvLanguage(
    val languages: List<Language> = emptyList()
)
