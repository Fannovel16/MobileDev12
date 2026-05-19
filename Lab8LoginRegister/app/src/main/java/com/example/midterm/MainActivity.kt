package com.example.midterm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

// ==========================================
// 1. DATA MODEL
// ==========================================
data class Product(
    var id: String = "",
    var tenSp: String = "",
    var loaiSp: String = "",
    var gia: Long = 0,
    var imageUrl: String = ""
)

// ==========================================
// 2. HÀM HỖ TRỢ LẤY TÊN FILE TỪ URI
// ==========================================
fun getFileNameFromUri(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result ?: "Tên file không xác định"
}

// ==========================================
// 3. ACTIVITY CHÍNH
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nhận quyền (role) từ màn hình Login truyền sang
        val userRole = intent.getStringExtra("USER_ROLE") ?: "user"
        val isAdmin = (userRole == "admin")

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        isAdmin = isAdmin,
                        onLogout = {
                            FirebaseAuth.getInstance().signOut()
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// 4. GIAO DIỆN CHÍNH (COMPOSE)
// ==========================================
@Composable
fun MainScreen(isAdmin: Boolean, onLogout: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val context = LocalContext.current

    // Các biến trạng thái quản lý dữ liệu trên màn hình
    var productList by remember { mutableStateOf(listOf<Product>()) }
    var ten by remember { mutableStateOf("") }
    var loai by remember { mutableStateOf("") }
    var gia by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Chưa chọn ảnh") } // Biến lưu tên file
    var isEditMode by remember { mutableStateOf(false) }
    var currentEditId by remember { mutableStateOf("") }
    var currentImageUrl by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) } // Chống bấm nhiều lần khi đang upload

    // Trình chọn ảnh
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
        if (uri != null) {
            fileName = getFileNameFromUri(context, uri) // Dịch URI thành tên file thật
        } else {
            fileName = "Chưa chọn ảnh"
        }
    }

    // Lắng nghe dữ liệu realtime từ Firestore
    LaunchedEffect(Unit) {
        db.collection("products").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                productList = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Product::class.java)?.apply { id = doc.id }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // Header: Tiêu đề + Nút Đăng xuất
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isAdmin) "Trang Admin" else "Trang Người Dùng",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            TextButton(onClick = onLogout) { Text("Đăng xuất") }
        }

        Divider(modifier = Modifier.padding(bottom = 8.dp))

        // ==========================================
        // PHẦN FORM NHẬP LIỆU (CHỈ HIỆN VỚI ADMIN)
        // ==========================================
        if (isAdmin) {
            OutlinedTextField(value = ten, onValueChange = { ten = it }, label = { Text("Tên sản phẩm") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = loai, onValueChange = { loai = it }, label = { Text("Loại sản phẩm") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = gia, onValueChange = { gia = it }, label = { Text("Giá tiền (VNĐ)") }, modifier = Modifier.fillMaxWidth())

            // Nút chọn ảnh và Tên file
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Button(onClick = { imagePicker.launch("image/*") }) { Text("Chọn ảnh") }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = fileName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis, // Tên dài quá sẽ hiện "..."
                    modifier = Modifier.weight(1f)
                )
            }

            // Nút Thêm / Cập nhật
            Button(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isUploading,
                onClick = {
                    if (ten.isBlank() || loai.isBlank() || gia.isBlank()) {
                        Toast.makeText(context, "Vui lòng nhập đủ thông tin!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isUploading = true

                    // Hàm phụ: Lưu Data vào Firestore sau khi xử lý ảnh
                    val saveToFirestore = { url: String ->
                        val p = Product(if(isEditMode) currentEditId else "", ten, loai, gia.toLong(), url)
                        if (isEditMode) {
                            db.collection("products").document(currentEditId).set(p)
                            Toast.makeText(context, "Cập nhật thành công!", Toast.LENGTH_SHORT).show()
                        } else {
                            db.collection("products").add(p)
                            Toast.makeText(context, "Thêm thành công!", Toast.LENGTH_SHORT).show()
                        }

                        // Reset form sau khi lưu xong
                        ten = ""; loai = ""; gia = ""; imageUri = null; isEditMode = false
                        fileName = "Chưa chọn ảnh"
                        isUploading = false
                    }

                    // Xử lý upload ảnh
                    if (imageUri != null) {
                        Toast.makeText(context, "Đang tải ảnh lên...", Toast.LENGTH_SHORT).show()
                        val ref = storage.reference.child("products/${UUID.randomUUID()}.jpg")
                        ref.putFile(imageUri!!).addOnSuccessListener {
                            ref.downloadUrl.addOnSuccessListener { uri -> saveToFirestore(uri.toString()) }
                        }.addOnFailureListener {
                            Toast.makeText(context, "Lỗi upload ảnh", Toast.LENGTH_SHORT).show()
                            isUploading = false
                        }
                    } else if (isEditMode) {
                        saveToFirestore(currentImageUrl) // Chế độ sửa: Nếu không chọn ảnh mới thì giữ link ảnh cũ
                    } else {
                        Toast.makeText(context, "Vui lòng chọn ảnh!", Toast.LENGTH_SHORT).show()
                        isUploading = false
                    }
                }
            ) {
                Text(if (isUploading) "Đang xử lý..." else if (isEditMode) "CẬP NHẬT SẢN PHẨM" else "THÊM SẢN PHẨM")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ==========================================
        // PHẦN HIỂN THỊ DANH SÁCH (CẢ ADMIN & USER ĐỀU THẤY)
        // ==========================================
        Text("Danh sách sản phẩm", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(productList) { product ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {

                        // Ảnh SP (Dùng Coil Load ảnh từ URL)
                        AsyncImage(
                            model = product.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            contentScale = ContentScale.Crop
                        )

                        // Thông tin SP
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(product.tenSp, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Loại: ${product.loaiSp}", fontSize = 14.sp)
                            Text("Giá: ${product.gia} đ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }

                        // Nút Sửa/Xóa (CHỈ HIỆN VỚI ADMIN)
                        if (isAdmin) {
                            Column {
                                IconButton(onClick = {
                                    // Đổ dữ liệu ngược lên Form
                                    ten = product.tenSp; loai = product.loaiSp; gia = product.gia.toString()
                                    currentImageUrl = product.imageUrl; currentEditId = product.id
                                    isEditMode = true; fileName = "Giữ ảnh cũ hoặc chọn mới"
                                    imageUri = null
                                }) { Icon(Icons.Default.Edit, contentDescription = "Sửa") }

                                IconButton(onClick = {
                                    db.collection("products").document(product.id).delete()
                                    Toast.makeText(context, "Đã xóa sản phẩm", Toast.LENGTH_SHORT).show()
                                }) { Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}