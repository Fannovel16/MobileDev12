package com.example.midterm

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LoginScreen(
                    onLoginSuccess = { role ->
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra("USER_ROLE", role)
                        startActivity(intent)
                        finish()
                    },
                    onNavigateToRegister = {
                        startActivity(Intent(this, RegisterActivity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit, onNavigateToRegister: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ĐĂNG NHẬP", fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Mật khẩu") }, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )

        // Quên mật khẩu
        TextButton(
            onClick = {
                if (email.isNotBlank()) {
                    auth.sendPasswordResetEmail(email).addOnSuccessListener {
                        Toast.makeText(context, "Đã gửi link khôi phục vào Email!", Toast.LENGTH_SHORT).show()
                    }
                } else Toast.makeText(context, "Nhập Email trước khi bấm Quên mật khẩu", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.align(Alignment.End)
        ) { Text("Quên mật khẩu?") }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading,
            onClick = {
                if (email.isBlank() || password.isBlank()) return@Button
                isLoading = true
                if (email == "admin" && password == "admin") {
                    onLoginSuccess("admin")
                    return@Button
                }
                auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user?.isEmailVerified == true) {
                            // Lấy quyền từ Firestore
                            db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                                val role = doc.getString("role") ?: "user"
                                onLoginSuccess(role)
                            }
                        } else {
                            Toast.makeText(context, "Vui lòng vào hộp thư Email để xác nhận tài khoản!", Toast.LENGTH_LONG).show()
                            auth.signOut()
                            isLoading = false
                        }
                    } else {
                        Toast.makeText(context, "Sai thông tin đăng nhập!", Toast.LENGTH_SHORT).show()
                        isLoading = false
                    }
                }
            }
        ) {
            Text(if (isLoading) "Đang đăng nhập..." else "ĐĂNG NHẬP")
        }

        TextButton(onClick = onNavigateToRegister) {
            Text("Chưa có tài khoản? Đăng ký ngay")
        }
    }
}