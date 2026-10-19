# InventoryApp

Aplicación Android para administrar inventario de productos electrónicos y explorar un catálogo de tecnología de referencia.

## Funcionalidades

- Catálogo interno, búsqueda, filtros y alertas por stock bajo.
- Invitado: consulta de inventario y productos externos.
- Vendedor: consulta y modificación de cantidades.
- Almacén: alta, edición, baja lógica e incorporación de productos externos.
- Persistencia offline con Room (SQLite), sincronización con Cloud Firestore y acceso con Firebase Authentication.
- Catálogo externo por API REST/JSON, con ficha de producto, imágenes y precios referenciales en CLP.

## Tecnologías

Kotlin, Jetpack Compose, Material 3, Room, Firebase Authentication, Cloud Firestore, Retrofit, Gson, OkHttp y Coil.

## Fuentes e interoperabilidad

- Datos de productos de demostración: [DummyJSON](https://dummyjson.com/docs/products).
- Tipo de cambio de referencia: [mindicador.cl](https://mindicador.cl/api), indicador `dolar`.

La conversión toma el último dólar observado válido (máximo siete días), lo almacena temporalmente y redondea al peso chileno. Si no se obtiene una cotización reciente, el precio se marca como no disponible y almacén puede ingresar un valor en CLP. **La conversión es referencial y no equivale a un precio comercial, costo de importación ni cotización de venta.** La pantalla de consulta no expone los precios originales de la API externa.

Los artículos externos tienen fichas dentro de la aplicación; el proveedor de demostración no suministra páginas comerciales individuales. La incorporación requiere confirmar unidades y precio de venta, y utiliza los repositorios Room/Firestore existentes. Una importación repetida por nombre y categoría se bloquea en la interfaz, pero no existe un identificador externo persistente para deduplicación robusta.

## Configuración

Requiere Android SDK 37, JDK 17 y `app/google-services.json` para `cl.aiep.inventoryapp`. En Firebase Authentication deben habilitarse Email/Password y Anónimo, y en Firestore publicarse las reglas de [`firestore.rules`](firestore.rules). Cada empleado requiere un documento `users/{uid}` con rol `vendedor` o `almacen`, asignado fuera de la app.

```powershell
.\gradlew testDebugUnitTest
.\gradlew assembleDebug
```

## Limitaciones

La sincronización usa marcas de tiempo del cliente para resolver conflictos y no garantiza consistencia transaccional de existencias entre ediciones simultáneas. El catálogo interno es legible por cualquier identidad Firebase autenticada, incluidas las anónimas: no incorporar datos sensibles en sus documentos. El almacenamiento local no aplica cifrado adicional.
