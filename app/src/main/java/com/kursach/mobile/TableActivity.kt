package com.kursach.mobile

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.kursach.mobile.api.ApiClient
import com.kursach.mobile.api.ApiService
import com.kursach.mobile.api.AssignComponentRequest
import com.kursach.mobile.api.ComponentIdRequest
import com.kursach.mobile.api.ComponentRequest
import com.kursach.mobile.api.DashboardComponent
import com.kursach.mobile.api.DashboardResponse
import com.kursach.mobile.api.MessageResponse
import com.kursach.mobile.api.UpdateComponentRequest
import com.kursach.mobile.ui.ComponentAdapter
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TableActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_USERNAME = "extra_username"
    }

    private lateinit var statusText: androidx.appcompat.widget.AppCompatTextView
    private lateinit var subtitleText: androidx.appcompat.widget.AppCompatTextView
    private lateinit var adapter: ComponentAdapter

    private val api by lazy { ApiClient.create(ApiService::class.java) }

    private var dashboard: DashboardResponse? = null
    private var selectedItem: DashboardComponent? = null
    private var pendingSerialInput: TextInputEditText? = null

    private val componentTypes = listOf("контролер", "датчик", "модуль")
    private val componentStatuses = listOf("вільне", "ремонт")

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val serial = result.contents?.trim().orEmpty()
        if (serial.isNotEmpty()) {
            pendingSerialInput?.setText(serial)
            toast("Serial scanned")
        }
        pendingSerialInput = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_table)

        statusText = findViewById(R.id.statusText)
        subtitleText = findViewById(R.id.subtitleText)

        adapter = ComponentAdapter { item ->
            selectedItem = item
            adapter.setSelectedId(item.id)
        }

        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.componentList).apply {
            layoutManager = LinearLayoutManager(this@TableActivity)
            adapter = this@TableActivity.adapter
        }

        findViewById<MaterialButton>(R.id.refreshButton).setOnClickListener { loadDashboard() }
        findViewById<MaterialButton>(R.id.addButton).setOnClickListener { showComponentDialog() }
        findViewById<MaterialButton>(R.id.editButton).setOnClickListener { selectedItem?.let { showComponentDialog(it) } ?: toast("Select a component") }
        findViewById<MaterialButton>(R.id.removeButton).setOnClickListener { selectedItem?.let { confirmRemove(it) } ?: toast("Select a component") }
        findViewById<MaterialButton>(R.id.assignButton).setOnClickListener { selectedItem?.let { showAssignDialog(it) } ?: toast("Select a component") }
        findViewById<MaterialButton>(R.id.logoutButton).setOnClickListener { logout() }

        subtitleText.text = intent.getStringExtra(EXTRA_USERNAME)?.let { "Signed in as $it" } ?: "Signed in"
        loadDashboard()
    }

    private fun loadDashboard() {
        setStatus("Loading dashboard...")
        api.dashboard().enqueue(object : Callback<DashboardResponse> {
            override fun onResponse(call: Call<DashboardResponse>, response: Response<DashboardResponse>) {
                if (!response.isSuccessful || response.body() == null) {
                    if (response.code() == 401) {
                        goBackToLogin("Session expired. Log in again.")
                        return
                    }
                    setStatus("Dashboard error (${response.code()})")
                    return
                }

                dashboard = response.body()
                val currentUser = dashboard!!.user
                subtitleText.text = "${currentUser.username} · ${currentUser.role}"
                adapter.submitList(dashboard!!.items, dashboard!!.assignmentByEquipmentId)
                adapter.setSelectedId(selectedItem?.id)
                setActionState(currentUser.role == "admin")
                setStatus("Loaded ${dashboard!!.items.size} components")
            }

            override fun onFailure(call: Call<DashboardResponse>, t: Throwable) {
                setStatus(t.message ?: "Failed to load dashboard")
            }
        })
    }

    private fun setActionState(enabled: Boolean) {
        findViewById<MaterialButton>(R.id.addButton).isEnabled = enabled
        findViewById<MaterialButton>(R.id.editButton).isEnabled = enabled
        findViewById<MaterialButton>(R.id.removeButton).isEnabled = enabled
        findViewById<MaterialButton>(R.id.assignButton).isEnabled = enabled
    }

    private fun showComponentDialog(item: DashboardComponent? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_component_form, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.componentNameInput)
        val typeInput = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.componentTypeInput)
        val serialInput = dialogView.findViewById<TextInputEditText>(R.id.componentSerialInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.componentDescriptionInput)
        val statusInput = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.componentStatusInput)
        val scanButton = dialogView.findViewById<MaterialButton>(R.id.scanSerialButton)

        typeInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, componentTypes))
        statusInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, componentStatuses))

        if (item != null) {
            nameInput.setText(item.name)
            typeInput.setText(item.type, false)
            serialInput.setText(item.serial)
            descriptionInput.setText(item.description.orEmpty())
            statusInput.setText(item.status, false)
        } else {
            typeInput.setText(componentTypes.first(), false)
            statusInput.setText("вільне", false)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (item == null) "Add component" else "Edit component")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        scanButton.setOnClickListener {
            pendingSerialInput = serialInput
            launchScanner()
        }

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val type = typeInput.text?.toString()?.trim().orEmpty()
                val serial = serialInput.text?.toString()?.trim().orEmpty()
                val description = descriptionInput.text?.toString()?.trim().orEmpty()
                val status = statusInput.text?.toString()?.trim().orEmpty().ifBlank { "вільне" }

                if (name.isBlank() || type.isBlank() || serial.isBlank()) {
                    toast("Name, type, and serial are required")
                    return@setOnClickListener
                }

                if (!componentTypes.contains(type)) {
                    toast("Select a valid component type")
                    return@setOnClickListener
                }

                if (status == "призначене") {
                    toast("Use Assign to set assigned status")
                    return@setOnClickListener
                }

                if (!componentStatuses.contains(status)) {
                    toast("Select a valid status")
                    return@setOnClickListener
                }

                if (item == null) {
                    api.addComponent(ComponentRequest(name, type, serial, description, status))
                        .enqueue(componentCallback("Component added"))
                } else {
                    api.updateComponent(UpdateComponentRequest(item.id, name, type, serial, status, description))
                        .enqueue(componentCallback("Component updated"))
                }

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showAssignDialog(item: DashboardComponent) {
        val users = dashboard?.users?.filter { it.role != "admin" }.orEmpty()
        val usernames = users.map { it.username }
        val userByName = users.associateBy { it.username }

        val dialogView = layoutInflater.inflate(R.layout.dialog_assign_component, null)
        val userInput = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.userInput)
        userInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, usernames))

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Assign component")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Assign", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedUsername = userInput.text?.toString()?.trim().orEmpty()
                val user = userByName[selectedUsername]

                if (user == null) {
                    toast("Select a valid non-admin user")
                    return@setOnClickListener
                }

                api.assignComponent(AssignComponentRequest(item.id, user.id))
                    .enqueue(componentCallback("Component assigned"))
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun confirmRemove(item: DashboardComponent) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove component")
            .setMessage("Remove ${item.name} (${item.serial})?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                api.removeComponent(ComponentIdRequest(item.id)).enqueue(componentCallback("Component removed"))
            }
            .show()
    }

    private fun launchScanner() {
        val options = ScanOptions().apply {
            setCaptureActivity(PortraitCaptureActivity::class.java)
            setDesiredBarcodeFormats(
                ScanOptions.QR_CODE,
                ScanOptions.CODE_128,
                ScanOptions.CODE_39,
                ScanOptions.EAN_13,
                ScanOptions.EAN_8,
                ScanOptions.UPC_A,
                ScanOptions.UPC_E,
                ScanOptions.ITF,
                ScanOptions.DATA_MATRIX
            )
            setPrompt("Scan serial number")
            setBeepEnabled(true)
            setOrientationLocked(true)
        }
        scanLauncher.launch(options)
    }

    private fun logout() {
        api.logout().enqueue(object : Callback<MessageResponse> {
            override fun onResponse(call: Call<MessageResponse>, response: Response<MessageResponse>) {
                ApiClient.clearSession()
                goBackToLogin("Logged out")
            }

            override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
                ApiClient.clearSession()
                goBackToLogin(t.message ?: "Logged out")
            }
        })
    }

    private fun componentCallback(successMessage: String) = object : Callback<MessageResponse> {
        override fun onResponse(call: Call<MessageResponse>, response: Response<MessageResponse>) {
            if (response.isSuccessful) {
                toast(successMessage)
                loadDashboard()
            } else {
                toast("Request failed (${response.code()})")
            }
        }

        override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
            toast(t.message ?: "Request failed")
        }
    }

    private fun goBackToLogin(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setStatus(message: String) {
        statusText.text = message
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

class PortraitCaptureActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
    }
}
