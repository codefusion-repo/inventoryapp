package cl.aiep.inventoryapp.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
private fun money(value: Long) = "${'$'}" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CL")).format(value)

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
        ui.message?.let { snack.showSnackbar(it); vm.dismissMessage() }
    }
    LaunchedEffect(ui.userEmail) {
        if (ui.userEmail != null) showLogin = false
    }
    LaunchedEffect(ui.canManage) {
        if (!ui.canManage) { editorVisible = false; deleting = null }
    }

    Scaffold(
        containerColor = Pale,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf(
                    Triple(InventoryDestination.RESUMEN, "Resumen", Icons.Default.Home),
                    Triple(InventoryDestination.PRODUCTOS, "Productos", Icons.Default.Inventory2),
                    Triple(InventoryDestination.CUENTA, "Cuenta", Icons.Default.Person)
                ).forEach { (to, label, icon) ->
                    NavigationBarItem(
                        selected = ui.destination == to,
                        onClick = { vm.navigate(to) },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (ui.destination == InventoryDestination.PRODUCTOS && ui.canManage) {
                ExtendedFloatingActionButton(
                    onClick = { editing = null; editorVisible = true },
                    containerColor = Teal, contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nuevo producto", fontWeight = FontWeight.SemiBold) }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("InventoryApp", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
                    Text("Control de inventario", fontSize = 12.sp, color = Color(0xFF56717C))
                }
                if (ui.userEmail != null) {
                    SuggestionChip(onClick = { vm.navigate(InventoryDestination.CUENTA) },
                        label = { Text(ui.role?.label ?: "Validando rol") })
                } else {
                    TextButton(onClick = { showLogin = true }) { Icon(Icons.Default.Login, null); Spacer(Modifier.width(5.dp)); Text("Ingresar") }
                }
            }
            when (ui.destination) {
                InventoryDestination.RESUMEN -> SummaryContent(ui, onGoProducts = { vm.navigate(InventoryDestination.PRODUCTOS) })
                InventoryDestination.PRODUCTOS -> ProductsContent(ui, vm,
                    onLogin = { showLogin = true },
                    onEdit = { product -> editing = product; editorVisible = true },
                    onDelete = { product -> deleting = product }, onDetail = { detail = it })
                InventoryDestination.CUENTA -> AccountContent(ui, vm, onLogin = { showLogin = true })
            }
        }
    }

    if (showLogin) LoginDialog(
        onDismiss = { showLogin = false }, onLogin = vm::signIn,
        busy = ui.busy, configured = ui.firebaseConfigured,
        diagnostic = ui.firebaseDiagnostic, error = ui.authError
    )

    if (editorVisible && ui.canManage) {
        ProductEditor(product = editing, onDismiss = { editorVisible = false },
            onSave = { name, description, category, supplier, qty, price ->
                vm.save(editing?.id, name, description, category, supplier, qty, price)
                editorVisible = false
            })
    }

    detail?.let { product ->
        AlertDialog(
            onDismissRequest = { detail = null }, title = { Text(product.nombre) },
            text = { Text("Descripción: ${product.descripcion}\nCategoría: ${product.categoria}\nProveedor: ${product.proveedor}\nStock: ${product.cantidad}\nPrecio: ${money(product.precioClp)}") },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Cerrar") } }
        )
    }

    deleting?.takeIf { ui.canManage }?.let { product ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Dar de baja producto") },
            text = { Text("¿Dar de baja ${product.nombre}? La operación se replicará a Firestore cuando haya conexión.") },
            confirmButton = { TextButton(onClick = { vm.delete(product); deleting = null }) { Text("Dar de baja") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SummaryContent(ui: InventoryUiState, onGoProducts: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 25.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { HeroPanel("Vista general", "Existencias, alertas y estado de inventario en un solo lugar.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Productos", ui.products.size.toString(), Modifier.weight(1f))
                Metric("Unidades", ui.stockUnits.toString(), Modifier.weight(1f))
                Metric("Bajo stock", ui.lowStock.toString(), Modifier.weight(1f))
            }
        }
        item { Text("Atención requerida", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Navy) }
        val lowStock = ui.products.filter { it.cantidad < 5 }.take(5)
        if (lowStock.isEmpty()) {
            item { Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                Text("No hay productos con stock bajo.", Modifier.padding(18.dp), color = Ink)
            } }
        } else {
            items(lowStock, key = { it.id }) { product ->
                Surface(shape = RoundedCornerShape(17.dp), color = Color.White) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(product.nombre, fontWeight = FontWeight.SemiBold, color = Navy)
                            Text(product.proveedor, color = Color.Gray, fontSize = 12.sp)
                        }
                        Text("${product.cantidad} uds.", color = Color(0xFFB44A22), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { Button(onClick = onGoProducts, modifier = Modifier.fillMaxWidth()) { Text("Ver inventario") } }
    }
}

@Composable
private fun ProductsContent(
    ui: InventoryUiState, vm: InventoryViewModel,
    onLogin: () -> Unit,
    onEdit: (InventoryProduct) -> Unit,
    onDelete: (InventoryProduct) -> Unit,
    onDetail: (InventoryProduct) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeroPanel("Productos electrónicos", "Busca artículos, consulta disponibilidad y revisa precios actualizados.") }
        item { SyncBanner(ui, onRefresh = vm::syncNow) }
        if (!ui.canManage && ui.userEmail == null) {
            item { OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Ingresar para sincronizar el inventario") } }
        }
        item {
            OutlinedTextField(
                value = ui.search, onValueChange = vm::search,
                label = { Text("Buscar producto o proveedor") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(16.dp)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Inventario", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f))
                Text("${ui.visible.size} productos", fontSize = 12.sp, color = Teal)
            }
        }
        if (ui.products.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Icon(Icons.Default.Inventory2, null, tint = Teal, modifier = Modifier.size(38.dp))
                        Text("Todavía no hay productos", fontWeight = FontWeight.Bold)
                        Text("Almacén puede crear productos y sincronizarlos con el equipo.", color = Color.Gray, fontSize = 12.sp)
                        if (ui.canManage) OutlinedButton(onClick = vm::addDemo) { Text("Añadir 3 productos de ejemplo") }
                    }
                }
            }
        } else if (ui.visible.isEmpty()) {
            item { Text("No se encontraron productos con esos filtros.", color = Color.Gray) }
        } else {
            items(ui.visible, key = { it.id }) { product ->
                ProductCard(product, canManage = ui.canManage, edit = { onEdit(product) },
                    delete = { onDelete(product) }, details = { onDetail(product) })
            }
        }
    }
}

