package com.hntek.android.opencv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hntek.android.opencv.ui.FaceComparisonScreen
import com.hntek.android.opencv.ui.theme.OpenCVTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenCVTheme {
                val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_MEDIA_IMAGES
                    )
                } else {
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { // 当函数的最后一个参数是 Lambda 表达式时，可以将 Lambda 写在圆括号外面
                    permissions ->
                    val allGranted = permissions.values.all { it } // true && true && false == false
                    if (allGranted) {
                        Log.i("permission", "all accepted");
                    } else {
                        Log.i("permission", "not all accepted");
                    }
                }

                val hasAllPermissions = requiredPermissions.all { permission ->
                    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
                }

                if (hasAllPermissions) {
                    FaceComparisonScreen()
                } else {
                    PermissionRequestScreen(
                        onRequestPermissions = {
                            // contract = ActivityResultContracts.RequestMultiplePermissions()，launch 的是系统权限对话框
                            // 传入参数定义：
                            // class RequestMultiplePermissions :
                            //        ActivityResultContract<Array<String>, Map<String, @JvmSuppressWildcards Boolean>>() {
                            //            internal fun createIntent(input: Array<String>): Intent {
                            //                return Intent(ACTION_REQUEST_PERMISSIONS).putExtra(EXTRA_PERMISSIONS, input)
                            //            }
                            //        }
                            permissionLauncher.launch(requiredPermissions)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermissions: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("需要权限来使用相机和访问图片")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermissions) {
                Text("请求权限")
            }
        }
    }
}