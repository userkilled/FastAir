package com.mob.lee.fastair.utils

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.room.Room
import com.mob.lee.fastair.R
import com.mob.lee.fastair.db.AppDatabase
import com.mob.lee.fastair.db.RecordDao
import com.mob.lee.fastair.model.Record
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.io.File
import java.util.*

/**
 * Created by Andy on 2017/7/7.
 */

fun Context.getPaths(file : File?) : List<File> {
    var path = file
    val list = ArrayList<File>()
    if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
        if (null == path) {
            path = Environment.getExternalStorageDirectory()
        }
        val files = path?.listFiles { dir, name ->
            dir.isDirectory && ! name.startsWith(".")
        }
        files?.sortBy { it.name.toLowerCase() }
        files?.let {
            for (file in files) {
                list.add(file)
            }
        }
    }
    return list
}

fun updateStorage(context : Context, record : Record) {
    val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
    intent.data = Uri.fromFile(File(record.path))
    context.sendBroadcast(intent)
}

fun openFile(context : Context, record : Record) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.fromFile(File(record.path))
    intent.resolveActivity(context.packageManager)?.let {
        context.startActivity(intent)
    }
}

fun Context.createFile(name : String) : File {
    return File(readDownloadPath() + File.separator + name)
}

fun Context.readDownloadPath() : String {
    val infos = getSharedPreferences("infos", Context.MODE_PRIVATE)
    return infos.getString("downloadPath", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
}

fun Context.writeDownloadPath(path : String) {
    val infos = getSharedPreferences("infos", Context.MODE_PRIVATE)
    infos.edit().putString("downloadPath", path).apply()
}

fun Context.toast(message : String?, bg : Int) {
    if (null == message) {
        return
    }
    val real = SpannableString(message)
    real.setSpan(ForegroundColorSpan(Color.WHITE), 0, real.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    val toast = Toast.makeText(this, real, Toast.LENGTH_SHORT)
    toast.view.setBackgroundResource(bg)
    toast.show()
}

fun Context.errorToast(message : Int) {
    val msg = getString(message)
    msg ?: return
    errorToast(msg)
}

fun Context.errorToast(message : String) {
    toast(message, R.drawable.bg_toast_failed)
}

fun Context.successToast(message : Int) {
    val msg = getString(message)
    msg ?: return
    successToast(msg)
}

fun Context.successToast(message : String) {
    toast(message, R.drawable.bg_toast_success)
}

fun Context.dialog(wrap : (AlertDialog.Builder) -> Unit) {
    val builder = AlertDialog.Builder(this)
            .setTitle(R.string.wramTips)
            .setPositiveButton(R.string.knowIt,null)
    wrap(builder)
    builder.show()
}

fun Context.database(scope : CoroutineScope, action : suspend (RecordDao) -> Unit) {
    val database = "fastair"
    var db : AppDatabase? = null
    try {
        db = Room.databaseBuilder(this, AppDatabase::class.java, database).build()
        val dao = db.recordDao()
        scope.async(Dispatchers.IO) {
            action(dao)
            db.close()
        }
    } catch (e : Exception) {
        Log.d(database, e.toString())
        db?.close()
    }
}