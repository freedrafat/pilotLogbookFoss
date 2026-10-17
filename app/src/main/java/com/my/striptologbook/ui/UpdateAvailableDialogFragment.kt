package com.my.striptologbook.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.my.striptologbook.utils.PreferencesManager
import com.my.striptologbook.utils.UpdateInfo

/**
 * The "Update Available" dialog shown on Home when the automatic check
 * finds an update. Displayed via the FragmentManager so it survives
 * configuration changes: after rotation the dialog reappears with the
 * same content and the same UpdateInfo.
 *
 * Content is identical to the dialog shown by UpdatesActivity when the
 * user taps "Check for App Update" manually.
 */
class UpdateAvailableDialogFragment : DialogFragment() {

    /**
     * Host must implement this to receive the user's choice.
     */
    interface Listener {
        fun onUpdateAccepted(info: UpdateInfo)
        fun onUpdateDeferred()
    }

    private var listener: Listener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val info = UpdateInfo(
            apkUrl = args.getString(ARG_URL, ""),
            fileName = args.getString(ARG_FILENAME, ""),
            versionName = args.getString(ARG_VERSION, "")
        )

        val autoInstall = PreferencesManager
            .getInstance(requireContext())
            .isAutoInstallEnabled()

        val message = "A new version is available.\n\n" +
                (if (autoInstall) "It will be downloaded and installed automatically."
                else "It will be saved in your Downloads folder for manual installation.")

        return AlertDialog.Builder(requireContext())
            .setTitle("Update Available")
            .setMessage(message)
            .setPositiveButton("Download") { _, _ ->
                listener?.onUpdateAccepted(info)
            }
            .setNegativeButton("Later") { _, _ ->
                listener?.onUpdateDeferred()
            }
            .create()
    }

    companion object {
        const val TAG = "UpdateAvailableDialogFragment"

        private const val ARG_URL     = "arg_url"
        private const val ARG_FILENAME = "arg_filename"
        private const val ARG_VERSION  = "arg_version"

        fun newInstance(info: UpdateInfo): UpdateAvailableDialogFragment =
            UpdateAvailableDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, info.apkUrl)
                    putString(ARG_FILENAME, info.fileName)
                    putString(ARG_VERSION, info.versionName)
                }
            }
    }
}