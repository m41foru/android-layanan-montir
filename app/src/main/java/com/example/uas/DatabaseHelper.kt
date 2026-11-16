package com.example.uas

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.Serializable

data class UserAccount(
    val id: Int,
    val fullName: String,
    val email: String,
    val whatsapp: String,
    val password: String,
    val photoUri: String
) : Serializable

data class EmergencyOrder(
    val id: Int = 0,
    val userId: Int,
    val namaPemilik: String,
    val tipeMobil: String,
    val platNomor: String,
    val kategoriMasalah: String,
    val jenisPenanganan: String, // "Servis di Lokasi" atau "Derek ke Bengkel"
    val deskripsiKeluhan: String,
    val lokasiMogok: String,
    val fotoKondisiUri: String,
    val estimasiBiaya: Int,
    val statusPenanganan: String,
    val tanggalWaktu: String
) : Serializable

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val DATABASE_NAME = "autofix_center.db"
        private const val DATABASE_VERSION = 1

        private const val PREFS_NAME = "autofix_session_prefs"
        private const val KEY_LOGGED_IN_USER_ID = "logged_in_user_id"

        // Tabel Users
        private const val TABLE_USERS = "users"
        private const val COL_USER_ID = "id"
        private const val COL_USER_NAME = "full_name"
        private const val COL_USER_EMAIL = "email"
        private const val COL_USER_WA = "whatsapp"
        private const val COL_USER_PASS = "password"
        private const val COL_USER_PHOTO = "photo_uri"

        // Tabel Emergency Orders (Entitas Utama CRUD - 12 Atribut)
        private const val TABLE_ORDERS = "emergency_orders"
        private const val COL_ORDER_ID = "id"
        private const val COL_ORDER_USER_ID = "user_id"
        private const val COL_ORDER_OWNER = "nama_pemilik"
        private const val COL_ORDER_CAR = "tipe_mobil"
        private const val COL_ORDER_PLATE = "plat_nomor"
        private const val COL_ORDER_CATEGORY = "kategori_masalah"
        private const val COL_ORDER_SERVICE_TYPE = "jenis_penanganan"
        private const val COL_ORDER_DESC = "deskripsi_keluhan"
        private const val COL_ORDER_LOCATION = "lokasi_mogok"
        private const val COL_ORDER_PHOTO = "foto_kondisi_uri"
        private const val COL_ORDER_COST = "estimasi_biaya"
        private const val COL_ORDER_STATUS = "status_penanganan"
        private const val COL_ORDER_DATE = "tanggal_waktu"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                $COL_USER_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_USER_NAME TEXT NOT NULL,
                $COL_USER_EMAIL TEXT NOT NULL UNIQUE,
                $COL_USER_WA TEXT NOT NULL,
                $COL_USER_PASS TEXT NOT NULL,
                $COL_USER_PHOTO TEXT DEFAULT ''
            )
        """.trimIndent()

        val createOrdersTable = """
            CREATE TABLE $TABLE_ORDERS (
                $COL_ORDER_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ORDER_USER_ID INTEGER NOT NULL,
                $COL_ORDER_OWNER TEXT NOT NULL,
                $COL_ORDER_CAR TEXT NOT NULL,
                $COL_ORDER_PLATE TEXT NOT NULL,
                $COL_ORDER_CATEGORY TEXT NOT NULL,
                $COL_ORDER_SERVICE_TYPE TEXT NOT NULL,
                $COL_ORDER_DESC TEXT NOT NULL,
                $COL_ORDER_LOCATION TEXT NOT NULL,
                $COL_ORDER_PHOTO TEXT NOT NULL,
                $COL_ORDER_COST INTEGER NOT NULL,
                $COL_ORDER_STATUS TEXT NOT NULL,
                $COL_ORDER_DATE TEXT NOT NULL
            )
        """.trimIndent()

        db.execSQL(createUsersTable)
        db.execSQL(createOrdersTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ORDERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    // ==================== MANAJEMEN SESI & AUTENTIKASI ====================

    fun saveSession(userId: Int) {
        prefs.edit().putInt(KEY_LOGGED_IN_USER_ID, userId).apply()
    }

    fun getLoggedInUserId(): Int = prefs.getInt(KEY_LOGGED_IN_USER_ID, -1)

    fun clearSession() {
        prefs.edit().remove(KEY_LOGGED_IN_USER_ID).apply()
    }

    fun isEmailExists(email: String, excludeUserId: Int = -1): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_USER_ID FROM $TABLE_USERS WHERE LOWER($COL_USER_EMAIL) = LOWER(?) AND $COL_USER_ID != ?",
            arrayOf(email.trim(), excludeUserId.toString())
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun registerUser(fullName: String, email: String, whatsapp: String, password: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_USER_NAME, fullName.trim())
            put(COL_USER_EMAIL, email.trim().lowercase())
            put(COL_USER_WA, whatsapp.trim())
            put(COL_USER_PASS, password)
            put(COL_USER_PHOTO, "")
        }
        val newUserId = db.insert(TABLE_USERS, null, values)
        if (newUserId != -1L) {
            saveSession(newUserId.toInt())
        }
        return newUserId
    }

    fun loginUser(email: String, password: String): UserAccount? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE LOWER($COL_USER_EMAIL) = LOWER(?) AND $COL_USER_PASS = ?",
            arrayOf(email.trim(), password)
        )
        var user: UserAccount? = null
        if (cursor.moveToFirst()) {
            user = UserAccount(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_ID)),
                fullName = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_NAME)),
                email = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_EMAIL)),
                whatsapp = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_WA)),
                password = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_PASS)),
                photoUri = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_PHOTO)) ?: ""
            )
            saveSession(user.id)
        }
        cursor.close()
        return user
    }

    fun getCurrentUser(): UserAccount? {
        val userId = getLoggedInUserId()
        if (userId == -1) return null
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE $COL_USER_ID = ?",
            arrayOf(userId.toString())
        )
        var user: UserAccount? = null
        if (cursor.moveToFirst()) {
            user = UserAccount(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_ID)),
                fullName = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_NAME)),
                email = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_EMAIL)),
                whatsapp = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_WA)),
                password = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_PASS)),
                photoUri = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_PHOTO)) ?: ""
            )
        }
        cursor.close()
        return user
    }

    fun updateUserProfile(
        userId: Int,
        fullName: String,
        email: String,
        whatsapp: String,
        newPassword: String?,
        photoUri: String
    ): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_USER_NAME, fullName.trim())
            put(COL_USER_EMAIL, email.trim().lowercase())
            put(COL_USER_WA, whatsapp.trim())
            if (!newPassword.isNullOrBlank()) {
                put(COL_USER_PASS, newPassword)
            }
            put(COL_USER_PHOTO, photoUri)
        }
        val rows = db.update(TABLE_USERS, values, "$COL_USER_ID = ?", arrayOf(userId.toString()))
        return rows > 0
    }

    fun deleteUserAccount(userId: Int): Boolean {
        val db = writableDatabase
        db.delete(TABLE_ORDERS, "$COL_ORDER_USER_ID = ?", arrayOf(userId.toString()))
        val rows = db.delete(TABLE_USERS, "$COL_USER_ID = ?", arrayOf(userId.toString()))
        clearSession()
        return rows > 0
    }

    // ==================== CRUD DATA UTAMA (EMERGENCY ORDERS) ====================

    fun insertOrder(order: EmergencyOrder): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ORDER_USER_ID, order.userId)
            put(COL_ORDER_OWNER, order.namaPemilik)
            put(COL_ORDER_CAR, order.tipeMobil)
            put(COL_ORDER_PLATE, order.platNomor.uppercase())
            put(COL_ORDER_CATEGORY, order.kategoriMasalah)
            put(COL_ORDER_SERVICE_TYPE, order.jenisPenanganan)
            put(COL_ORDER_DESC, order.deskripsiKeluhan)
            put(COL_ORDER_LOCATION, order.lokasiMogok)
            put(COL_ORDER_PHOTO, order.fotoKondisiUri)
            put(COL_ORDER_COST, order.estimasiBiaya)
            put(COL_ORDER_STATUS, order.statusPenanganan)
            put(COL_ORDER_DATE, order.tanggalWaktu)
        }
        return db.insert(TABLE_ORDERS, null, values)
    }

    fun updateOrder(order: EmergencyOrder): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ORDER_CAR, order.tipeMobil)
            put(COL_ORDER_PLATE, order.platNomor.uppercase())
            put(COL_ORDER_CATEGORY, order.kategoriMasalah)
            put(COL_ORDER_SERVICE_TYPE, order.jenisPenanganan)
            put(COL_ORDER_DESC, order.deskripsiKeluhan)
            put(COL_ORDER_LOCATION, order.lokasiMogok)
            put(COL_ORDER_PHOTO, order.fotoKondisiUri)
            put(COL_ORDER_COST, order.estimasiBiaya)
            put(COL_ORDER_STATUS, order.statusPenanganan)
        }
        return db.update(
            TABLE_ORDERS,
            values,
            "$COL_ORDER_ID = ?",
            arrayOf(order.id.toString())
        )
    }

    fun deleteOrder(orderId: Int): Int {
        val db = writableDatabase
        return db.delete(TABLE_ORDERS, "$COL_ORDER_ID = ?", arrayOf(orderId.toString()))
    }

    fun getOrdersByUser(userId: Int, searchQuery: String = "", filterType: String = "Semua"): List<EmergencyOrder> {
        val list = mutableListOf<EmergencyOrder>()
        val db = readableDatabase

        val selectionParts = mutableListOf("$COL_ORDER_USER_ID = ?")
        val argsList = mutableListOf(userId.toString())

        if (filterType != "Semua") {
            selectionParts.add("$COL_ORDER_SERVICE_TYPE = ?")
            argsList.add(filterType)
        }

        if (searchQuery.isNotBlank()) {
            selectionParts.add("($COL_ORDER_CAR LIKE ? OR $COL_ORDER_PLATE LIKE ? OR $COL_ORDER_CATEGORY LIKE ? OR $COL_ORDER_DESC LIKE ?)")
            val q = "%${searchQuery.trim()}%"
            argsList.addAll(listOf(q, q, q, q))
        }

        val whereClause = selectionParts.joinToString(" AND ")
        val cursor = db.query(
            TABLE_ORDERS,
            null,
            whereClause,
            argsList.toTypedArray(),
            null,
            null,
            "$COL_ORDER_ID DESC"
        )

        if (cursor.moveToFirst()) {
            do {
                list.add(
                    EmergencyOrder(
                        id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ORDER_ID)),
                        userId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ORDER_USER_ID)),
                        namaPemilik = cursor.getString(cursor.getColumnIndexOrThrow(COL_ORDER_OWNER)),
                        tipeMobil = cursor.getString(cursor.getColumnIndexOrThrow(COL_ORDER_CAR)),
                        platNomor = cursor.getString(cursor.getColumnIndexOrThrow(COL_ORDER_PLATE)),
                        kategoriMasalah = cursor.getString(cursor.getColumnIndexOrThrow(COL_ORDER_CATEGORY)),
                        jenisPenanganan = cursor.getString(cursor.getColumnIndexOrThrow(COL_ORDER_SERVICE_TYPE)),
                        deskripsiKeluhan = cursor.getString(cursor.getColumnIndexOrThrow(COL_ORDER_DESC)),
                        lokasiMogok = cursor.getString(cursor.getColumnIndexOrThrow(COL_ORDER_LOCATION)),
                        fotoKondisiUri = cursor.getString(cursor.getColumnIndexOrThrow(COL_ORDER_PHOTO)) ?: "",
                        estimasiBiaya = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ORDER_COST)),
                        statusPenanganan = cursor.getString(cursor.getColumnIndexOrThrow(COL_ORDER_STATUS)),
                        tanggalWaktu = cursor.getString(cursor.getColumnIndexOrThrow(COL_ORDER_DATE))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }
}