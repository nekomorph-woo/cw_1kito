package com.cw2.cw_1kito.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * API Key 验证状态
 */
sealed interface ApiKeyValidationState {
    /** 空闲状态，未进行验证 */
    data object Idle : ApiKeyValidationState

    /** 正在验证 */
    data object Validating : ApiKeyValidationState

    /** 验证成功 */
    data object Valid : ApiKeyValidationState

    /** 验证失败 */
    data class Invalid(val reason: String = "API Key 无效") : ApiKeyValidationState
}

/**
 * API Key 配置引导对话框
 *
 * 当翻译执行时发现 API Key 缺失时显示此对话框，
 * 用户可以直接在此对话框中输入和验证 API Key，
 * 也可以跳转到实验室设置页面进行配置。
 *
 * @param onDismiss 关闭对话框回调
 * @param onNavigateToSettings 跳转到设置页面回调
 * @param onValidateKey 验证 API Key 回调，返回 Boolean 表示是否有效
 * @param onSaveKey 保存 API Key 回调
 * @param modifier 修饰符
 */
@Composable
fun ApiKeyGuideDialog(
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onValidateKey: suspend (String) -> Boolean,
    onSaveKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var validationState by remember { mutableStateOf<ApiKeyValidationState>(ApiKeyValidationState.Idle)}

    val isValid = validationState == ApiKeyValidationState.Valid
    val isInvalid = validationState is ApiKeyValidationState.Invalid
    val isValidating = validationState == ApiKeyValidationState.Validating
    val canSave = apiKeyInput.isNotBlank() && isValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("需要配置 API Key")
        },
        text = {
            Column {
                Text("当前翻译方案需要 API Key 才能使用。")
                Spacer(modifier = Modifier.height(8.dp))

                // API Key 输入框
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        // 输入变化时重置验证状态
                        if (validationState !is ApiKeyValidationState.Validating) {
                            validationState = ApiKeyValidationState.Idle
                        }
                    },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(
                            onClick = { isPasswordVisible = !isPasswordVisible },
                            modifier = Modifier.width(48.dp)
                        ) {
                            Text(if (isPasswordVisible) "🙈" else "👁️")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    isError = isInvalid,
                    enabled = !isValidating,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        when (validationState) {
                            is ApiKeyValidationState.Idle -> {
                                if (apiKeyInput.isEmpty()) {
                                    Text("请输入 API Key")
                                } else {
                                    Text("点击「验证」按钮检查 API Key 是否有效")
                                }
                            }
                            is ApiKeyValidationState.Validating -> {
                                Text("正在验证...")
                            }
                            is ApiKeyValidationState.Valid -> {
                                Text("API Key 有效", color = MaterialTheme.colorScheme.primary)
                            }
                            is ApiKeyValidationState.Invalid -> {
                                val reason = (validationState as ApiKeyValidationState.Invalid).reason
                                Text(reason, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )

                // 验证按钮（当有输入且未验证时显示）
                if (apiKeyInput.isNotBlank() && validationState !is ApiKeyValidationState.Valid) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            // 触发验证逻辑（由调用者处理）
                        },
                        enabled = !isValidating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("验证中...")
                        } else {
                            Text("验证 API Key")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 获取 API Key 步骤说明
                Text(
                    "获取 API Key：",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("1. 访问 siliconflow.cn")
                Text("2. 注册/登录账号")
                Text("3. 在控制台获取 API Key")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canSave) {
                        onSaveKey(apiKeyInput)
                        onDismiss()
                    }
                },
                enabled = canSave
            ) {
                Text("保存并继续")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onNavigateToSettings) {
                Text("前往设置")
            }
        }
    )
}

/**
 * API Key 配置引导对话框（简化版）
 *
 * 仅提示用户前往设置页面配置，不支持直接输入。
 *
 * @param onDismiss 关闭对话框回调
 * @param onNavigateToSettings 跳转到设置页面回调
 * @param modifier 修饰符
 */
@Composable
fun ApiKeyGuideDialogSimple(
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("需要配置 API Key")
        },
        text = {
            Column {
                Text("当前翻译方案需要 API Key 才能使用。")
                Spacer(modifier = Modifier.height(8.dp))
                Text("请在实验室设置中配置您的 API Key。")
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "获取 API Key：",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("1. 访问 siliconflow.cn")
                Text("2. 注册/登录账号")
                Text("3. 在控制台获取 API Key")
            }
        },
        confirmButton = {
            TextButton(onClick = onNavigateToSettings) {
                Text("前往配置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        }
    )
}
