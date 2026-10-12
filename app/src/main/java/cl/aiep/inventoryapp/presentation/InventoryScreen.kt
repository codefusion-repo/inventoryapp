package cl.aiep.inventoryapp.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.aiep.inventoryapp.domain.InventoryProduct
import cl.aiep.inventoryapp.domain.InventoryValidation
import cl.aiep.inventoryapp.presentation.theme.*
import java.text.NumberFormat
import java.util.Locale

private val categories = listOf("Todos", "Computadores", "Accesorios", "Audio", "Otros")
private fun money(value: Long): String = "${'$'}" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CL")).format(value)

@Composable
fun InventoryScreen(vm: InventoryViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showLogin by remember { mutableStateOf(false) }
    var editorVisible by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<InventoryProduct?>(null) }
    var deleting by remember { mutableStateOf<InventoryProduct?>(null) }
    var detail by remember { mutableStateOf<InventoryProduct?>(null) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        val msg = ui.message ?: return@LaunchedEffect
        snack.showSnackbar(msg)
        vm.dismissMessage()
    }

    Scaffold(
        containerColor = Pale,
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; editorVisible = true },
                containerColor = Teal, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo producto", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("InventoryApp", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
                        Text("Inventario de productos electrónicos", fontSize = 12.sp, color = Color(0xFF5F7781))
                    }
                    FilledTonalIconButton(onClick = { if (ui.userEmail != null) vm.syncNow() else showLogin = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sincronizar")
                    }
                    Spacer(Modifier.width(6.dp))
                    FilledTonalIconButton(onClick = { if (ui.userEmail == null) showLogin = true else vm.logout() }) {
                        Icon(if (ui.userEmail == null) Icons.Default.Login else Icons.Default.Logout,
                            contentDescription = if (ui.userEmail == null) "Iniciar sesión" else "Cerrar sesión")
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Navy)
                ) {
                    Column(Modifier.fillMaxWidth().background(
                        Brush.linearGradient(listOf(Navy, Teal))
                    ).padding(22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(9.dp))
                            Text("Control de inventario", color = Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Text("Controla existencias, precios y proveedores desde un solo lugar.",
                            color = Color(0xFFDAF4F3), fontSize = 13.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(if (ui.userEmail != null) "Cuenta: ${ui.userEmail}" else "Modo local · inicia sesión para sincronizar",
                            color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    Metric("Productos", ui.products.size.toString(), Modifier.weight(1f))
                    Metric("Unidades", ui.stockUnits.toString(), Modifier.weight(1f))
                    Metric("Bajo stock", ui.lowStock.toString(), Modifier.weight(1f))
                }
            }
            item {
                Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Mint)) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ui.userEmail != null) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null, tint = Teal)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ui.syncStatus, style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold)
                            Text("${ui.pending} cambio(s) pendientes · ${if (ui.firebaseConfigured) "Firebase configurado" else "Firebase por configurar"}",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = ui.search, onValueChange = vm::search,
                    label = { Text("Buscar por nombre, proveedor o categoría") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(15.dp)
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { category ->
                        FilterChip(selected = ui.category == category,
                            onClick = { vm.category(category) }, label = { Text(category) })
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Productos", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Navy,
                        modifier = Modifier.weight(1f))
                    Text("${ui.visible.size} resultados", color = Teal, fontSize = 12.sp)
                }
            }
            if (ui.products.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(23.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = Teal, modifier = Modifier.size(38.dp))
                            Text("Aún no hay productos", fontWeight = FontWeight.Bold)
                            Text("Crea el primer producto o carga información de ejemplo para comenzar.",
                                style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = vm::addDemo) { Text("Cargar 3 productos de ejemplo") }
                        }
                    }
                }
            } else if (ui.visible.isEmpty()) {
                item { Text("No se encontraron productos con esos filtros.", color = Color.Gray) }
            } else {
                items(ui.visible, key = { it.id }) { product ->
                    ProductCard(product, edit = { editing = product; editorVisible = true },
                        delete = { deleting = product }, details = { detail = product })
                }
            }
        }
    }

    if (showLogin) LoginDialog(onDismiss = { showLogin = false }, onLogin = { email, pass ->
        vm.signIn(email, pass)
        showLogin = false
    }, busy = ui.busy, configured = ui.firebaseConfigured)

    if (editorVisible) ProductEditor(product = editing, onDismiss = { editorVisible = false },
        onSave = { name, description, category, supplier, qty, price ->
            vm.save(editing?.id, name, description, category, supplier, qty, price)
            editorVisible = false
        })

    detail?.let { product ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(product.nombre) },
            text = { Text("Descripción: ${product.descripcion}\nCategoría: ${product.categoria}\nProveedor: ${product.proveedor}\nStock: ${product.cantidad}\nPrecio: ${money(product.precioClp)}") },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Cerrar") } }
        )
    }

    deleting?.let { product ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("Dar de baja producto") },
            text = { Text("¿Eliminar ${product.nombre}? La baja se conservará para sincronizarse con Firestore.") },
            confirmButton = { TextButton(onClick = { vm.delete(product); deleting = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Teal)
            Text(label, fontSize = 11.sp, color = Color(0xFF607680))
        }
    }
}

