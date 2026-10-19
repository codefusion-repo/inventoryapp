package cl.aiep.inventoryapp.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.aiep.inventoryapp.domain.ExternalCatalogCategory
import cl.aiep.inventoryapp.domain.ExternalCatalogProduct
import cl.aiep.inventoryapp.presentation.theme.Ink
import cl.aiep.inventoryapp.presentation.theme.Mint
import cl.aiep.inventoryapp.presentation.theme.Navy
import cl.aiep.inventoryapp.presentation.theme.Teal
import coil.compose.AsyncImage

private fun priceClp(value: Long?): String = value?.let(::formatClp) ?: "Precio no disponible"

/** Explorar es público; solo almacén puede confirmar un alta en el inventario interno. */
@Composable
fun ExternalCatalogScreen(
    state: InventoryUiState,
    selectCategory: (ExternalCatalogCategory) -> Unit,
    retry: () -> Unit,
    importProduct: (ExternalCatalogProduct) -> Unit
) {
    var detail by remember { mutableStateOf<ExternalCatalogProduct?>(null) }
    LaunchedEffect(state.externalCategory) { detail = null }

    detail?.let { selected ->
        ExternalProductDetail(
            selected, canManage = state.canManage,
            onBack = { detail = null },
            onImport = { importProduct(selected) }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Mint)
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Explora productos", color = Color.White, fontSize = 20.sp,
                            fontWeight = FontWeight.Bold)
                        Text("Descubre tecnología y revisa precios referenciales en pesos chilenos.",
                            color = Color(0xFFE5F4F1), fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExternalCatalogCategory.entries) { category ->
                    FilterChip(
                        selected = state.externalCategory == category,
                        onClick = { selectCategory(category) },
                        label = { Text(category.label) }
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Productos disponibles", fontWeight = FontWeight.Bold, color = Navy,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = retry, enabled = !state.externalLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar productos", tint = Teal)
                }
            }
        }
        if (state.externalLoading) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 30.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Buscando productos…")
                }
            }
        } else if (state.externalError != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(state.externalError, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = retry) { Text("Volver a intentar") }
                    }
                }
            }
        } else if (state.externalLoaded && state.externalProducts.isEmpty()) {
            item { Text("Por ahora no hay productos en esta categoría.", color = Ink) }
        } else {
            items(state.externalProducts, key = { it.externalId }) { product ->
                ExternalProductCard(
                    product = product, canManage = state.canManage,
                    onDetail = { detail = product },
                    onImport = { importProduct(product) }
                )
            }
        }
    }
}

@Composable
private fun ProductPhoto(product: ExternalCatalogProduct, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(15.dp)).background(Mint), contentAlignment = Alignment.Center) {
        if (product.thumbnailUrl != null) {
            AsyncImage(
                model = product.thumbnailUrl, contentDescription = "Imagen de ${product.name}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp)
            )
        } else {
            Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Teal,
                modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun ExternalProductCard(product: ExternalCatalogProduct, canManage: Boolean,
    onDetail: () -> Unit, onImport: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                ProductPhoto(product, Modifier.size(88.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(product.name, fontWeight = FontWeight.Bold, color = Navy,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(product.supplier, fontSize = 12.sp, color = Teal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(priceClp(product.priceClp), fontSize = 16.sp,
                        color = Teal, fontWeight = FontWeight.Bold)
                    if (product.priceClp != null) Text("Precio referencial · CLP", fontSize = 10.sp,
                        color = Color(0xFF657983))
                }
            }
            if (canManage) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDetail, modifier = Modifier.weight(1f)) {
                        Text("Ver ficha", maxLines = 1)
                    }
                    Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                        Text("Incorporar", maxLines = 1)
                    }
                }
            } else {
                Button(onClick = onDetail, modifier = Modifier.fillMaxWidth()) {
                    Text("Ver producto")
                }
            }
        }
    }
}

@Composable
private fun ExternalProductDetail(product: ExternalCatalogProduct, canManage: Boolean,
    onBack: () -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        TextButton(onClick = onBack) {
            Icon(
    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription = null
)
            Spacer(Modifier.width(6.dp))
            Text("Volver a productos")
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ProductPhoto(product, Modifier.fillMaxWidth().height(200.dp))
                Text(product.name, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text(product.supplier, color = Teal)
                Text(priceClp(product.priceClp), fontSize = 24.sp,
                    fontWeight = FontWeight.Bold, color = Teal)
                if (product.priceClp != null) Text("Precio estimado en CLP, sujeto a variaciones.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                HorizontalDivider()
                Text("Descripción", fontWeight = FontWeight.SemiBold, color = Navy)
                Text(product.description.ifBlank { "No hay una descripción disponible para este producto." },
                    color = Ink)
                if (canManage) {
                    Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                        Text("Incorporar al inventario")
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
