package cl.aiep.inventoryapp.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.aiep.inventoryapp.domain.ExternalCatalogCategory
import cl.aiep.inventoryapp.domain.ExternalCatalogProduct
import cl.aiep.inventoryapp.presentation.theme.Ink
import cl.aiep.inventoryapp.presentation.theme.Mint
import cl.aiep.inventoryapp.presentation.theme.Navy
import cl.aiep.inventoryapp.presentation.theme.Teal
import java.util.Locale

/** Consulta REST opcional para almacén. Los artículos no se importan sin revisión. */
@Composable
fun ExternalCatalogScreen(
    state: InventoryUiState,
    selectCategory: (ExternalCatalogCategory) -> Unit,
    retry: () -> Unit,
    importProduct: (ExternalCatalogProduct) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Mint)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Catálogo externo", color = Color.White, fontSize = 20.sp,
                            fontWeight = FontWeight.Bold)
                        Text("Consulta productos electrónicos mediante REST y JSON.",
                            color = Color(0xFFE5F4F1), fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Text("Fuente de demostración: DummyJSON · GET /products/category/{category}",
                style = MaterialTheme.typography.bodySmall, color = Ink)
            Text("Los precios externos están en USD. Para importar, debes definir el precio en CLP y las existencias.",
                style = MaterialTheme.typography.bodySmall, color = Teal)
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
                    Icon(Icons.Default.Refresh, contentDescription = "Volver a consultar", tint = Teal)
                }
            }
        }
        if (state.externalLoading) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Consultando el servicio…")
                }
            }
        } else if (state.externalError != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(state.externalError, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = retry) { Text("Reintentar consulta") }
                    }
                }
            }
        } else if (state.externalLoaded && state.externalProducts.isEmpty()) {
            item { Text("No hay artículos disponibles en esta categoría.", color = Ink) }
        } else {
            items(state.externalProducts, key = { it.externalId }) { product ->
                ExternalProductCard(product, onImport = { importProduct(product) })
            }
        }
    }
}

@Composable
private fun ExternalProductCard(product: ExternalCatalogProduct, onImport: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(product.name, fontWeight = FontWeight.Bold, color = Navy)
                    Text(product.supplier, fontSize = 12.sp, color = Teal)
                }
                Spacer(Modifier.width(8.dp))
                Text(String.format(Locale.US, "USD %.2f", product.priceUsd),
                    fontSize = 13.sp, color = Teal, fontWeight = FontWeight.Bold)
            }
            if (product.description.isNotBlank()) Text(product.description,
                style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 3)
            Text("ID externo: ${product.externalId}", fontSize = 11.sp, color = Color.Gray)
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("Preparar incorporación al inventario")
            }
        }
    }
}
