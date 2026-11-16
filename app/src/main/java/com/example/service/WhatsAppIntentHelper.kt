package com.example.service

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

data class WhatsAppAppOption(
  val packageName: String,
  val displayLabel: String
)

data class WhatsAppAvailability(
  val installedApps: List<WhatsAppAppOption>,
  val hasAnyHandler: Boolean
)

object WhatsAppIntentHelper {

  const val WHATSAPP_PACKAGE = "com.whatsapp"
  const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
  private const val MAX_URL_MESSAGE_CHARS = 1800

  fun detectInstalledWhatsAppApps(context: Context): WhatsAppAvailability {
    val pm = context.packageManager
    val options = mutableListOf<WhatsAppAppOption>()

    if (isPackageInstalled(pm, WHATSAPP_PACKAGE)) {
      options.add(WhatsAppAppOption(WHATSAPP_PACKAGE, "WhatsApp"))
    }
    if (isPackageInstalled(pm, WHATSAPP_BUSINESS_PACKAGE)) {
      options.add(WhatsAppAppOption(WHATSAPP_BUSINESS_PACKAGE, "WhatsApp Business"))
    }

    val probeIntent = buildDeepLinkIntent(
      whatsappPhoneDigits = "919999999999",
      message = "Reminder",
      targetPackage = null
    )
    val canResolveDeepLink = probeIntent.resolveActivity(pm) != null

    return WhatsAppAvailability(
      installedApps = options,
      hasAnyHandler = options.isNotEmpty() || canResolveDeepLink
    )
  }

  private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, 0)
      }
      true
    } catch (_: PackageManager.NameNotFoundException) {
      false
    }
  }

  fun buildWhatsAppLaunchIntent(
    context: Context,
    whatsappPhoneDigits: String,
    message: String,
    targetPackage: String?
  ): Intent {
    val cleanDigits = whatsappPhoneDigits.filter { it.isDigit() }
    val intent = if (message.length <= MAX_URL_MESSAGE_CHARS) {
      buildDeepLinkIntent(cleanDigits, message, targetPackage)
    } else {
      buildSendTextFallbackIntent(cleanDigits, message, targetPackage)
    }
    if (context !is Activity) {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return intent
  }

  fun buildDeepLinkIntent(
    whatsappPhoneDigits: String,
    message: String,
    targetPackage: String?
  ): Intent {
    val encodedMessage = Uri.encode(message)
    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$whatsappPhoneDigits&text=$encodedMessage")
    return Intent(Intent.ACTION_VIEW, uri).apply {
      if (!targetPackage.isNullOrBlank()) {
        setPackage(targetPackage)
      }
    }
  }

  fun buildSendTextFallbackIntent(
    whatsappPhoneDigits: String,
    message: String,
    targetPackage: String?
  ): Intent {
    return Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, message)
      if (whatsappPhoneDigits.isNotBlank()) {
        putExtra("jid", "$whatsappPhoneDigits@s.whatsapp.net")
      }
      if (!targetPackage.isNullOrBlank()) {
        setPackage(targetPackage)
      }
    }
  }

  fun copyMessageToClipboard(context: Context, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Payment Reminder", message))
  }
}
