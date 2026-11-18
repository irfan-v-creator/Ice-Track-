package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.auth.AuthUiResult
import com.example.ui.auth.GoogleSignInManager
import com.example.ui.theme.FigmaBackground
import com.example.ui.theme.FigmaInk
import com.example.ui.theme.FigmaLine
import com.example.ui.theme.FigmaMuted
import com.example.ui.theme.FigmaNavy
import com.example.ui.theme.FigmaRed
import com.example.ui.theme.FigmaSoftTeal
import com.example.ui.theme.FigmaTeal
import com.example.ui.theme.FigmaWhite
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
  onAuthenticated: (FirebaseUser) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var isSigningIn by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  // Attempt silent auto-sign-in for previously authorized accounts once on first mount
  LaunchedEffect(Unit) {
    val activity = context as? Activity ?: return@LaunchedEffect
    when (val res = GoogleSignInManager.trySilentSignIn(activity)) {
      is AuthUiResult.Success -> onAuthenticated(res.user)
      else -> Unit
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(FigmaSoftTeal, FigmaBackground, FigmaWhite)
        )
      )
      .statusBarsPadding()
      .navigationBarsPadding()
      .padding(horizontal = 24.dp, vertical = 24.dp)
      .testTag("auth_screen"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 520.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      // Brand Emblem
      Box(
        modifier = Modifier
          .size(76.dp)
          .clip(CircleShape)
          .background(FigmaNavy),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.AcUnit,
          contentDescription = "IceTrack Emblem",
          tint = FigmaWhite,
          modifier = Modifier.size(38.dp)
        )
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = "IceTrack",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = FigmaNavy
        )
        Text(
          text = "Wholesale Ice Cream Distribution & Ledger",
          style = MaterialTheme.typography.bodyMedium,
          color = FigmaMuted,
          textAlign = TextAlign.Center
        )
      }

      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FigmaWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, FigmaLine, RoundedCornerShape(16.dp))
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Text(
            text = "Sign in to Your Business Workspace",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = FigmaInk
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(Icons.Default.CloudDone, contentDescription = null, tint = FigmaTeal)
            Text(
              text = "All 25 local SQLite ledgers, batches, returns, notes & audit trails remain stored offline and sync privately to your Cloud Firestore account.",
              style = MaterialTheme.typography.bodySmall,
              color = FigmaMuted
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = FigmaNavy)
            Text(
              text = "Protected by Google Sign-In and user-scoped Cloud Firestore Zero-Trust Security Rules.",
              style = MaterialTheme.typography.bodySmall,
              color = FigmaMuted
            )
          }

          errorMessage?.let { err ->
            Surface(
              color = FigmaRed.copy(alpha = 0.08f),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FigmaRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .testTag("auth_error_banner")
            ) {
              Text(
                text = err,
                color = FigmaRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(2.dp))

          Button(
            onClick = {
              if (isSigningIn) return@Button
              scope.launch {
                isSigningIn = true
                errorMessage = null
                when (val res = GoogleSignInManager.signInInteractive(context)) {
                  is AuthUiResult.Success -> {
                    isSigningIn = false
                    onAuthenticated(res.user)
                  }
                  AuthUiResult.Cancelled -> {
                    isSigningIn = false
                  }
                  is AuthUiResult.Error -> {
                    isSigningIn = false
                    errorMessage = res.message
                  }
                }
              }
            },
            enabled = !isSigningIn,
            colors = ButtonDefaults.buttonColors(
              containerColor = FigmaTeal,
              contentColor = FigmaWhite
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 50.dp)
              .testTag("google_sign_in_button")
          ) {
            if (isSigningIn) {
              CircularProgressIndicator(
                color = FigmaWhite,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text("Signing in with Google...", fontWeight = FontWeight.SemiBold)
            } else {
              Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
              Spacer(modifier = Modifier.width(10.dp))
              Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }
  }
}
