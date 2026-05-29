package com.kursach.mobile

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.widget.doAfterTextChanged
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
import com.kursach.mobile.api.DeleteUserRequest
import com.kursach.mobile.api.CreateUserRequest
import com.kursach.mobile.api.MessageResponse
import com.kursach.mobile.api.UpdateComponentRequest
import com.kursach.mobile.ui.ComponentAdapter
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class TableActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_USERNAME = "extra_username"
    }

    private enum class ScanTarget { SERIAL, SEARCH }

    private lateinit var statusText: androidx.appcompat.widget.AppCompatTextView
    private lateinit var subtitleText: androidx.appcompat.widget.AppCompatTextView
    private lateinit var adapter: ComponentAdapter
    private lateinit var searchInput: TextInputEditText
    private lateinit var adminButtonsRow: android.view.View
    private lateinit var adminDbRow: android.view.View

    private val api by lazy { ApiClient.create(ApiService::class.java) }

    private var dashboard: DashboardResponse? = null
    private var selectedItem: DashboardComponent? = null
    private var pendingSerialInput: TextInputEditText? = null
    private var pendingScanTarget: ScanTarget? = null
    private var currentUserRole: String = "user"
    private var currentUserId: Long = -1
    private var searchQuery: String = ""
    private var visibleItems: List<DashboardComponent> = emptyList()

    private val componentTypes = listOf("контролер", "датчик", "модуль")
    private val componentStatuses = listOf("вільне", "ремонт")

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val serial = result.contents?.trim().orEmpty()
        if (serial.isNotEmpty()) {
            when (pendingScanTarget) {
                ScanTarget.SEARCH -> {
                    searchInput.setText(serial)
                    searchInput.setSelection(serial.length)
                    searchQuery = serial
                    applyFilters()
                    toast("Скановано для пошуку")
                }
                else -> {
                    pendingSerialInput?.setText(serial)
                    toast("Серійний номер скановано")
                }
            }
        }
        pendingSerialInput = null
        pendingScanTarget = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_table)

        statusText = findViewById(R.id.statusText)
        subtitleText = findViewById(R.id.subtitleText)
        searchInput = findViewById(R.id.searchInput)
        adminButtonsRow = findViewById(R.id.adminButtonsRow)
        adminDbRow = findViewById(R.id.adminDbRow)

        adapter = ComponentAdapter(
            onItemClick = { item ->
                selectedItem = item
                adapter.setSelectedId(item.id)
            },
            onReturnClick = { item, broken ->
                if (broken) {
                    returnBrokenComponent(item)
                } else {
                    returnComponent(item)
                }
            }
        )

        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.componentList).apply {
            layoutManager = LinearLayoutManager(this@TableActivity)
            adapter = this@TableActivity.adapter
        }

        findViewById<MaterialButton>(R.id.refreshButton).setOnClickListener { loadDashboard() }
        findViewById<MaterialButton>(R.id.addButton).setOnClickListener { showComponentDialog() }
        findViewById<MaterialButton>(R.id.editButton).setOnClickListener { selectedItem?.let { showComponentDialog(it) } ?: toast("Оберіть компонент") }
        findViewById<MaterialButton>(R.id.removeButton).setOnClickListener { selectedItem?.let { confirmRemove(it) } ?: toast("Оберіть компонент") }
        findViewById<MaterialButton>(R.id.assignButton).setOnClickListener { selectedItem?.let { showAssignDialog(it) } ?: toast("Оберіть компонент") }
        findViewById<MaterialButton>(R.id.addUserButton).setOnClickListener { openAddUserDialog() }
        findViewById<MaterialButton>(R.id.removeUserButton).setOnClickListener { openDeleteUserDialog() }
        findViewById<MaterialButton>(R.id.resetDbButton).setOnClickListener { confirmResetDatabase() }
        findViewById<MaterialButton>(R.id.reportButton).setOnClickListener { showWarehouseReport() }
        findViewById<MaterialButton>(R.id.logoutButton).setOnClickListener { logout() }

        searchInput.doAfterTextChanged {
            searchQuery = it?.toString().orEmpty()
            applyFilters()
        }
        findViewById<MaterialButton>(R.id.searchScanButton).setOnClickListener {
            pendingScanTarget = ScanTarget.SEARCH
            launchScanner()
        }

        subtitleText.text = intent.getStringExtra(EXTRA_USERNAME)?.let { "Увійшли як $it" } ?: "Увійшли в систему"
        loadDashboard()
    }

    private fun loadDashboard() {
        setStatus("Завантаження панелі...")
        api.dashboard().enqueue(object : Callback<DashboardResponse> {
            override fun onResponse(call: Call<DashboardResponse>, response: Response<DashboardResponse>) {
                if (!response.isSuccessful || response.body() == null) {
                    if (response.code() == 401) {
                        goBackToLogin("Сесію завершено. Увійдіть ще раз.")
                        return
                    }
                    setStatus("Помилка панелі (${response.code()})")
                    return
                }

                dashboard = response.body()
                val currentUser = dashboard!!.user
                currentUserRole = currentUser.role
                currentUserId = currentUser.id
                subtitleText.text = "${currentUser.username} · ${if (currentUser.role == "admin") "адмін" else "користувач"}"
                applyFilters()
                adapter.setSelectedId(selectedItem?.id)
                setActionState(currentUser.role == "admin")
                adminButtonsRow.visibility = if (currentUser.role == "admin") android.view.View.VISIBLE else android.view.View.GONE
                adminDbRow.visibility = if (currentUser.role == "admin") android.view.View.VISIBLE else android.view.View.GONE
                setStatus("Завантажено ${dashboard!!.items.size} компонентів")
            }

            override fun onFailure(call: Call<DashboardResponse>, t: Throwable) {
                setStatus(t.message ?: "Не вдалося завантажити панель")
            }
        })
    }

    private fun setActionState(enabled: Boolean) {
        findViewById<MaterialButton>(R.id.addButton).isEnabled = enabled
        findViewById<MaterialButton>(R.id.editButton).isEnabled = enabled
        findViewById<MaterialButton>(R.id.removeButton).isEnabled = enabled
        findViewById<MaterialButton>(R.id.assignButton).isEnabled = enabled
    }

    private fun applyFilters() {
        val dashboardItems = dashboard?.items.orEmpty()
        val query = searchQuery.trim().lowercase()
        visibleItems = if (query.isBlank()) {
            dashboardItems
        } else {
            dashboardItems.filter { item ->
                listOf(item.name, item.type, item.serial, item.status, item.description.orEmpty())
                    .any { value -> value.lowercase().contains(query) }
            }
        }

        adapter.submitList(visibleItems, dashboard?.assignmentByEquipmentId.orEmpty(), currentUserRole != "admin")
        adapter.setSelectedId(selectedItem?.id)
    }

    private fun showComponentDialog(item: DashboardComponent? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_component_form, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.componentNameInput)
        val typeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.componentTypeInput)
        val serialInput = dialogView.findViewById<TextInputEditText>(R.id.componentSerialInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.componentDescriptionInput)
        val statusInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.componentStatusInput)
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
            .setTitle(if (item == null) "Додати компонент" else "Редагувати компонент")
            .setView(dialogView)
            .setNegativeButton("Скасувати", null)
            .setPositiveButton("Зберегти", null)
            .create()

        scanButton.setOnClickListener {
            pendingSerialInput = serialInput
            pendingScanTarget = ScanTarget.SERIAL
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
                    toast("Потрібні назва, тип і серійний номер")
                    return@setOnClickListener
                }

                if (!componentTypes.contains(type)) {
                    toast("Оберіть коректний тип компонента")
                    return@setOnClickListener
                }

                if (status == "призначене") {
                    toast("Для статусу призначення використайте окрему дію")
                    return@setOnClickListener
                }

                if (!componentStatuses.contains(status)) {
                    toast("Оберіть коректний статус")
                    return@setOnClickListener
                }

                if (item == null) {
                    api.addComponent(ComponentRequest(name, type, serial, description, status))
                        .enqueue(componentCallback("Компонент додано"))
                } else {
                    api.updateComponent(UpdateComponentRequest(item.id, name, type, serial, status, description))
                        .enqueue(componentCallback("Компонент оновлено"))
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
            .setTitle("Призначити компонент")
            .setView(dialogView)
            .setNegativeButton("Скасувати", null)
            .setPositiveButton("Призначити", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedUsername = userInput.text?.toString()?.trim().orEmpty()
                val user = userByName[selectedUsername]

                if (user == null) {
                    toast("Оберіть коректного користувача без ролі admin")
                    return@setOnClickListener
                }

                api.assignComponent(AssignComponentRequest(item.id, user.id))
                    .enqueue(componentCallback("Компонент призначено"))
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun confirmRemove(item: DashboardComponent) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Видалити компонент")
            .setMessage("Видалити ${item.name} (${item.serial})?")
            .setNegativeButton("Скасувати", null)
            .setPositiveButton("Видалити") { _, _ ->
                api.removeComponent(ComponentIdRequest(item.id)).enqueue(componentCallback("Компонент видалено"))
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
            setPrompt("Скануйте серійний номер")
            setBeepEnabled(true)
            setOrientationLocked(true)
        }
        scanLauncher.launch(options)
    }

    fun returnComponent(item: DashboardComponent) {
        api.returnComponent(ComponentIdRequest(item.id)).enqueue(componentCallback("Компонент повернено"))
    }

    fun returnBrokenComponent(item: DashboardComponent) {
        api.returnBrokenComponent(ComponentIdRequest(item.id)).enqueue(componentCallback("Компонент повернено як пошкоджений"))
    }

    private fun openAddUserDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_user_form, null)
        val usernameInput = dialogView.findViewById<TextInputEditText>(R.id.newUserUsernameInput)
        val passwordInput = dialogView.findViewById<TextInputEditText>(R.id.newUserPasswordInput)
        val roleInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.newUserRoleInput)
        val roles = listOf("user", "admin", "teacher")
        roleInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, roles))
        roleInput.setText("user", false)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Створити користувача")
            .setView(dialogView)
            .setNegativeButton("Скасувати", null)
            .setPositiveButton("Створити", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val username = usernameInput.text?.toString()?.trim().orEmpty()
                val password = passwordInput.text?.toString().orEmpty()
                val role = roleInput.text?.toString()?.trim().orEmpty()

                if (username.isBlank() || password.isBlank() || role.isBlank()) {
                    toast("Заповніть логін, пароль і роль")
                    return@setOnClickListener
                }

                if (!roles.contains(role)) {
                    toast("Оберіть коректну роль")
                    return@setOnClickListener
                }

                api.addUser(CreateUserRequest(username, password, role)).enqueue(componentCallback("Користувача створено"))
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun openDeleteUserDialog() {
        val users = dashboard?.users?.filter { it.role != "admin" }.orEmpty()
        val usernames = users.map { it.username }
        val userByName = users.associateBy { it.username }

        val dialogView = layoutInflater.inflate(R.layout.dialog_assign_component, null)
        val userInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.userInput)
        userInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, usernames))

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Видалити користувача")
            .setView(dialogView)
            .setNegativeButton("Скасувати", null)
            .setPositiveButton("Видалити", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedUsername = userInput.text?.toString()?.trim().orEmpty()
                val user = userByName[selectedUsername]

                if (user == null) {
                    toast("Оберіть коректного користувача")
                    return@setOnClickListener
                }

                api.deleteUser(DeleteUserRequest(user.id)).enqueue(componentCallback("Користувача видалено"))
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun confirmResetDatabase() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Скинути базу даних")
            .setMessage("Усі користувачі, компоненти та призначення будуть видалені. Продовжити?")
            .setNegativeButton("Скасувати", null)
            .setPositiveButton("Скинути") { _, _ ->
                api.resetDatabase().enqueue(componentCallback("Базу даних скинуто"))
            }
            .show()
    }

    private fun showWarehouseReport() {
        val report = dashboard?.warehouseReport
        if (report == null) {
            toast("Звіт недоступний")
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Звіт складу")
            .setMessage(
                "Усього: ${report.totalEquipment}\n" +
                    "Вільне: ${report.freeEquipment}\n" +
                    "Призначене: ${report.assignedEquipment}\n" +
                    "Пошкоджене: ${report.damagedEquipment}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun logout() {
        api.logout().enqueue(object : Callback<MessageResponse> {
            override fun onResponse(call: Call<MessageResponse>, response: Response<MessageResponse>) {
                ApiClient.clearSession()
                goBackToLogin("Вихід виконано")
            }

            override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
                ApiClient.clearSession()
                goBackToLogin(t.message ?: "Вихід виконано")
            }
        })
    }

    private fun componentCallback(successMessage: String) = object : Callback<MessageResponse> {
        override fun onResponse(call: Call<MessageResponse>, response: Response<MessageResponse>) {
            if (response.isSuccessful) {
                toast(successMessage)
                loadDashboard()
            } else {
                toast("Запит не виконано (${response.code()})")
            }
        }

        override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
            toast(t.message ?: "Запит не виконано")
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
