package fi.hell.storygame.model

import android.content.Context
import fi.hell.storygame.R
import java.util.*
import kotlin.collections.ArrayList

class Language {
    companion object {
        fun getLocalesForSpinner(context: Context): ArrayList<Locale> {
            val options = arrayListOf(Locale("", context.getString(R.string.select_language)))
            val locales = Locale.getAvailableLocales()
                .filter { it.country.isNotBlank() }.sortedWith(compareBy { it.displayCountry })
                .filter {
                    context.resources.getIdentifier("flag_" + it.country.toLowerCase(),"drawable","fi.hell.storygame") != 0
                }
            options.addAll(locales)
            return options
        }
    }
}