package cl.aiep.inventoryapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.aiep.inventoryapp.presentation.InventoryScreen
import cl.aiep.inventoryapp.presentation.InventoryViewModel
import cl.aiep.inventoryapp.presentation.theme.InventoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // El tema de aplicación configura barras claras y contraste suficiente.
        setContent {
            InventoryTheme {
                Surface(Modifier.fillMaxSize()) {
                    val vm: InventoryViewModel = viewModel()
                    InventoryScreen(vm)
                }
            }
        }
    }
}