@Composable
private fun ProductCard(product: InventoryProduct, edit: () -> Unit, delete: () -> Unit, details: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(47.dp).clip(RoundedCornerShape(14.dp)).background(Mint),
                    contentAlignment = Alignment.Center) {
                    Text(product.nombre.take(2).uppercase(), color = Teal, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(product.nombre, fontWeight = FontWeight.Bold, color = Navy,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${product.categoria} · ${product.proveedor}", fontSize = 11.sp,
                        color = Color(0xFF637984))
                }
                Text(money(product.precioClp), fontWeight = FontWeight.Bold, color = Teal, fontSize = 14.sp)
            }
            if (product.descripcion.isNotBlank()) {
                Text(product.descripcion, fontSize = 12.sp, color = Color(0xFF61737B))
            }
            HorizontalDivider(color = Border)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (product.cantidad < 5) Color(0xFFFFE6D9) else Mint,
                    shape = RoundedCornerShape(20.dp)) {
                    Text("${product.cantidad} unidades", modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        color = if (product.cantidad < 5) Color(0xFF9A3412) else Teal, fontSize = 12.sp)
                }
                if (product.pendingSync) {
                    Spacer(Modifier.width(8.dp))
                    Text("Pendiente", color = Color(0xFFB66B1B), fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = details) { Text("Ver detalle") }
                IconButton(onClick = edit) { Icon(Icons.Default.Edit, contentDescription = "Editar ${product.nombre}", tint = Teal) }
                IconButton(onClick = delete) { Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar ${product.nombre}", tint = Color(0xFFB42318)) }
            }
        }
    }
}

@Composable
private fun LoginDialog(onDismiss: () -> Unit, onLogin: (String, String) -> Unit,
    busy: Boolean, configured: Boolean
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Acceso a la nube") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (configured) "Ingresa con una cuenta creada en Firebase Authentication."
                    else "Firebase aún no está configurado. Puedes seguir usando el inventario local.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Contraseña") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onLogin(email, password) }, enabled = configured && !busy &&
                email.isNotBlank() && password.isNotBlank()) { Text("Conectar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Continuar sin nube") } })
}

@Composable
private fun ProductEditor(product: InventoryProduct?, onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit
) {
    var name by remember(product?.id) { mutableStateOf(product?.nombre ?: "") }
    var description by remember(product?.id) { mutableStateOf(product?.descripcion ?: "") }
    var supplier by remember(product?.id) { mutableStateOf(product?.proveedor ?: "") }
    var category by remember(product?.id) { mutableStateOf(product?.categoria ?: "Accesorios") }
    var quantity by remember(product?.id) { mutableStateOf(product?.cantidad?.toString() ?: "0") }
    var price by remember(product?.id) { mutableStateOf(product?.precioClp?.toString() ?: "0") }
    val error = if (quantity.toIntOrNull() == null || price.toLongOrNull() == null)
        "Cantidad y precio deben ser números enteros."
    else InventoryValidation.validate(name, supplier, quantity.toInt(), price.toLong())

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Nuevo producto" else "Editar producto") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 410.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 2)
                OutlinedTextField(supplier, { supplier = it }, label = { Text("Proveedor") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Categoría", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories.drop(1)) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(quantity, { quantity = it }, label = { Text("Cantidad") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(price, { price = it }, label = { Text("Precio CLP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true)
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(enabled = error == null, onClick = { onSave(name, description, category, supplier, quantity, price) }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

