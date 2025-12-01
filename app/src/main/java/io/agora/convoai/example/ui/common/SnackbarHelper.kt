package io.agora.convoai.example.startup.ui.common

import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import io.agora.convoai.example.R

object SnackbarHelper {
    /**
     * Show a Snackbar with normal background color
     * @param fragment The fragment to show the Snackbar in
     * @param message The message to display
     * @param duration The duration of the Snackbar (default: Snackbar.LENGTH_SHORT)
     */
    fun showNormal(fragment: Fragment, message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        val rootView = fragment.activity?.findViewById(android.R.id.content)
            ?: fragment.view
        rootView?.let { view ->
            val snackbar = Snackbar.make(view, message, duration)
            snackbar.view.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(fragment.requireContext(), R.color.snackbar_background)
            )
            snackbar.setTextColor(ContextCompat.getColor(fragment.requireContext(), R.color.snackbar_text))
            // Remove margins to make it stick to screen edges
            snackbar.view.post {
                val layoutParams = snackbar.view.layoutParams as? ViewGroup.MarginLayoutParams
                layoutParams?.let {
                    it.leftMargin = 0
                    it.rightMargin = 0
                    it.bottomMargin = 0
                    snackbar.view.layoutParams = it
                }
            }
            snackbar.show()
        }
    }

    /**
     * Show a Snackbar with error background color
     * @param fragment The fragment to show the Snackbar in
     * @param message The message to display
     * @param duration The duration of the Snackbar (default: Snackbar.LENGTH_LONG)
     */
    fun showError(fragment: Fragment, message: String, duration: Int = Snackbar.LENGTH_LONG) {
        val rootView = fragment.activity?.findViewById(android.R.id.content)
            ?: fragment.view
        rootView?.let { view ->
            val snackbar = Snackbar.make(view, message, duration)
            snackbar.view.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(fragment.requireContext(), R.color.snackbar_background_error)
            )
            snackbar.setTextColor(ContextCompat.getColor(fragment.requireContext(), R.color.snackbar_text))
            // Remove margins to make it stick to screen edges
            snackbar.view.post {
                val layoutParams = snackbar.view.layoutParams as? ViewGroup.MarginLayoutParams
                layoutParams?.let {
                    it.leftMargin = 0
                    it.rightMargin = 0
                    it.bottomMargin = 0
                    snackbar.view.layoutParams = it
                }
            }
            snackbar.show()
        }
    }
}

