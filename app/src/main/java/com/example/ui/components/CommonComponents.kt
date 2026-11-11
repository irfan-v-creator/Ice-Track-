package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.SubledgerCalculationEngine
import com.example.ui.theme.FigmaInk
import com.example.ui.theme.FigmaLine
import com.example.ui.theme.FigmaMuted
import com.example.ui.theme.FigmaRed
import com.example.ui.theme.FigmaSoftTeal
import com.example.ui.theme.FigmaTeal
import com.example.ui.theme.FigmaWhite

@Composable
fun CurrencyText(
  paise: Long,
  modifier: Modifier = Modifier,
  isReceivable: Boolean? = null,
  fontSize: Int = 16,
  fontWeight: FontWeight = FontWeight.SemiBold
) {
  val formatted = SubledgerCalculationEngine.formatPaise(paise)
  val textColor = when {
    isReceivable == true -> FigmaInk
    isReceivable == false -> if (paise > 0) FigmaRed else FigmaInk
    else -> FigmaInk
  }

  Text(
    text = formatted,
    color = textColor,
    fontSize = fontSize.sp,
    fontWeight = fontWeight,
    modifier = modifier
  )
}

@Composable
fun StatCard(
  title: String,
  value: String,
  subtitle: String? = null,
  icon: ImageVector,
  accentColor: Color = FigmaTeal,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = FigmaWhite),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, FigmaLine, RoundedCornerShape(12.dp))
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(46.dp)
          .clip(RoundedCornerShape(12.dp))
        .background(FigmaSoftTeal),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = FigmaTeal,
          modifier = Modifier.size(24.dp)
        )
      }

      Spacer(modifier = Modifier.width(16.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyMedium,
          color = FigmaMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = value,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = FigmaInk
        )
        if (subtitle != null) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = FigmaMuted
          )
        }
      }
    }
  }
}

@Composable
fun EmptyStateView(
  icon: ImageVector,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  actionButton: @Composable (() -> Unit)? = null
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Box(
      modifier = Modifier
        .size(60.dp)
        .clip(CircleShape)
        .background(FigmaSoftTeal),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = FigmaTeal,
        modifier = Modifier.size(28.dp)
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = FigmaInk,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = FigmaMuted,
      textAlign = TextAlign.Center
    )

    if (actionButton != null) {
      Spacer(modifier = Modifier.height(18.dp))
      actionButton()
    }
  }
}