@Composable
private fun AccountContent(ui: InventoryUiState, vm: InventoryViewModel, onLogin: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item { HeroPanel("Cuenta y sincronización", "Consulta tu acceso, permisos y estado de conexión.") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Acceso actual", style = MaterialTheme.typography.titleMedium, color = Navy)
                    Text(ui.userEmail ?: "Sin sesión iniciada", color = Ink)
                    Text("Rol: ${ui.role?.label ?: "No asignado"}", color = Teal, fontWeight = FontWeight.SemiBold)
                    Text(if (ui.canManage) "Puedes gestionar productos y existencias."
                        else if (ui.role != null) "Puedes consultar el inventario. La gestión corresponde al almacén."
                        else "Puedes consultar los datos locales. Para sincronizar, inicia sesión.",
                        style = MaterialTheme.typography.bodySmall)
                    if (ui.userEmail == null) {
                        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
                    } else {
                        OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
                    }
                }
            }
        }
        item { SyncBanner(ui, onRefresh = vm::syncNow) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Estado de Firebase", fontWeight = FontWeight.Bold, color = Navy)
                    Text(ui.firebaseDiagnostic, style = MaterialTheme.typography.bodySmall)
                    Text("La configuración local y la conexión a Firestore son comprobaciones distintas.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun HeroPanel(title: String, subtitle: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Navy)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Navy, Teal))).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(30.dp), tint = Mint)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(subtitle, color = Color(0xFFE7F5F3), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SyncBanner(ui: InventoryUiState, onRefresh: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Mint)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ui.syncStatus.startsWith("Conectado")) Icons.Default.CloudDone else Icons.Default.CloudOff, null, tint = Teal)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.syncStatus, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("${ui.pending} cambios pendientes · ${if (ui.canManage) "Edición habilitada" else "Solo lectura"}",
                    fontSize = 11.sp, color = Teal)
            }
            IconButton(onClick = onRefresh, enabled = ui.canReadCloud) {
                Icon(Icons.Default.Refresh, "Sincronizar", tint = Teal)
            }
        }
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
private fun ProductCard(product: InventoryProduct, canManage: Boolean, edit: () -> Unit, delete: () -> Unit, details: () -> Unit) {
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
                if (canManage) {
                    IconButton(onClick = edit) { Icon(Icons.Default.Edit, contentDescription = "Editar ${product.nombre}", tint = Teal) }
                    IconButton(onClick = delete) { Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar ${product.nombre}", tint = Color(0xFFB42318)) }
                }
            }
        }
    }
}

@Composable
private fun LoginDialog(onDismiss: () -> Unit, onLogin: (String, String) -> Unit,
    busy: Boolean, configured: Boolean, diagnostic: String, error: String?
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Acceso a la nube") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (configured) "Ingresa con una cuenta creada en Firebase Authentication."
                    else "Firebase no está configurado. $diagnostic",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Contraseña") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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

