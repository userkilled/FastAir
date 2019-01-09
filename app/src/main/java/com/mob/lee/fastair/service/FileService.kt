package com.mob.lee.fastair.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import com.mob.lee.fastair.R
import com.mob.lee.fastair.base.AndroidScope
import com.mob.lee.fastair.io.FileReader
import com.mob.lee.fastair.io.FileWriter
import com.mob.lee.fastair.io.ProcessListener
import com.mob.lee.fastair.io.SocketService
import com.mob.lee.fastair.io.state.FaildState
import com.mob.lee.fastair.io.state.ProcessState
import com.mob.lee.fastair.io.state.STATE_DISCONNECTED
import com.mob.lee.fastair.io.state.StartState
import com.mob.lee.fastair.io.state.State
import com.mob.lee.fastair.io.state.SuccessState
import com.mob.lee.fastair.model.ADDRESS
import com.mob.lee.fastair.model.IS_HOST
import com.mob.lee.fastair.model.PORT_FILE
import com.mob.lee.fastair.model.Record
import com.mob.lee.fastair.model.STATE_SUCCESS
import com.mob.lee.fastair.utils.database
import com.mob.lee.fastair.utils.updateStorage
import java.io.File

/**
 * Created by Andy on 2017/12/28.
 */
class FileService : Service() {
    var socket : SocketService? = null
    var writing = false
    var mHandler : Handler? = null
    var mFileChangeListener : ProcessListener? = null

    val mScope : AndroidScope by lazy {
        AndroidScope()
    }

    override fun onCreate() {
        super.onCreate()
        mHandler = Handler {
            if (null == it.obj) {
                return@Handler true
            }
            val state = it.obj as? State
            state?.let {
                mFileChangeListener?.invoke(it)
            }
            val file = state?.obj as? File
            when (state) {
                is StartState -> {
                    notification(0, file?.name ?: "")
                }

                is ProcessState -> {
                    notification((state.process / state.total * 100).toInt(), file?.name ?: "")
                }

                is SuccessState -> {
                    notification(100, file?.name ?: "")
                    writing = false
                    startSendNew()
                }
            }
            true
        }
        mScope.create()
    }

    override fun onDestroy() {
        super.onDestroy()
        mScope.destory()
    }

    override fun onBind(intent : Intent?) : IBinder {
        return BinderImpl(this)
    }

    override fun onStartCommand(intent : Intent?, flags : Int, startId : Int) : Int {
        if (null == intent || null != socket) {
            startSendNew()
            return super.onStartCommand(intent, flags, startId)
        }
        val host = intent?.getStringExtra(ADDRESS)
        val isHost = intent?.getBooleanExtra(IS_HOST, false) ?: false
        socket = SocketService(mScope)
        socket?.addListener{
            state,info->
            when(state){
                STATE_DISCONNECTED->{
                    mFileChangeListener?.invoke(FaildState())
                    stopSelf()
                }
            }
        }
        socket?.open(PORT_FILE, if (isHost) null else host)
        socket?.read(FileReader(this@FileService) {
            it.sendMessage(mHandler)
            updateRecord(it)
        })
        startSendNew()
        return super.onStartCommand(intent, flags, startId)
    }

    fun startSendNew() {
        if (writing) {
            return
        }
        database(mScope) { dao ->
            val record = dao.waitRecord()
            record ?: let {
                writing = false
                return@database
            }
            writing = true
            socket?.write(FileWriter(record.path) {
                it.sendMessage(mHandler)
                updateRecord(it, record)
            })
        }
    }

    fun updateRecord(state : State?, record : Record? = null) {
        if (state !is SuccessState) {
            return
        }
        val file = state.obj as? File
        file ?: return
        val target = if (null == record) {
            updateStorage(file.absolutePath)
            Record(file.lastModified(), file.length(), file.lastModified(), file.path, STATE_SUCCESS, state.duration)
        } else {
            record.state= STATE_SUCCESS
            record
        }
        database(mScope) { dao ->
            if(null==record){
                dao.insert(target)
            }else {
                dao.update(target)
            }
        }
    }

    fun notification(progress : Int, title : String) {
        val builder = if (26 <= Build.VERSION.SDK_INT) {
            Notification.Builder(this, "FastAir")
        } else {
            Notification.Builder(this)
        }
        builder.setContentTitle(title)
        builder.setSmallIcon(R.mipmap.ic_launcher)
        builder.setProgress(100, progress, false)
        startForeground(9727, builder.build())
    }
}