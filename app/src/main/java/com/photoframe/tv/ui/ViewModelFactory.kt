package com.photoframe.tv.ui.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Builds a one-off [ViewModelProvider.Factory] from a lambda, for manual DI. */
inline fun <VM : ViewModel> simpleFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
