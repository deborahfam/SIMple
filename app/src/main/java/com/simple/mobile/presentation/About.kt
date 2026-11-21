package com.simple.mobile.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun AboutCard() {
    val context = LocalContext.current
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No hay un navegador disponible.", Toast.LENGTH_SHORT).show()
        }
    }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Acerca de SIMple", style = MaterialTheme.typography.titleLarge)
            Text("Versión ${com.simple.mobile.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Text("Creado por deborahfam")
            Text("© 2026 deborahfam. Licencia Apache 2.0.", style = MaterialTheme.typography.bodySmall)
            Text("Proyecto independiente, sin afiliación con ETECSA, Cubacel ni los bancos.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { open("https://github.com/deborahfam") }) { Text("Perfil de GitHub · deborahfam") }
            OutlinedButton(onClick = { open("https://github.com/deborahfam/SIMple") }) { Text("Código fuente y licencia") }
            LegalDocumentButton("licenses", "Licencias de código abierto")
        }
    }
}

@Composable internal fun PrivacyInformation() {
    LegalDocumentButton("privacy", "Política de privacidad · sin conexión")
    DetailDisclosure("Privacidad y uso de tus datos") {
        Text("SIMple procesa las consultas en tu teléfono. La edición directa también permite importar SMS autorizados. Guarda lecturas, destinatarios e información de recargas cifrados. El PIN bancario solo se guarda si lo solicitas; para guardarlo o cargarlo se solicita el bloqueo del dispositivo. Los PIN guardados con versiones anteriores deben reemplazarse.")
        Text("No incluye anuncios, cuentas ni telemetría. Las consultas y operaciones telefónicas se envían al operador o banco correspondiente. En la edición directa, las consultas de actualización usan Internet; los enlaces externos se abren en el navegador: GitHub o el sitio abierto reciben tu dirección IP y datos técnicos de la conexión, pero la app no les envía tus saldos, SMS ni PIN.")
        Text("Puedes revocar permisos en Android y eliminar los datos de las líneas desde Ajustes. Para borrar todos los datos y preferencias de SIMple, usa Borrar almacenamiento en Android. Las copias de seguridad de la app están desactivadas.")
        Text("Para contactar con el creador, visita su perfil de GitHub. No publiques PIN, números de teléfono ni capturas con datos personales en incidencias públicas.")
    }
}

@Composable private fun LegalDocumentButton(document: String, label: String) {
    val context = LocalContext.current
    var legal by remember { mutableStateOf<String?>(null) }
    legal?.let { name ->
        val content = remember(name) { context.assets.open("legal/$name.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { legal = null },
            title = { Text(if (name == "privacy") "Política de privacidad" else "Licencias de código abierto") },
            text = { Text(content, Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { legal = null }) { Text("Cerrar") } })
    }
    OutlinedButton(onClick = { legal = document }) { Text(label) }
}
