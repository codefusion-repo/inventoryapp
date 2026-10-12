# InventoryApp

Aplicación Android para gestionar inventario de productos electrónicos, con operación offline y sincronización a Cloud Firestore.

## Funciones

- Inventario consultable con búsqueda, filtros y alertas de stock bajo.
- Creación, edición y baja lógica de productos desde el perfil de almacén.
- Acceso de solo lectura para vendedores.
- Persistencia local con Room (SQLite) y sincronización con Firestore.
- Resumen de existencias, cambios pendientes y estado de sincronización.

## Stack

Kotlin · Jetpack Compose · Material 3 · Room · Firebase Authentication · Cloud Firestore · Corrutinas/Flow.

## Accesos

| Rol | Consultar | Crear/editar/dar de baja |
|---|---|---|
| Vendedor | Sí | No |
| Encargado de almacén | Sí | Sí |

Los roles provienen de `users/{uid}` en Firestore y están protegidos por [`firestore.rules`](firestore.rules). No se asignan desde el cliente.

## Configuración

Se requiere Android SDK 37, JDK 17 y `app/google-services.json` correspondiente al `applicationId` `cl.aiep.inventoryapp` (archivo excluido de Git). Habilita **Email/Password** en Firebase Authentication y publica las reglas incluidas.

Después de crear cada cuenta en Firebase Authentication, añade manualmente un documento `users/{uid}` con el campo `role` igual a `vendedor` o `almacen`. Estos documentos no pueden crearse desde la app.

```powershell
.\gradlew testDebugUnitTest
.\gradlew assembleDebug
```

La app puede consultar datos locales sin sesión. La edición y sincronización requieren una cuenta de almacén con perfil reconocido. Un rol obtenido previamente desde la caché de Firestore puede permitir editar sin red; todas las escrituras remotas son verificadas nuevamente por las reglas del servidor.

**Limitaciones:** el prototipo utiliza marcas de tiempo del dispositivo para resolver conflictos (no garantiza consistencia transaccional entre ediciones simultáneas). No incluye auditoría, permisos por sucursal ni conciliación de stock. La base local debe considerarse información interna y no está cifrada mediante una capa adicional de la app.
