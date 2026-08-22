package com.localai.chat.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localai.chat.data.model.ChatMessage
import com.localai.chat.data.model.MessageRole

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val context = LocalContext.current
    var showCopied by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(if (isUser) 1f else 1f)
        ) {
            if (!isUser) {
                // AI avatar indicator
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AI",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Column(
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isUser) 16.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 16.dp
                            )
                        )
                        .background(
                            if (isUser) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (message.isStreaming && message.content.isEmpty()) {
                        TypingIndicator()
                    } else {
                        Text(
                            text = message.content,
                            color = if (isUser)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 22.sp
                        )
                    }
                }

                // Copy button for AI messages
                if (!isUser && !message.isStreaming && message.content.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(
                            visible = showCopied,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Text(
                                text = "Copied!",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("AI Response", message.content))
                                showCopied = true
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy response",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // Auto-hide the "Copied!" indicator
                    LaunchedEffect(showCopied) {
                        if (showCopied) {
                            kotlinx.coroutines.delay(2000)
                            showCopied = false
                        }
                    }
                }
            }
        }
    }
}
